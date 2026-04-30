package nl.detoxathome.remotecaremanager.service.model;

import nl.detoxathome.remotecaremanager.dao.Database;
import nl.rrd.utils.exception.DatabaseException;
import nl.detoxathome.remotecaremanager.dao.DatabaseTableDef;

public class GroupTable extends DatabaseTableDef<Group> {
	public static String NAME = "group";
	
	private static int VERSION = 0;

	public GroupTable() {
		super(NAME, Group.class, VERSION, false);
	}

	@Override
	public int upgradeTable(int version, Database db, String physTable)
			throws DatabaseException {
		return 0;
	}
}
