package nl.detoxathome.remotecaremanager.clientextensions.project;

import nl.detoxathome.remotecaremanager.client.project.BaseProject;
import nl.detoxathome.remotecaremanager.client.project.ProjectRepository;
import nl.detoxathome.remotecaremanager.clientextensions.project.detox.DetoxProject;

import java.util.List;

public class DetoxProjectRepository extends ProjectRepository {
	@Override
	protected List<BaseProject> createProjects() {
		return List.of(
				new DetoxProject()
		);
	}
}
