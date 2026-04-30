package nl.detoxathome.remotecaremanager.clientextensions.project.defaultproject;

import nl.detoxathome.remotecaremanager.client.model.LinkedSensorTable;
import nl.detoxathome.remotecaremanager.client.model.notification.AppNotificationTable;
import nl.detoxathome.remotecaremanager.client.model.questionnaire.QuestionnaireDataTable;
import nl.detoxathome.remotecaremanager.client.project.BaseProject;
import nl.detoxathome.remotecaremanager.client.sensor.BaseSensor;
import nl.detoxathome.remotecaremanager.dao.DatabaseTableDef;
import nl.detoxathome.remotecaremanager.clientextensions.sensor.stepcounter.DetoxStepCounterSensor;

import java.util.ArrayList;
import java.util.List;

public class DefaultProject extends BaseProject {
	public DefaultProject() {
		super("default", "Default");
	}

	@Override
	public List<BaseSensor> getSensors() {
		List<BaseSensor> sensors = new ArrayList<>();
		sensors.add(new DetoxStepCounterSensor());
		return sensors;
	}

	@Override
	public List<DatabaseTableDef<?>> getDatabaseTables() {
		List<DatabaseTableDef<?>> result = new ArrayList<>();
		result.addAll(getSensorDatabaseTables());
		result.add(new QuestionnaireDataTable());
		result.add(new LinkedSensorTable());
		result.add(new AppNotificationTable());
		return result;
	}
}
