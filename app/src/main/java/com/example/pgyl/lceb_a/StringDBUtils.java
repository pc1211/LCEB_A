package com.example.pgyl.lceb_a;

import com.example.pgyl.pekislib_a.StringDB;

import static com.example.pgyl.lceb_a.StringDBTables.targetIDPrefix;
import static com.example.pgyl.lceb_a.StringDBTables.tileIDPrefix;
import static com.example.pgyl.pekislib_a.StringDB.getIDPatternWhereCondition;

public class StringDBUtils {

    //region TABLES
    public static void createLCEBTableIfNotExists(StringDB stringDB, String tableName) {
        stringDB.createTableIfNotExists(tableName, 1 + StringDBTables.getLCEBTableDataFieldsCount(tableName));   //  Champ ID + Données;
    }

    public static void initializeTableTilesTarget(StringDB stringDB) {
        stringDB.insertOrReplaceRows(StringDBTables.getTilesTargetTableName(), StringDBTables.getTilesTargetInits());
    }

    public static int getDBTilesInitCount(StringDB stringDB) {
        int tilesCount = 0;
        String[][] tileValuesRows = getDBTileValuesRows(stringDB);
        if (tileValuesRows != null) tilesCount = tileValuesRows.length;
        tileValuesRows = null;
        return tilesCount;
    }

    public static String[][] getDBTileValuesRows(StringDB stringDB) {
        return stringDB.selectRows(StringDBTables.getTilesTargetTableName(), getIDPatternWhereCondition(tileIDPrefix + "%"));
    }

    public static String[] getDBTileValueRow(StringDB stringDB, int numTile) {
        return stringDB.selectRowById(StringDBTables.getTilesTargetTableName(), tileIDPrefix + numTile);
    }

    public static String[] getDBTargetValueRow(StringDB stringDB) {
        return stringDB.selectRowById(StringDBTables.getTilesTargetTableName(), targetIDPrefix);
    }

    public static void saveDBTileValueRow(StringDB stringDB, String[] values) {
        stringDB.insertOrReplaceRow(StringDBTables.getTilesTargetTableName(), values);
    }

    public static void saveDBTargetValueRow(StringDB stringDB, String[] values) {
        stringDB.insertOrReplaceRow(StringDBTables.getTilesTargetTableName(), values);
    }
    //endregion
}
