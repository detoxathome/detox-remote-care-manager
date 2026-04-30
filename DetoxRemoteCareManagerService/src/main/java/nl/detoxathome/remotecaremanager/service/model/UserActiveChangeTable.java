package nl.detoxathome.remotecaremanager.service.model;

import nl.detoxathome.remotecaremanager.dao.Database;
import nl.rrd.utils.exception.DatabaseException;
import nl.detoxathome.remotecaremanager.dao.DatabaseTableDef;

public class UserActiveChangeTable extends DatabaseTableDef<UserActiveChange> {
	public static final String NAME = "user_active_changes";
	
	private static final int VERSION = 0;

	public UserActiveChangeTable() {
		super(NAME, UserActiveChange.class, VERSION, false);
	}

	@Override
	public int upgradeTable(int version, Database db, String physTable)
			throws DatabaseException {
		return 0;
	}
}
