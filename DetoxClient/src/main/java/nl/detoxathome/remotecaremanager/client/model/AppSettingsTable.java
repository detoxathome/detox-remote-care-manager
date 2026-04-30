package nl.detoxathome.remotecaremanager.client.model;

import nl.rrd.utils.exception.DatabaseException;
import nl.detoxathome.remotecaremanager.dao.Database;
import nl.detoxathome.remotecaremanager.dao.DatabaseTableDef;

public class AppSettingsTable extends DatabaseTableDef<AppSettings> {
	public static final String NAME = "app_settings";

	private static final int VERSION = 0;

	public AppSettingsTable() {
		super(NAME, AppSettings.class, VERSION, false);
	}

	@Override
	public int upgradeTable(int version, Database db, String physTable)
			throws DatabaseException {
		return 0;
	}
}
