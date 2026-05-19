package nl.detoxathome.remotecaremanager.service.scheduled;

import nl.detoxathome.remotecaremanager.service.Configuration;
import nl.rrd.utils.AppComponents;
import org.slf4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class HealthchecksPingService {
	public static final String DEFAULT_GENERAL_PING_URL =
			"https://hc-ping.com/z8zqs5tbzgpoko4eyto5eg/" +
					"detoxhome-remotecaremanager-general";

	private static final String LOGTAG =
			HealthchecksPingService.class.getSimpleName();
	private static final long SCHEDULE_DELAY_MS = 30 * 60 * 1000;
	private static final int MAX_RETRIES = 5;
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
	private static final long[] RETRY_DELAYS_MS = {
			1000, 2000, 4000, 8000, 16000
	};

	private final Configuration config;
	private final HttpClient httpClient;
	private final Sleeper sleeper;
	private final Logger logger;
	private final Object statusLock = new Object();

	private boolean lastEnabled = true;
	private boolean lastConfigured = true;
	private boolean inProgress = false;
	private String lastOutcome = "never_run";
	private ZonedDateTime lastAttemptTime = null;
	private ZonedDateTime lastSuccessTime = null;
	private ZonedDateTime lastFailureTime = null;
	private Integer lastStatusCode = null;

	public HealthchecksPingService() {
		this(AppComponents.get(Configuration.class),
				HttpClient.newBuilder()
						.connectTimeout(REQUEST_TIMEOUT)
						.build(),
				Thread::sleep);
	}

	HealthchecksPingService(Configuration config, HttpClient httpClient,
			Sleeper sleeper) {
		this.config = config;
		this.httpClient = httpClient;
		this.sleeper = sleeper;
		this.logger = AppComponents.getLogger(LOGTAG);
	}

	@Scheduled(fixedDelay=SCHEDULE_DELAY_MS)
	public void runTask() {
		try {
			runPing();
		} catch (RuntimeException ex) {
			logger.error("Unexpected error while sending Healthchecks ping: " +
					ex.getMessage(), ex);
		}
	}

	public Status getStatus() {
		synchronized (statusLock) {
			return new Status(
					lastEnabled,
					lastConfigured,
					inProgress,
					lastOutcome,
					lastAttemptTime,
					lastSuccessTime,
					lastFailureTime,
					lastStatusCode,
					SCHEDULE_DELAY_MS,
					REQUEST_TIMEOUT.toMillis(),
					MAX_RETRIES,
					Arrays.stream(RETRY_DELAYS_MS).boxed().toList()
			);
		}
	}

	private void runPing() {
		if (!isEnabled()) {
			updateDisabledStatus();
			return;
		}
		String url = config.get(Configuration.HEALTHCHECKS_GENERAL_PING_URL,
				DEFAULT_GENERAL_PING_URL);
		if (url == null || url.isBlank()) {
			logger.error("Healthchecks ping URL is empty");
			updateInvalidConfigStatus();
			return;
		}
		URI uri;
		try {
			uri = URI.create(url.trim());
		} catch (IllegalArgumentException ex) {
			logger.error("Invalid Healthchecks ping URL: " + url, ex);
			updateInvalidConfigStatus();
			return;
		}
		HttpRequest request = HttpRequest.newBuilder()
				.uri(uri)
				.timeout(REQUEST_TIMEOUT)
				.GET()
				.build();
		updateStartedStatus();
		for (int attempt = 1; attempt <= MAX_RETRIES + 1; attempt++) {
			try {
				if (sendPing(request, attempt)) {
					updateSuccessStatus();
					return;
				}
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				logger.warn("Healthchecks ping interrupted");
				updateFailureStatus(null);
				return;
			} catch (IOException ex) {
				logger.warn("Healthchecks ping attempt " + attempt +
						" failed: " + ex.getMessage(), ex);
			}
			if (attempt <= MAX_RETRIES && !sleepBeforeRetry(attempt))
				return;
		}
		updateFailureStatus(lastStatusCode);
	}

	private boolean isEnabled() {
		String enabled = config.get(
				Configuration.HEALTHCHECKS_GENERAL_PING_ENABLED);
		if (enabled != null && !enabled.isBlank())
			return "true".equalsIgnoreCase(enabled.trim());
		String environment = config.get(Configuration.MOBILE_ENVIRONMENT);
		if (environment == null)
			return false;
		environment = environment.trim().toLowerCase(Locale.ROOT);
		return environment.equals("production") ||
				environment.equals("prod") ||
				environment.equals("utwente");
	}

	private boolean sendPing(HttpRequest request, int attempt)
			throws IOException, InterruptedException {
		HttpResponse<String> response = httpClient.send(request,
				HttpResponse.BodyHandlers.ofString());
		int status = response.statusCode();
		updateStatusCode(status);
		if (status >= 200 && status < 300) {
			logger.info("Sent Healthchecks ping");
			return true;
		}
		logger.warn("Healthchecks ping attempt " + attempt +
				" returned status " + status + ": " +
				truncateBody(response.body()));
		return false;
	}

	private boolean sleepBeforeRetry(int attempt) {
		try {
			sleeper.sleep(RETRY_DELAYS_MS[attempt - 1]);
			return true;
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			logger.warn("Healthchecks ping retry sleep interrupted");
			updateFailureStatus(lastStatusCode);
			return false;
		}
	}

	private void updateDisabledStatus() {
		synchronized (statusLock) {
			lastEnabled = false;
			lastConfigured = true;
			inProgress = false;
			lastOutcome = "disabled";
		}
	}

	private void updateInvalidConfigStatus() {
		synchronized (statusLock) {
			lastEnabled = true;
			lastConfigured = false;
			inProgress = false;
			lastOutcome = "invalid_config";
		}
	}

	private void updateStartedStatus() {
		synchronized (statusLock) {
			lastEnabled = true;
			lastConfigured = true;
			inProgress = true;
			lastOutcome = "running";
			lastAttemptTime = ZonedDateTime.now();
			lastStatusCode = null;
		}
	}

	private void updateSuccessStatus() {
		synchronized (statusLock) {
			inProgress = false;
			lastOutcome = "success";
			lastSuccessTime = ZonedDateTime.now();
		}
	}

	private void updateFailureStatus(Integer statusCode) {
		synchronized (statusLock) {
			inProgress = false;
			lastOutcome = "failure";
			lastFailureTime = ZonedDateTime.now();
			lastStatusCode = statusCode;
		}
	}

	private void updateStatusCode(Integer statusCode) {
		synchronized (statusLock) {
			lastStatusCode = statusCode;
		}
	}

	private String truncateBody(String body) {
		if (body == null || body.isBlank())
			return "";
		if (body.length() <= 500)
			return body;
		return body.substring(0, 500) + "...";
	}

	interface Sleeper {
		void sleep(long millis) throws InterruptedException;
	}

	public record Status(
			boolean enabled,
			boolean configured,
			boolean inProgress,
			String outcome,
			ZonedDateTime lastAttemptTime,
			ZonedDateTime lastSuccessTime,
			ZonedDateTime lastFailureTime,
			Integer lastStatusCode,
			long scheduleDelayMs,
			long requestTimeoutMs,
			int maxRetries,
			List<Long> retryDelaysMs) {
	}
}
