package com.example.pgyl.lceb_a;

import com.example.pgyl.pekislib_a.InputButtonsActivity;

import static com.example.pgyl.pekislib_a.Constants.REG_EXP_POSITIVE_INTEGER;
import static com.example.pgyl.pekislib_a.Constants.REG_EXP_POSITIVE_INTEGER_ERROR_MESSAGE;
import static com.example.pgyl.pekislib_a.StringDB.TABLE_ID_INDEX;
import static com.example.pgyl.pekislib_a.StringDBTables.TABLE_IDS;

public class StringDBTables {
    public static String platePattern = "PLATE";     //  Pour PLATE1, PLATE2, ...
    public static String targetPattern = "TARGET";   //  Pour TARGET

    enum LCEB_TABLES {   // Les tables, rattachées à leurs champs de data
        PLATES_TARGET(PlatesTargetTableDataFields.PlatesTarget.class, "Plates and Target");

        private int dataFieldsCount;
        private String description;

        LCEB_TABLES(Class<? extends PlatesTargetTableDataFields> swTimerTableFields, String description) {
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

    private interface PlatesTargetTableDataFields {  //  Les champs de data, par table

        enum PlatesTarget implements PlatesTargetTableDataFields {
            VALUE("Value");

            private String valueLabel;

            PlatesTarget(String valueLabel) {
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

    //region PLATES_TARGET
    public static String getPlatesTargetTableName() {
        return LCEB_TABLES.PLATES_TARGET.toString();
    }

    public static String[][] getPlatesTargetInits() {
        final String[][] TABLE_PLATES_TARGET_INITS = {
                {TABLE_IDS.LABEL.toString(), PlatesTargetTableDataFields.PlatesTarget.VALUE.LABEL()},
                {TABLE_IDS.KEYBOARD.toString(), InputButtonsActivity.KEYBOARDS.POSINT.toString()},
                {TABLE_IDS.REGEXP.toString(), REG_EXP_POSITIVE_INTEGER},
                {TABLE_IDS.REGEXP_ERROR_MESSAGE.toString(), REG_EXP_POSITIVE_INTEGER_ERROR_MESSAGE},
                {platePattern + "1", "25"},
                {platePattern + "2", "50"},
                {platePattern + "3", "75"},
                {platePattern + "4", "100"},
                {platePattern + "5", "3"},
                {platePattern + "6", "6"},
                {targetPattern, "952"}
        };
        return TABLE_PLATES_TARGET_INITS;
    }

    public static int getPlatesTargetValueIndex() {
        return PlatesTargetTableDataFields.PlatesTarget.VALUE.INDEX();
    }

    public static int plateValueRowToPlateValue(String[] plateValueRow) {
        int plateValue = 0;
        if (plateValueRow != null)
            plateValue = Integer.valueOf(plateValueRow[getPlatesTargetValueIndex()]);
        return plateValue;
    }

    public static int targetRowToTarget(String[] targetRow) {
        int target = 0;
        if (targetRow != null) target = Integer.valueOf(targetRow[getPlatesTargetValueIndex()]);
        return target;
    }

    public static String[] plateValueToPlateValueRow(int plateValue, int numPlate) {
        String[] plateValueRow = new String[1 + PlatesTargetTableDataFields.PlatesTarget.values().length];  //  Champ ID + données
        plateValueRow[TABLE_ID_INDEX] = platePattern + numPlate;
        plateValueRow[getPlatesTargetValueIndex()] = String.valueOf(plateValue);
        return plateValueRow;
    }

    public static String[] targetToTargetRow(int target) {
        String[] targetRow = new String[1 + PlatesTargetTableDataFields.PlatesTarget.values().length];   //  Champ ID + données
        targetRow[TABLE_ID_INDEX] = targetPattern;
        targetRow[getPlatesTargetValueIndex()] = String.valueOf(target);
        return targetRow;
    }
    //endregion
}
