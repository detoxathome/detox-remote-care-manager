package nl.detoxathome.remotecaremanager.service.export;

import nl.detoxathome.remotecaremanager.client.DetoxClient;
import nl.detoxathome.remotecaremanager.client.model.User;
import nl.rrd.utils.AppComponent;

import java.io.File;
import java.util.List;

@AppComponent
public interface DataExporterFactory {
	List<String> getProjectCodes();
	DataExporter create(String project, String id, User user,
			DetoxClient client, File dir, DataExportListener listener);
}
