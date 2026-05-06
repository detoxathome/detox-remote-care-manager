package nl.detoxathome.remotecaremanager.service.model;

import nl.detoxathome.remotecaremanager.dao.Database;
import nl.detoxathome.remotecaremanager.dao.DatabaseTableDef;
import nl.rrd.utils.exception.DatabaseException;

public class DataExportTable extends DatabaseTableDef<DataExportRecord> {
	public static final String NAME = "data_exports";

	private static final int VERSION = 0;

	public DataExportTable() {
		super(NAME, DataExportRecord.class, VERSION, false);
	}

	@Override
	public int upgradeTable(int version, Database db, String physTable)
			throws DatabaseException {
		return 0;
	}
}
