package nl.detoxathome.remotecaremanager.app;

import nl.rrd.utils.AppComponents;
import nl.detoxathome.remotecaremanager.dao.DatabaseFactory;
import nl.detoxathome.remotecaremanager.service.ApplicationInit;
import nl.detoxathome.remotecaremanager.service.Configuration;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The main entry point for the Detox Remote Care Manager service as a Spring
 * Boot application.
 * 
 * @author Dennis Hofs (RRD)
 */
@SpringBootApplication(
		scanBasePackages = {
				"nl.detoxathome.remotecaremanager.service",
				"nl.detoxathome.remotecaremanager.app"
		},
		exclude={ MongoAutoConfiguration.class }
)
@EnableScheduling
public class Application extends SpringBootServletInitializer implements
ApplicationListener<ContextClosedEvent> {
	private ApplicationInit appInit;

	/**
	 * Constructs a new application. It reads service.properties and
	 * initialises the {@link Configuration Configuration} and the {@link
	 * AppComponents AppComponents} with the {@link DatabaseFactory
	 * DatabaseFactory}.
	 * 
	 * @throws Exception if the application can't be initialised
	 */
	public Application() throws Exception {
		appInit = new DetoxRemoteCareManagerApplicationInit();
	}

	@Override
	public void onApplicationEvent(ContextClosedEvent event) {
		appInit.onApplicationEvent(event);
	}
	
	@Override
	protected SpringApplicationBuilder configure(
			SpringApplicationBuilder builder) {
		return builder.sources(Application.class);
	}

	@Bean
	public OperationCustomizer operationCustomizer() {
		return appInit.getOperationCustomizer();
	}

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
}
