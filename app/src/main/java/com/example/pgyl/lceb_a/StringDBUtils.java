package com.example.pgyl.lceb_a;

import com.example.pgyl.pekislib_a.StringDB;

import static com.example.pgyl.lceb_a.StringDBTables.plateIDPrefix;
import static com.example.pgyl.lceb_a.StringDBTables.targetIDPrefix;
import static com.example.pgyl.pekislib_a.StringDB.getIDPatternWhereCondition;

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
        String[][] plateValueRows = getDBPlateValueRows(stringDB);
        if (plateValueRows != null) plateCount = plateValueRows.length;
        plateValueRows = null;
        return plateCount;
    }

    public static String[][] getDBPlateValueRows(StringDB stringDB) {
        return stringDB.selectRows(StringDBTables.getPlatesTargetTableName(), getIDPatternWhereCondition(plateIDPrefix + "%"));
    }

    public static String[] getDBPlateValueRow(StringDB stringDB, int numPlate) {
        return stringDB.selectRowById(StringDBTables.getPlatesTargetTableName(), plateIDPrefix + numPlate);
    }

    public static String[] getDBTargetValueRow(StringDB stringDB) {
        return stringDB.selectRowById(StringDBTables.getPlatesTargetTableName(), targetIDPrefix);
    }

    public static void saveDBPlateValueRow(StringDB stringDB, String[] values) {
        stringDB.insertOrReplaceRow(StringDBTables.getPlatesTargetTableName(), values);
    }

    public static void saveDBTargetValueRow(StringDB stringDB, String[] values) {
        stringDB.insertOrReplaceRow(StringDBTables.getPlatesTargetTableName(), values);
    }
    //endregion
}
