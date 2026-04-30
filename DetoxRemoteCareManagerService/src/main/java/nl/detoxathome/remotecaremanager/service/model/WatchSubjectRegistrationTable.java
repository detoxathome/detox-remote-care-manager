package nl.detoxathome.remotecaremanager.service.model;

import nl.detoxathome.remotecaremanager.dao.Database;
import nl.rrd.utils.exception.DatabaseException;
import nl.detoxathome.remotecaremanager.dao.DatabaseColumnDef;
import nl.detoxathome.remotecaremanager.dao.DatabaseTableDef;
import nl.detoxathome.remotecaremanager.dao.DatabaseType;

public class WatchSubjectRegistrationTable extends
DatabaseTableDef<WatchSubjectRegistration> {
	public static final String NAME = "watch_subject_registrations";
	
	private static final int VERSION = 1;
	
	public WatchSubjectRegistrationTable() {
		super(NAME, WatchSubjectRegistration.class, VERSION, false);
	}

	@Override
	public int upgradeTable(int version, Database db, String physTable)
			throws DatabaseException {
		if (version == 0)
			return upgradeTableV0(db, physTable);
		else
			return 1;
	}

	private int upgradeTableV0(Database db, String physTable)
			throws DatabaseException {
		db.dropColumn(physTable, "events");
		db.addColumn(physTable, new DatabaseColumnDef("events",
				DatabaseType.TEXT));
		return 1;
	}
}
