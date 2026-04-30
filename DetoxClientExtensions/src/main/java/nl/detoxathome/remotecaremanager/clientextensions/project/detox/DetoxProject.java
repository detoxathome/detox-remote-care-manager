package nl.detoxathome.remotecaremanager.clientextensions.project.detox;

import nl.detoxathome.remotecaremanager.client.model.detox.DetoxMessageQueueTable;
import nl.detoxathome.remotecaremanager.client.model.detox.DetoxProcessedMessageQueueTable;
import nl.detoxathome.remotecaremanager.client.model.detox.DetoxTaskConfigurationTable;
import nl.detoxathome.remotecaremanager.client.model.detox.DetoxTaskRefreshRequestTable;
import nl.detoxathome.remotecaremanager.client.project.BaseProject;
import nl.detoxathome.remotecaremanager.client.sensor.BaseSensor;
import nl.detoxathome.remotecaremanager.dao.DatabaseTableDef;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DetoxProject extends BaseProject {
	public DetoxProject() {
		super("detox", "Detox");
	}

	@Override
	public List<BaseSensor> getSensors() {
		return null;
	}

	@Override
	public List<DatabaseTableDef<?>> getDatabaseTables() {
		List<DatabaseTableDef<?>> result = new ArrayList<>();
		result.add(new DetoxMessageQueueTable());
		result.add(new DetoxProcessedMessageQueueTable());
		result.add(new DetoxTaskConfigurationTable());
		result.add(new DetoxTaskRefreshRequestTable());
		return result;
	}

	@Override
	public Map<String,List<DatabaseTableDef<?>>> getModuleTables() {
		Map<String,List<DatabaseTableDef<?>>> result = new LinkedHashMap<>();
		List<DatabaseTableDef<?>> taskTables = new ArrayList<>();
		taskTables.add(new DetoxTaskConfigurationTable());
		taskTables.add(new DetoxTaskRefreshRequestTable());
		result.put("tasks", taskTables);
		return result;
	}
}
