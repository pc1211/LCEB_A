package com.example.pgyl.lceb_a;

import com.example.pgyl.pekislib_a.InputButtonsActivity;

import static com.example.pgyl.pekislib_a.Constants.REG_EXP_POSITIVE_INTEGER;
import static com.example.pgyl.pekislib_a.Constants.REG_EXP_POSITIVE_INTEGER_ERROR_MESSAGE;
import static com.example.pgyl.pekislib_a.StringDB.TABLE_ID_INDEX;
import static com.example.pgyl.pekislib_a.StringDBTables.TABLE_IDS;

public class StringDBTables {
    public static String tileIDPrefix = "TILE";     //  Pour TILE1, TILE2, ...
    public static String targetIDPrefix = "TARGET";   //  Pour TARGET

    enum LCEB_TABLES {   // Les tables, rattachées à leurs champs de data
        TILES_TARGET(TilesTargetTableDataFields.TilesTarget.class, "Tiles and Target");

        private int dataFieldsCount;
        private String description;

        LCEB_TABLES(Class<? extends TilesTargetTableDataFields> swTimerTableFields, String description) {
            dataFieldsCount = swTimerTableFields.getEnumConstants().length;
            this.description = description;
        }

        public String DESCRIPTION() {
            return description;
        }

        public int INDEX() {
            return ordinal();
        }

        public int getDataFieldsCount() {
            return dataFieldsCount;
        }
    }

    private interface TilesTargetTableDataFields {  //  Les champs de data, par table

        enum TilesTarget implements TilesTargetTableDataFields {
            VALUE("Value");

            private String valueLabel;

            TilesTarget(String valueLabel) {
                this.valueLabel = valueLabel;
            }

            public int INDEX() {
                return ordinal() + 1;
            }   //  INDEX 0 pour identifiant utilisateur

            public String LABEL() {
                return valueLabel;
            }
        }
    }

    public static int getLCEBTableDataFieldsCount(String tableName) {
        return LCEB_TABLES.valueOf(tableName).getDataFieldsCount();
    }

    public static int getLCEBTableIndex(String tableName) {
        return LCEB_TABLES.valueOf(tableName).INDEX();
    }

    public static String getLCEBTableDescription(String tableName) {
        return LCEB_TABLES.valueOf(tableName).DESCRIPTION();
    }

    //region TILES_TARGET
    public static String getTilesTargetTableName() {
        return LCEB_TABLES.TILES_TARGET.toString();
    }

    public static String[][] getTilesTargetInits() {
        final String[][] TABLE_TILES_TARGET_INITS = {
                {TABLE_IDS.LABEL.toString(), TilesTargetTableDataFields.TilesTarget.VALUE.LABEL()},
                {TABLE_IDS.KEYBOARD.toString(), InputButtonsActivity.KEYBOARDS.POSINT.toString()},
                {TABLE_IDS.REGEXP.toString(), REG_EXP_POSITIVE_INTEGER},
                {TABLE_IDS.REGEXP_ERROR_MESSAGE.toString(), REG_EXP_POSITIVE_INTEGER_ERROR_MESSAGE},
                {tileIDPrefix + "1", "25"},
                {tileIDPrefix + "2", "50"},
                {tileIDPrefix + "3", "75"},
                {tileIDPrefix + "4", "100"},
                {tileIDPrefix + "5", "3"},
                {tileIDPrefix + "6", "6"},
                {targetIDPrefix, "952"}
        };
        return TABLE_TILES_TARGET_INITS;
    }

    public static int getTilesTargetValueIndex() {
        return TilesTargetTableDataFields.TilesTarget.VALUE.INDEX();
    }

    public static int tileValueRowToTileValue(String[] tileValueRow) {
        int tileValue = 0;
        if (tileValueRow != null)
            tileValue = Integer.valueOf(tileValueRow[getTilesTargetValueIndex()]);
        return tileValue;
    }

    public static int targetValueRowToTargetValue(String[] targetValueRow) {
        int targetValue = 0;
        if (targetValueRow != null)
            targetValue = Integer.valueOf(targetValueRow[getTilesTargetValueIndex()]);
        return targetValue;
    }

    public static String[] tileValueToTileValueRow(int tileValue, int numTile) {
        String[] tileValueRow = new String[1 + TilesTargetTableDataFields.TilesTarget.values().length];  //  Champ ID + données
        tileValueRow[TABLE_ID_INDEX] = tileIDPrefix + numTile;
        tileValueRow[getTilesTargetValueIndex()] = String.valueOf(tileValue);
        return tileValueRow;
    }

    public static String[] targetValueToTargetValueRow(int target) {
        String[] targetRow = new String[1 + TilesTargetTableDataFields.TilesTarget.values().length];   //  Champ ID + données
        targetRow[TABLE_ID_INDEX] = targetIDPrefix;
        targetRow[getTilesTargetValueIndex()] = String.valueOf(target);
        return targetRow;
    }
    //endregion
}
