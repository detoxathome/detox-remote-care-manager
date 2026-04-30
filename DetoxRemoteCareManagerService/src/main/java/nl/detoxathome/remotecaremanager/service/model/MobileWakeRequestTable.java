package nl.detoxathome.remotecaremanager.service.model;

import nl.detoxathome.remotecaremanager.dao.Database;
import nl.detoxathome.remotecaremanager.dao.DatabaseTableDef;
import nl.rrd.utils.exception.DatabaseException;

public class MobileWakeRequestTable
		extends DatabaseTableDef<MobileWakeRequest> {
	public static final String NAME = "wake_mobile_requests";

	private static final int VERSION = 0;

	public MobileWakeRequestTable() {
		super(NAME, MobileWakeRequest.class, VERSION, false);
	}

	@Override
	public int upgradeTable(int version, Database db, String physTable)
			throws DatabaseException {
		return 0;
	}
}
