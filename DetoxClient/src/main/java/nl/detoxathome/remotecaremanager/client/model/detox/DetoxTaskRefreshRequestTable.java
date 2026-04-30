package nl.detoxathome.remotecaremanager.client.model.detox;

import nl.detoxathome.remotecaremanager.dao.Database;
import nl.detoxathome.remotecaremanager.dao.DatabaseTableDef;
import nl.rrd.utils.exception.DatabaseException;

public class DetoxTaskRefreshRequestTable extends
		DatabaseTableDef<DetoxTaskRefreshRequest> {
	public static final String NAME = "task_refresh_requests";

	private static final int VERSION = 0;

	public DetoxTaskRefreshRequestTable() {
		super(NAME, DetoxTaskRefreshRequest.class, VERSION, false);
	}

	@Override
	public int upgradeTable(int version, Database db, String physTable)
			throws DatabaseException {
		return VERSION;
	}
}
