package nl.detoxathome.remotecaremanager.client.model;

import nl.detoxathome.remotecaremanager.dao.Database;
import nl.detoxathome.remotecaremanager.dao.DatabaseTableDef;
import nl.rrd.utils.exception.DatabaseException;

public class RegexUserAccessTable extends DatabaseTableDef<RegexUserAccess> {
	private static final int VERSION = 0;

	public RegexUserAccessTable(String name) {
		super(name, RegexUserAccess.class, VERSION, false);
	}

	@Override
	public int upgradeTable(int version, Database db, String physTable)
			throws DatabaseException {
		return 0;
	}
}
