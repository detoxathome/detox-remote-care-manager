package nl.detoxathome.remotecaremanager.clientextensions.sensor.stepcounter;

import nl.detoxathome.remotecaremanager.client.sensor.BaseSensor;
import nl.detoxathome.remotecaremanager.dao.DatabaseTableDef;
import nl.detoxathome.remotecaremanager.clientextensions.sensor.SensorProduct;

import java.util.ArrayList;
import java.util.List;

public class DetoxStepCounterSensor extends BaseSensor {
	public DetoxStepCounterSensor() {
		super(SensorProduct.EXAMPLE_STEP_COUNTER);
	}

	@Override
	public List<DatabaseTableDef<?>> getTables() {
		List<DatabaseTableDef<?>> tables = new ArrayList<>();
		tables.add(new DetoxStepsMinuteTable());
		tables.add(new DetoxStepsDayTable());
		return tables;
	}
}
