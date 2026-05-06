package nl.detoxathome.remotecaremanager.client.model.questionnaire;

import nl.detoxathome.remotecaremanager.dao.Database;
import nl.detoxathome.remotecaremanager.dao.DatabaseColumnDef;
import nl.detoxathome.remotecaremanager.dao.DatabaseTableDef;
import nl.detoxathome.remotecaremanager.dao.DatabaseType;
import nl.rrd.utils.exception.DatabaseException;

public class QuestionnaireTable extends DatabaseTableDef<QuestionnaireRecord> {
	public static final String NAME = "questionnaires";

	private static final int VERSION = 1;

	public QuestionnaireTable() {
		super(NAME, QuestionnaireRecord.class, VERSION, false);
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
		db.delete(physTable, null, null);
		db.addColumn(physTable, new DatabaseColumnDef("localTime",
				DatabaseType.STRING, true));
		db.addColumn(physTable, new DatabaseColumnDef("utcTime",
				DatabaseType.LONG, true));
		db.addColumn(physTable, new DatabaseColumnDef("timezone",
				DatabaseType.STRING));
		return 1;
	}
}
