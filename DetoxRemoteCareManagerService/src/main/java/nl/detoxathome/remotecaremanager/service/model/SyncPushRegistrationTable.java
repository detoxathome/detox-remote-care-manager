package nl.detoxathome.remotecaremanager.service.model;

import nl.detoxathome.remotecaremanager.dao.Database;
import nl.rrd.utils.exception.DatabaseException;
import nl.detoxathome.remotecaremanager.dao.DatabaseTableDef;

public class SyncPushRegistrationTable extends
DatabaseTableDef<SyncPushRegistration> {
	public static final String NAME = "sync_push_registrations";

	public static final int VERSION = 0;

	public SyncPushRegistrationTable() {
		super(NAME, SyncPushRegistration.class, VERSION, false);
	}

	@Override
	public int upgradeTable(int version, Database db, String physTable)
			throws DatabaseException {
		return 0;
	}
}
