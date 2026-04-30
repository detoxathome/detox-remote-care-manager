package nl.detoxathome.remotecaremanager.clientextensions.sensor.stepcounter;

import nl.detoxathome.remotecaremanager.client.model.sample.IntLocalSample;
import nl.detoxathome.remotecaremanager.dao.Database;
import nl.detoxathome.remotecaremanager.dao.DatabaseTableDef;
import nl.rrd.utils.exception.DatabaseException;

public class DetoxStepsDayTable extends DatabaseTableDef<IntLocalSample> {
	public static final String NAME = "example_steps_day";

	private static final int VERSION = 0;

	public DetoxStepsDayTable() {
		super(NAME, IntLocalSample.class, VERSION, true);
	}

	@Override
	public int upgradeTable(int version, Database db, String physTable)
			throws DatabaseException {
		return 0;
	}
}
