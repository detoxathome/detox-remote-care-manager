package nl.detoxathome.remotecaremanager.app;

import nl.detoxathome.remotecaremanager.service.access.ProjectUserAccessControl;
import nl.detoxathome.remotecaremanager.service.access.ProjectUserAccessControlRepository;
import nl.rrd.utils.AppComponent;

import java.util.HashMap;
import java.util.Map;

@AppComponent
public class DetoxProjectUserAccessControlRepository
		extends ProjectUserAccessControlRepository {
	@Override
	public Map<String,ProjectUserAccessControl> getProjectMap() {
		return new HashMap<>();
	}
}
