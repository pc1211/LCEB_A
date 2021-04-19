package com.example.pgyl.lceb_a;

import com.example.pgyl.pekislib_a.StringDB;

import static com.example.pgyl.lceb_a.StringDBTables.platePattern;
import static com.example.pgyl.lceb_a.StringDBTables.targetPattern;
import static com.example.pgyl.pekislib_a.StringDB.TABLE_ID_INDEX;

public class StringDBUtils {

    //region TABLES
    public static void createLCEBTableIfNotExists(StringDB stringDB, String tableName) {
        stringDB.createTableIfNotExists(tableName, 1 + StringDBTables.getLCEBTableDataFieldsCount(tableName));   //  Champ ID + Données;
    }

    public static void initializeTablePlatesTarget(StringDB stringDB) {
        stringDB.insertOrReplaceRows(StringDBTables.getPlatesTargetTableName(), StringDBTables.getPlatesTargetInits());
    }

    public static int getDBPlateInitCount(StringDB stringDB) {
        int plateCount = 0;
        String[][] plateValueRows = stringDB.selectRows(StringDBTables.getPlatesTargetTableName(), stringDB.getFieldName(TABLE_ID_INDEX) + " LIKE '" + platePattern + "%'");
        if (plateValueRows != null) plateCount = plateValueRows.length;
        plateValueRows = null;
        return plateCount;
    }

    public static String[] getDBPlateValue(StringDB stringDB, int numPlate) {
        return stringDB.selectRowById(StringDBTables.getPlatesTargetTableName(), platePattern + numPlate);
    }

    public static String[] getDBTargetValue(StringDB stringDB) {
        return stringDB.selectRowById(StringDBTables.getPlatesTargetTableName(), targetPattern);
    }

    public static void saveDBPlateValue(StringDB stringDB, String[] values) {
        stringDB.insertOrReplaceRow(StringDBTables.getPlatesTargetTableName(), values);
    }

    public static void saveDBTargetValue(StringDB stringDB, String[] values) {
        stringDB.insertOrReplaceRow(StringDBTables.getPlatesTargetTableName(), values);
    }
    //endregion
}
