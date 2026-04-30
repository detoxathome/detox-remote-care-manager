package nl.detoxathome.remotecaremanager.app;

import nl.detoxathome.remotecaremanager.client.DetoxClient;
import nl.detoxathome.remotecaremanager.client.model.User;
import nl.detoxathome.remotecaremanager.service.export.DataExportListener;
import nl.detoxathome.remotecaremanager.service.export.DataExporter;
import nl.detoxathome.remotecaremanager.service.export.DataExporterFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DetoxRemoteCareManagerDataExporterFactory implements DataExporterFactory {
	@Override
	public List<String> getProjectCodes() {
		return new ArrayList<>();
	}

	@Override
	public DataExporter create(String project, String id, User user,
			DetoxClient client, File dir, DataExportListener listener) {
		return null;
	}
}
