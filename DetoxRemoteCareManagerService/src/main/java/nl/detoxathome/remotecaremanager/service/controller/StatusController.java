package nl.detoxathome.remotecaremanager.service.controller;

import io.swagger.v3.oas.annotations.Parameter;
import nl.detoxathome.remotecaremanager.service.scheduled.HealthchecksPingService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v{version}/status")
public class StatusController {
	private final HealthchecksPingService healthchecksPingService;

	public StatusController(HealthchecksPingService healthchecksPingService) {
		this.healthchecksPingService = healthchecksPingService;
	}

	@RequestMapping(value="/healthchecks-general", method=RequestMethod.GET)
	public HealthchecksPingService.Status getHealthchecksGeneralStatus(
			@PathVariable("version")
			@Parameter(hidden=true)
			String versionName) {
		return healthchecksPingService.getStatus();
	}
}
