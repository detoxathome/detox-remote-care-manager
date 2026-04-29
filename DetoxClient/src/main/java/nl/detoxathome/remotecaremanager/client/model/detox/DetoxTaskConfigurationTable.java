package nl.detoxathome.remotecaremanager.client.model.detox;

import nl.detoxathome.remotecaremanager.dao.Database;
import nl.detoxathome.remotecaremanager.dao.DatabaseTableDef;
import nl.rrd.utils.exception.DatabaseException;

public class DetoxTaskConfigurationTable extends
		DatabaseTableDef<DetoxTaskConfiguration> {
	public static final String NAME = "task_configurations";

	private static final int VERSION = 0;

	public DetoxTaskConfigurationTable() {
		super(NAME, DetoxTaskConfiguration.class, VERSION, false);
	}

	@Override
	public int upgradeTable(int version, Database db, String physTable)
			throws DatabaseException {
		return VERSION;
	}
}
