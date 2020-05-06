package com.example.pgyl.swtimer_a;

import com.example.pgyl.pekislib_a.StringDB;

import static com.example.pgyl.swtimer_a.StringDBTables.getBackScreenColorsInits;
import static com.example.pgyl.swtimer_a.StringDBTables.getBackScreenColorsTableName;
import static com.example.pgyl.swtimer_a.StringDBTables.getChronoTimersTableName;
import static com.example.pgyl.swtimer_a.StringDBTables.getDotMatrixDisplayCoeffsInits;
import static com.example.pgyl.swtimer_a.StringDBTables.getDotMatrixDisplayCoeffsTableName;
import static com.example.pgyl.swtimer_a.StringDBTables.getDotMatrixDisplayColorsInits;
import static com.example.pgyl.swtimer_a.StringDBTables.getDotMatrixDisplayColorsTableName;
import static com.example.pgyl.swtimer_a.StringDBTables.getPresetsCTInits;
import static com.example.pgyl.swtimer_a.StringDBTables.getPresetsCTTableName;
import static com.example.pgyl.swtimer_a.StringDBTables.getStateButtonsColorsInits;
import static com.example.pgyl.swtimer_a.StringDBTables.getStateButtonsColorsTableName;
import static com.example.pgyl.swtimer_a.StringDBTables.getSwTimerTableDataFieldsCount;

public class StringDBUtils {

    //region TABLES
    public static void createSwtimerTableIfNotExists(StringDB stringDB, String tableName) {
        stringDB.createTableIfNotExists(tableName, 1 + getSwTimerTableDataFieldsCount(tableName));   //  Champ ID + Données;
    }

    public static void initializeTablePresetsCT(StringDB stringDB) {
        stringDB.insertOrReplaceRows(getPresetsCTTableName(), getPresetsCTInits());
    }

    public static void initializeTableDotMatrixDisplayColors(StringDB stringDB) {
        stringDB.insertOrReplaceRows(getDotMatrixDisplayColorsTableName(), getDotMatrixDisplayColorsInits());
    }

    public static void initializeTableDotMatrixDisplayDotSpacingCoeffs(StringDB stringDB) {
        stringDB.insertOrReplaceRows(getDotMatrixDisplayCoeffsTableName(), getDotMatrixDisplayCoeffsInits());
    }

    public static void initializeTableStateButtonsColors(StringDB stringDB) {
        stringDB.insertOrReplaceRows(getStateButtonsColorsTableName(), getStateButtonsColorsInits());
    }

    public static void initializeTableBackScreenColors(StringDB stringDB) {
        stringDB.insertOrReplaceRows(getBackScreenColorsTableName(), getBackScreenColorsInits());
    }
    //endregion

    //region CHRONO_TIMERS
    public static String[] getChronoTimerById(StringDB stringDB, int idct) {
        return stringDB.selectRowByIdOrCreate(getChronoTimersTableName(), String.valueOf(idct));
    }

    public static String[][] getChronoTimers(StringDB stringDB) {
        return stringDB.selectRows(getChronoTimersTableName(), null);
    }

    public static void saveChronoTimer(StringDB stringDB, String[] values) {
        stringDB.insertOrReplaceRow(getChronoTimersTableName(), values);
    }

    public static void saveChronoTimers(StringDB stringDB, String[][] values) {
        stringDB.deleteRows(getChronoTimersTableName(), null);
        stringDB.insertOrReplaceRows(getChronoTimersTableName(), values);
    }
    //endregion

}
