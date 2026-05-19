package nl.detoxathome.remotecaremanager.service.scheduled;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.detoxathome.remotecaremanager.service.Configuration;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class HealthchecksPingServiceIT {
	@Test
	void sendsGetPingToConfiguredUrl() throws Exception {
		MockWebServer server = new MockWebServer();
		server.enqueue(new MockResponse().setResponseCode(200));
		server.start();
		try {
			TestConfiguration config = new TestConfiguration();
			config.set(Configuration.HEALTHCHECKS_GENERAL_PING_URL,
					server.url("/general").toString());
			List<Long> sleepDelays = new ArrayList<>();
			HealthchecksPingService service = newService(config, sleepDelays);

			service.runTask();

			RecordedRequest request = server.takeRequest(2, TimeUnit.SECONDS);
			assertNotNull(request);
			assertEquals("GET", request.getMethod());
			assertEquals("/general", request.getPath());
			assertEquals(List.of(), sleepDelays);
			HealthchecksPingService.Status status = service.getStatus();
			assertEquals("success", status.outcome());
			assertEquals(200, status.lastStatusCode());
			assertNotNull(status.lastAttemptTime());
			assertNotNull(status.lastSuccessTime());
		} finally {
			server.shutdown();
		}
	}

	@Test
	void statusDoesNotExposePingUrl() throws Exception {
		TestConfiguration config = new TestConfiguration();
		config.set(Configuration.HEALTHCHECKS_GENERAL_PING_URL,
				HealthchecksPingService.DEFAULT_GENERAL_PING_URL);
		HealthchecksPingService service = newService(config, new ArrayList<>());

		String json = new ObjectMapper().writeValueAsString(
				service.getStatus());

		assertFalse(json.contains("hc-ping.com"));
		assertFalse(json.contains("z8zqs5tbzgpoko4eyto5eg"));
	}

	@Test
	void retriesAfterRequestFailure() throws Exception {
		MockWebServer server = new MockWebServer();
		server.enqueue(new MockResponse()
				.setResponseCode(503)
				.setBody("temporarily unavailable"));
		server.enqueue(new MockResponse().setResponseCode(200));
		server.start();
		try {
			TestConfiguration config = new TestConfiguration();
			config.set(Configuration.HEALTHCHECKS_GENERAL_PING_URL,
					server.url("/retry").toString());
			List<Long> sleepDelays = new ArrayList<>();
			HealthchecksPingService service = newService(config, sleepDelays);

			service.runTask();

			assertNotNull(server.takeRequest(2, TimeUnit.SECONDS));
			RecordedRequest retriedRequest =
					server.takeRequest(2, TimeUnit.SECONDS);
			assertNotNull(retriedRequest);
			assertEquals("/retry", retriedRequest.getPath());
			assertEquals(List.of(1000L), sleepDelays);
		} finally {
			server.shutdown();
		}
	}

	@Test
	void retriesNonSuccessResponses() throws Exception {
		MockWebServer server = new MockWebServer();
		for (int i = 0; i < 6; i++) {
			server.enqueue(new MockResponse()
					.setResponseCode(500)
					.setBody("temporary failure"));
		}
		server.start();
		try {
			TestConfiguration config = new TestConfiguration();
			config.set(Configuration.HEALTHCHECKS_GENERAL_PING_URL,
					server.url("/server-error").toString());
			List<Long> sleepDelays = new ArrayList<>();
			HealthchecksPingService service = newService(config, sleepDelays);

			service.runTask();

			assertEquals(6, server.getRequestCount());
			assertEquals(List.of(1000L, 2000L, 4000L, 8000L, 16000L),
					sleepDelays);
		} finally {
			server.shutdown();
		}
	}

	@Test
	void disabledConfigSkipsPing() throws Exception {
		MockWebServer server = new MockWebServer();
		server.start();
		try {
			TestConfiguration config = new TestConfiguration();
			config.set(Configuration.HEALTHCHECKS_GENERAL_PING_URL,
					server.url("/disabled").toString());
			config.set(Configuration.HEALTHCHECKS_GENERAL_PING_ENABLED,
					"false");
			List<Long> sleepDelays = new ArrayList<>();
			HealthchecksPingService service = newService(config, sleepDelays);

			service.runTask();

			assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS));
			assertEquals(List.of(), sleepDelays);
		} finally {
			server.shutdown();
		}
	}

	@Test
	void invalidAndEmptyUrlsDoNotThrow() {
		TestConfiguration invalidConfig = new TestConfiguration();
		invalidConfig.set(Configuration.HEALTHCHECKS_GENERAL_PING_URL,
				"://invalid");
		assertDoesNotThrow(() ->
				newService(invalidConfig, new ArrayList<>()).runTask());

		TestConfiguration emptyConfig = new TestConfiguration();
		emptyConfig.set(Configuration.HEALTHCHECKS_GENERAL_PING_URL, " ");
		assertDoesNotThrow(() ->
				newService(emptyConfig, new ArrayList<>()).runTask());
	}

	private HealthchecksPingService newService(TestConfiguration config,
			List<Long> sleepDelays) {
		HttpClient client = HttpClient.newBuilder()
				.connectTimeout(Duration.ofMillis(500))
				.build();
		return new HealthchecksPingService(config, client, sleepDelays::add);
	}

	private static class TestConfiguration extends Configuration {
		private final Map<String,String> values = new HashMap<>();

		public void set(String key, String value) {
			values.put(key, value);
		}

		@Override
		public String get(String key, String defaultValue) {
			return values.getOrDefault(key, defaultValue);
		}
	}
}
