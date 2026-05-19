package nl.detoxathome.remotecaremanager.service.scheduled;

import nl.detoxathome.remotecaremanager.service.Configuration;
import nl.rrd.utils.AppComponents;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component("healthchecksPingIntervalConfig")
public class HealthchecksPingIntervalConfig {
	private static final String LOGTAG =
			HealthchecksPingIntervalConfig.class.getSimpleName();

	private final long intervalMs;

	public HealthchecksPingIntervalConfig() {
		this(AppComponents.get(Configuration.class),
				AppComponents.getLogger(LOGTAG));
	}

	HealthchecksPingIntervalConfig(Configuration config, Logger logger) {
		this.intervalMs = HealthchecksPingService.resolveScheduleDelayMs(
				config, logger);
	}

	public long getIntervalMs() {
		return intervalMs;
	}
}
