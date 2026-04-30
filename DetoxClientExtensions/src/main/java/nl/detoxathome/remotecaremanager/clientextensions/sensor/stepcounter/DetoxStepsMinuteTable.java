package nl.detoxathome.remotecaremanager.clientextensions.sensor.stepcounter;

import nl.detoxathome.remotecaremanager.client.model.sample.IntListSample;
import nl.detoxathome.remotecaremanager.dao.Database;
import nl.detoxathome.remotecaremanager.dao.DatabaseTableDef;
import nl.rrd.utils.exception.DatabaseException;

public class DetoxStepsMinuteTable extends DatabaseTableDef<IntListSample> {
	public static final String NAME = "example_steps_minute";

	private static final int VERSION = 0;

	public DetoxStepsMinuteTable() {
		super(NAME, IntListSample.class, VERSION, true);
	}

	@Override
	public int upgradeTable(int version, Database db, String physTable)
			throws DatabaseException {
		return 0;
	}
}
