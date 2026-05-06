package nl.detoxathome.remotecaremanager.service.access;

import nl.rrd.utils.AppComponent;

import java.util.Map;

@AppComponent
public abstract class ProjectUserAccessControlRepository {
	public ProjectUserAccessControl get(String project) {
		return getProjectMap().get(project);
	}

	public abstract Map<String, ProjectUserAccessControl> getProjectMap();
}
