package nl.detoxathome.remotecaremanager.client.model.notification;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import nl.detoxathome.remotecaremanager.dao.Database;
import nl.detoxathome.remotecaremanager.dao.DatabaseColumnDef;
import nl.detoxathome.remotecaremanager.dao.DatabaseCriteria;
import nl.rrd.utils.exception.DatabaseException;
import nl.detoxathome.remotecaremanager.dao.DatabaseSort;
import nl.detoxathome.remotecaremanager.dao.DatabaseTableDef;
import nl.detoxathome.remotecaremanager.dao.DatabaseType;

public class AppNotificationTable extends DatabaseTableDef<AppNotification> {
	public static final String NAME = "app_notifications";

	private static final int VERSION = 1;

	public AppNotificationTable() {
		super(NAME, AppNotification.class, VERSION, true);
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
		DatabaseSort[] sort = new DatabaseSort[] {
				new DatabaseSort("utcTime", true)
		};
		List<Map<String,?>> maps = db.selectMaps(physTable, null, null, 0,
				sort);
		db.dropColumn(physTable, "newdata");
		db.addColumn(physTable, new DatabaseColumnDef("newdata",
				DatabaseType.TEXT));
		for (Map<String,?> map : maps) {
			DatabaseCriteria criteria = new DatabaseCriteria.Equal(
					"id", (String)map.get("id"));
			Map<String,Object> values = new LinkedHashMap<>();
			values.put("newdata", map.get("data"));
			db.update(physTable, null, criteria, values);
		}
		db.renameColumn(physTable, "data", "olddata");
		db.renameColumn(physTable, "newdata", "data");
		db.dropColumn(physTable, "olddata");
		return 1;
	}
}
