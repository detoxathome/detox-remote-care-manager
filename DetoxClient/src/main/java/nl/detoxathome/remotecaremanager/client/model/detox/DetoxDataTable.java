//package nl.detoxathome.remotecaremanager.client.model.detox;
//
//import nl.detoxathome.remotecaremanager.client.model.questionnaire.QuestionnaireData;
//import nl.rrd.utils.exception.DatabaseException;
//
//import java.util.LinkedHashMap;
//import java.util.List;
//import java.util.Map;
//
//
//import nl.detoxathome.remotecaremanager.dao.Database;
//import nl.detoxathome.remotecaremanager.dao.DatabaseColumnDef;
//import nl.detoxathome.remotecaremanager.dao.DatabaseCriteria;
//import nl.detoxathome.remotecaremanager.dao.DatabaseSort;
//import nl.detoxathome.remotecaremanager.dao.DatabaseTableDef;
//import nl.detoxathome.remotecaremanager.dao.DatabaseType;
//
//
//
//public class DetoxDataTable extends
//        DatabaseTableDef<>
//{
//}
//
//
//
//
//public class QuestionnaireDataTable extends
//        DatabaseTableDef<QuestionnaireData> {
//    public static final String NAME = "questionnaire_data";
//
//    private static final int VERSION = 1;
//
//    public QuestionnaireDataTable() {
//        super(NAME, QuestionnaireData.class, VERSION, true);
//    }
//
//    @Override
//    public int upgradeTable(int version, Database db, String physTable)
//            throws DatabaseException {
//        if (version == 0)
//            return upgradeTableV0(db, physTable);
//        else
//            return 1;
//    }
//
//    private int upgradeTableV0(Database db, String physTable)
//            throws DatabaseException {
//        DatabaseSort[] sort = new DatabaseSort[] {
//                new DatabaseSort("utcTime", true)
//        };
//        List<Map<String,?>> maps = db.selectMaps(physTable, null, null, 0,
//                sort);
//        db.dropColumn(physTable, "newanswers");
//        db.addColumn(physTable, new DatabaseColumnDef("newanswers",
//                DatabaseType.TEXT));
//        for (Map<String,?> map : maps) {
//            DatabaseCriteria criteria = new DatabaseCriteria.Equal(
//                    "id", (String)map.get("id"));
//            Map<String,Object> values = new LinkedHashMap<>();
//            values.put("newanswers", map.get("answers"));
//            db.update(physTable, null, criteria, values);
//        }
//        db.renameColumn(physTable, "answers", "oldanswers");
//        db.renameColumn(physTable, "newanswers", "answers");
//        db.dropColumn(physTable, "oldanswers");
//        return 1;
//    }
//}
