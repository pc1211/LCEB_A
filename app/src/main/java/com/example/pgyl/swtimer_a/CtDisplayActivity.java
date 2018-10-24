package com.example.pgyl.swtimer_a;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.example.pgyl.pekislib_a.ColorPickerActivity;
import com.example.pgyl.pekislib_a.DotMatrixDisplayView;
import com.example.pgyl.pekislib_a.HelpActivity;
import com.example.pgyl.pekislib_a.PresetsActivity;
import com.example.pgyl.pekislib_a.StringShelfDatabase;
import com.example.pgyl.pekislib_a.SymbolButtonView;

import java.util.logging.Level;
import java.util.logging.Logger;

import static com.example.pgyl.pekislib_a.Constants.ACTIVITY_EXTRA_KEYS;
import static com.example.pgyl.pekislib_a.Constants.ACTIVITY_START_TYPE;
import static com.example.pgyl.pekislib_a.Constants.COLOR_PREFIX;
import static com.example.pgyl.pekislib_a.Constants.NOT_FOUND;
import static com.example.pgyl.pekislib_a.Constants.PEKISLIB_ACTIVITIES;
import static com.example.pgyl.pekislib_a.Constants.PEKISLIB_TABLES;
import static com.example.pgyl.pekislib_a.Constants.SHP_FILE_NAME_SUFFIX;
import static com.example.pgyl.pekislib_a.Constants.TABLE_ACTIVITY_INFOS_DATA_FIELDS;
import static com.example.pgyl.pekislib_a.Constants.TABLE_EXTRA_KEYS;
import static com.example.pgyl.pekislib_a.Constants.TABLE_IDS;
import static com.example.pgyl.pekislib_a.HelpActivity.HELP_ACTIVITY_EXTRA_KEYS;
import static com.example.pgyl.pekislib_a.HelpActivity.HELP_ACTIVITY_TITLE;
import static com.example.pgyl.pekislib_a.MiscUtils.caseFirstUpOtherLow;
import static com.example.pgyl.pekislib_a.PresetsActivity.PRESET_ACTIVITY_DATA_TYPES;
import static com.example.pgyl.pekislib_a.PresetsActivity.PRESET_ACTIVITY_EXTRA_KEYS;
import static com.example.pgyl.pekislib_a.TimeDateUtils.TIMEUNITS;
import static com.example.pgyl.pekislib_a.TimeDateUtils.convertMsToHms;
import static com.example.pgyl.swtimer_a.Constants.COLOR_ITEMS;
import static com.example.pgyl.swtimer_a.Constants.SWTIMER_ACTIVITIES;
import static com.example.pgyl.swtimer_a.Constants.SWTIMER_SHP_KEY_NAMES;
import static com.example.pgyl.swtimer_a.Constants.SWTIMER_TABLES;
import static com.example.pgyl.swtimer_a.Constants.TABLE_COLORS_TIMEBUTTONS_DATA_FIELDS;
import static com.example.pgyl.swtimer_a.Constants.TABLE_PRESETS_CT_DATA_FIELDS;
import static com.example.pgyl.swtimer_a.CtRecord.MODE;
import static com.example.pgyl.swtimer_a.CtRecord.USE_CLOCK_APP;

public class CtDisplayActivity extends Activity {
    //region Constantes
    private enum COMMANDS {
        RUN(R.raw.ct_run), SPLIT(R.raw.ct_split), INVERT_CLOCK_APP_ALARM(R.raw.ct_bell), RESET(R.raw.ct_reset), CHRONO_MODE(R.raw.ct_chrono), TIMER_MODE(R.raw.ct_timer);

        private int valueId;

        COMMANDS(int valueId) {
            this.valueId = valueId;
        }

        public int ID() {
            return valueId;
        }
    }

    private enum BAR_MENU_ITEMS {KEEP_SCREEN}

    public enum CTDISPLAY_EXTRA_KEYS {
        CURRENT_CHRONO_TIMER_ID
    }

    private final int UPDATE_CT_DISPLAY_VIEW_TIME_INTERVAL_MS = 10;
    private final int ACTIVITY_CODE_MULTIPLIER = 100;  // Pour différencier les types d'appel à une même activité
    //endregion
    //region Variables
    private CtRecord currentCtRecord;
    private CtDisplayTimeRobot ctDisplayTimeRobot;
    private boolean keepScreen;
    private String[] timeColors;
    private String[] buttonColors;
    private String[] backScreenColors;
    private DotMatrixDisplayView timeDotMatrixDisplayView;
    private SymbolButtonView[] buttons;
    private Menu menu;
    private MenuItem[] barMenuItems;
    private LinearLayout backLayout;
    private boolean validReturnFromCalledActivity;
    private String calledActivity;
    private StringShelfDatabase stringShelfDatabase;
    private String shpFileName;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().requestFeature(Window.FEATURE_ACTION_BAR);
        setupOrientationLayout();
        setupButtons();
        setupTimeDotMatrixDisplayView();
        backLayout = (LinearLayout) findViewById(R.id.BACK_LAYOUT);
        validReturnFromCalledActivity = false;
    }

    @Override
    protected void onPause() {
        super.onPause();

        savePreferences();

        ctDisplayTimeRobot.stopAutomatic();
        ctDisplayTimeRobot.close();
        ctDisplayTimeRobot = null;
        saveCurrentColors(COLOR_ITEMS.TIME, timeColors);
        saveCurrentColors(COLOR_ITEMS.BUTTONS, buttonColors);
        saveCurrentColors(COLOR_ITEMS.BACK_SCREEN, backScreenColors);
        saveCurrentChronoTimer();
        currentCtRecord = null;
        stringShelfDatabase.close();
        stringShelfDatabase = null;
    }

    @Override
    protected void onResume() {
        super.onResume();

        long nowm = System.currentTimeMillis();
        shpFileName = getPackageName() + SHP_FILE_NAME_SUFFIX;   //  Sans nom d'activité car partagé avec MainActivity
        setupKeepScreen();
        setupStringShelfDatabase();
        loadCurrentChronoTimer(getIntent().getIntExtra(CTDISPLAY_EXTRA_KEYS.CURRENT_CHRONO_TIMER_ID.toString(), NOT_FOUND));
        timeColors = getCurrentColors(COLOR_ITEMS.TIME);
        buttonColors = getCurrentColors(COLOR_ITEMS.BUTTONS);
        backScreenColors = getCurrentColors(COLOR_ITEMS.BACK_SCREEN);

        if (isColdStart()) {
            setHotStart();
        } else {
            if (validReturnFromCalledActivity) {
                validReturnFromCalledActivity = false;
                if (returnsFromPresets()) {
                    if (!currentCtRecord.setMessage(getCurrentPresetMessageFromPresets())) {
                        Toast.makeText(this, "Error updating Clock App alarm message", Toast.LENGTH_LONG).show();
                    }
                    if (!currentCtRecord.setTimeDef(Long.parseLong(getCurrentPresetTimeDefFromPresets()))) {
                        Toast.makeText(this, "Error updating Clock App alarm default time", Toast.LENGTH_LONG).show();
                    }
                }
                if (returnsFromColorPicker()) {
                    if (calledActivity.equals(PEKISLIB_ACTIVITIES.COLORPICKER.toString() + COLOR_ITEMS.TIME.toString())) {
                        timeColors = getCurrentColorsFromColorPicker(COLOR_ITEMS.TIME);
                    }
                    if (calledActivity.equals(PEKISLIB_ACTIVITIES.COLORPICKER.toString() + COLOR_ITEMS.BUTTONS.toString())) {
                        buttonColors = getCurrentColorsFromColorPicker(COLOR_ITEMS.BUTTONS);
                    }
                    if (calledActivity.equals(PEKISLIB_ACTIVITIES.COLORPICKER.toString() + COLOR_ITEMS.BACK_SCREEN.toString())) {
                        backScreenColors = getCurrentColorsFromColorPicker(COLOR_ITEMS.BACK_SCREEN);
                    }
                }
                if (returnsFromHelp()) {
                    //  NOP
                }
            }
        }

        currentCtRecord.updateTime(nowm);
        updatetimeDotMatrixDisplayViewText();
        getActionBar().setTitle(currentCtRecord.getMessage());
        updateButtonColors();
        setupTimeColors();
        setupBackScreenColor();
        setupCtDisplayTimeRobot();
        if (currentCtRecord.isRunning()) {
            ctDisplayTimeRobot.startAutomatic(UPDATE_CT_DISPLAY_VIEW_TIME_INTERVAL_MS);
        }
        invalidateOptionsMenu();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent returnIntent) {
        validReturnFromCalledActivity = false;
        if (requestCode == PEKISLIB_ACTIVITIES.PRESETS.ordinal()) {
            calledActivity = PEKISLIB_ACTIVITIES.PRESETS.toString();
            if (resultCode == RESULT_OK) {
                validReturnFromCalledActivity = true;
            }
        }
        if (requestCode == (PEKISLIB_ACTIVITIES.COLORPICKER.ordinal() + 1) * ACTIVITY_CODE_MULTIPLIER + COLOR_ITEMS.TIME.ordinal()) {
            calledActivity = PEKISLIB_ACTIVITIES.COLORPICKER.toString() + COLOR_ITEMS.TIME.toString();
            if (resultCode == RESULT_OK) {
                validReturnFromCalledActivity = true;
            }
        }
        if (requestCode == (PEKISLIB_ACTIVITIES.COLORPICKER.ordinal() + 1) * ACTIVITY_CODE_MULTIPLIER + COLOR_ITEMS.BUTTONS.ordinal()) {
            calledActivity = PEKISLIB_ACTIVITIES.COLORPICKER.toString() + COLOR_ITEMS.BUTTONS.toString();
            if (resultCode == RESULT_OK) {
                validReturnFromCalledActivity = true;
            }
        }
        if (requestCode == (PEKISLIB_ACTIVITIES.COLORPICKER.ordinal() + 1) * ACTIVITY_CODE_MULTIPLIER + COLOR_ITEMS.BACK_SCREEN.ordinal()) {
            calledActivity = PEKISLIB_ACTIVITIES.COLORPICKER.toString() + COLOR_ITEMS.BACK_SCREEN.toString();
            if (resultCode == RESULT_OK) {
                validReturnFromCalledActivity = true;
            }
        }
        if (requestCode == PEKISLIB_ACTIVITIES.HELP.ordinal()) {
            calledActivity = PEKISLIB_ACTIVITIES.HELP.toString();
            validReturnFromCalledActivity = true;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {  //  Non appelé après changement d'orientation
        getMenuInflater().inflate(R.menu.menu_ct_display, menu);
        this.menu = menu;
        setupBarMenuItems();
        setKeepScreenBarMenuItemIcon(keepScreen);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {  // appelé par invalideOptionsMenu après changement d'orientation
        setKeepScreenBarMenuItemIcon(keepScreen);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.help) {
            launchHelpActivity();
            return true;
        }
        if (item.getItemId() == R.id.BAR_MENU_KEEP_SCREEN) {
            keepScreen = !keepScreen;
            setScreen(keepScreen);
            setKeepScreenBarMenuItemIcon(keepScreen);
        }
        if (item.getItemId() == R.id.set_time_colors) {
            setCurrentColorsForColorPicker(COLOR_ITEMS.TIME, timeColors);
            setDefaultColorsForPresets(COLOR_ITEMS.TIME, getDefaultColors(COLOR_ITEMS.TIME));
            launchColorPickerActivity(COLOR_ITEMS.TIME);
            return true;
        }
        if (item.getItemId() == R.id.set_button_colors) {
            setCurrentColorsForColorPicker(COLOR_ITEMS.BUTTONS, buttonColors);
            setDefaultColorsForPresets(COLOR_ITEMS.BUTTONS, getDefaultColors(COLOR_ITEMS.BUTTONS));
            launchColorPickerActivity(COLOR_ITEMS.BUTTONS);
            return true;
        }
        if (item.getItemId() == R.id.set_back_screen_colors) {
            setCurrentColorsForColorPicker(COLOR_ITEMS.BACK_SCREEN, backScreenColors);
            setDefaultColorsForPresets(COLOR_ITEMS.BACK_SCREEN, getDefaultColors(COLOR_ITEMS.BACK_SCREEN));
            launchColorPickerActivity(COLOR_ITEMS.BACK_SCREEN);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    //endregion

    private void onButtonClick(COMMANDS command) {
        long nowm = System.currentTimeMillis();
        if (command.equals(COMMANDS.RUN)) {
            onButtonClickRun(nowm);
        }
        if (command.equals(COMMANDS.SPLIT)) {
            onButtonClickSplit(nowm);
        }
        if (command.equals(COMMANDS.INVERT_CLOCK_APP_ALARM)) {
            onButtonClickInvertClockAppAlarm();
        }
        if (command.equals(COMMANDS.RESET)) {
            onButtonClickReset();
        }
        if (command.equals(COMMANDS.CHRONO_MODE)) {
            onButtonClickMode(MODE.CHRONO);
        }
        if (command.equals(COMMANDS.TIMER_MODE)) {
            onButtonClickMode(MODE.TIMER);
        }
        currentCtRecord.updateTime(nowm);
        updateButtonColors();
        updatetimeDotMatrixDisplayViewText();
    }

    private void onButtonClickRun(long nowm) {
        final long DELAY_ZERO_MS = 0;

        if (!currentCtRecord.isRunning()) {
            currentCtRecord.start(nowm);
            ctDisplayTimeRobot.startAutomatic(DELAY_ZERO_MS);
        } else {
            ctDisplayTimeRobot.stopAutomatic();
            if (!currentCtRecord.stop(nowm)) {
                currentCtRecord.setClockAppAlarmOff(USE_CLOCK_APP);
            }
        }
    }

    private void onButtonClickSplit(long nowm) {
        currentCtRecord.split(nowm);
    }

    private void onButtonClickInvertClockAppAlarm() {
        if (currentCtRecord.getMode().equals(MODE.TIMER)) {
            if (currentCtRecord.isRunning()) {
                if (!currentCtRecord.hasClockAppAlarm()) {
                    currentCtRecord.setClockAppAlarmOn(USE_CLOCK_APP);
                } else {
                    currentCtRecord.setClockAppAlarmOff(USE_CLOCK_APP);
                }
            }
        }
    }

    private void onButtonClickReset() {
        ctDisplayTimeRobot.stopAutomatic();
        if (!currentCtRecord.reset()) {
            currentCtRecord.setClockAppAlarmOff(USE_CLOCK_APP);
        }
    }

    private void onButtonClickMode(MODE newMode) {
        MODE oldMode = currentCtRecord.getMode();
        if (!currentCtRecord.setMode(newMode)) {
            if (!newMode.equals(oldMode)) {
                Toast.makeText(this, "First stop " + caseFirstUpOtherLow(oldMode.toString()), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void onExpiredTimerCurrentChronoTimer() {
        Toast.makeText(this, currentCtRecord.getTimeZoneExpirationMessage(), Toast.LENGTH_LONG).show();
        updatetimeDotMatrixDisplayViewText();
        updateButtonColors();
    }

    private void onTimeDotMatrixDisplayViewClick() {
        setCurrentTimeDefForPresets(currentCtRecord.getTimeDef());
        setDefaultTimeForPresets(currentCtRecord.getTimeDefInit());
        setCurrentMessageForPresets(currentCtRecord.getMessage());
        setDefaultMessageForPresets(currentCtRecord.getMessageInit());
        launchPresetsActivity();
    }

    private void updatetimeDotMatrixDisplayViewText() {
        timeDotMatrixDisplayView.fillGridOff();
        timeDotMatrixDisplayView.drawText(0, 0, convertMsToHms(currentCtRecord.getTimeDisplay(), TIMEUNITS.CS));
        timeDotMatrixDisplayView.invalidate();
    }

    private void updateButtonColor(COMMANDS command) {  //   ON/BACK ou OFF/BACK
        int frontColorIndex;
        int alternateColorIndex;

        if (getButtonState(command)) {
            frontColorIndex = TABLE_COLORS_TIMEBUTTONS_DATA_FIELDS.ON.INDEX();
            alternateColorIndex = TABLE_COLORS_TIMEBUTTONS_DATA_FIELDS.OFF.INDEX();
        } else {
            frontColorIndex = TABLE_COLORS_TIMEBUTTONS_DATA_FIELDS.OFF.INDEX();
            alternateColorIndex = TABLE_COLORS_TIMEBUTTONS_DATA_FIELDS.ON.INDEX();
        }
        int index = command.ordinal();
        buttons[index].setFrontColorIndex(frontColorIndex);
        buttons[index].setBackColorIndex(TABLE_COLORS_TIMEBUTTONS_DATA_FIELDS.BACK.INDEX());
        buttons[index].setAlternateColorIndex(alternateColorIndex);
        buttons[index].invalidate();
    }

    private void updateButtonColors() {
        for (COMMANDS command : COMMANDS.values()) {
            int index = command.ordinal();
            buttons[index].setColors(buttonColors);
            updateButtonColor(command);
        }
    }

    private boolean getButtonState(COMMANDS command) {
        if (command.equals(COMMANDS.CHRONO_MODE)) {
            return currentCtRecord.getMode().equals(MODE.CHRONO);
        }
        if (command.equals(COMMANDS.TIMER_MODE)) {
            return currentCtRecord.getMode().equals(MODE.TIMER);
        }
        if (command.equals(COMMANDS.RUN)) {
            return currentCtRecord.isRunning();
        }
        if (command.equals(COMMANDS.SPLIT)) {
            return currentCtRecord.isSplitted();
        }
        if (command.equals(COMMANDS.RESET)) {
            return false;
        }
        if (command.equals(COMMANDS.INVERT_CLOCK_APP_ALARM)) {
            return currentCtRecord.hasClockAppAlarm();
        }
        return false;
    }

    private void setScreen(boolean keepScreen) {
        if (keepScreen) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void setKeepScreenBarMenuItemIcon(boolean keepScreen) {
        int id;

        if (keepScreen) {
            id = R.drawable.main_light_on;
        } else {
            id = R.drawable.main_light_off;
        }
        barMenuItems[BAR_MENU_ITEMS.KEEP_SCREEN.ordinal()].setIcon(id);
    }

    private boolean getSHPKeepScreen() {
        final boolean KEEP_SCREEN_DEFAULT_VALUE = false;

        SharedPreferences shp = getSharedPreferences(shpFileName, MODE_PRIVATE);
        return shp.getBoolean(SWTIMER_SHP_KEY_NAMES.KEEP_SCREEN.toString(), KEEP_SCREEN_DEFAULT_VALUE);
    }

    private void savePreferences() {
        SharedPreferences shp = getSharedPreferences(shpFileName, MODE_PRIVATE);
        SharedPreferences.Editor shpEditor = shp.edit();
        shpEditor.putBoolean(SWTIMER_SHP_KEY_NAMES.KEEP_SCREEN.toString(), keepScreen);
        shpEditor.commit();
    }

    private void setupKeepScreen() {
        keepScreen = getSHPKeepScreen();
        setScreen(keepScreen);
    }

    private void setupOrientationLayout() {
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            setContentView(R.layout.ctdisplay_p);
        } else {
            setContentView(R.layout.ctdisplay_l);
        }
    }

    private void setupTimeColors() {
        timeDotMatrixDisplayView.setColors(timeColors);
        timeDotMatrixDisplayView.setFrontColorIndex(TABLE_COLORS_TIMEBUTTONS_DATA_FIELDS.ON.INDEX());
        timeDotMatrixDisplayView.setBackColorIndex(TABLE_COLORS_TIMEBUTTONS_DATA_FIELDS.OFF.INDEX());
        timeDotMatrixDisplayView.setAlternateColorIndex(TABLE_COLORS_TIMEBUTTONS_DATA_FIELDS.BACK.INDEX());
        timeDotMatrixDisplayView.invalidate();
    }

    private void setupBackScreenColor() {
        backLayout.setBackgroundColor(Color.parseColor(COLOR_PREFIX + backScreenColors[Constants.TABLE_COLORS_BACK_SCREEN_DATA_FIELDS.BACK.INDEX()]));
    }

    private void setupBarMenuItems() {
        final String MENU_ITEM_XML_PREFIX = "BAR_MENU_";

        barMenuItems = new MenuItem[BAR_MENU_ITEMS.values().length];
        Class rid = R.id.class;
        for (BAR_MENU_ITEMS barMenuItem : BAR_MENU_ITEMS.values())
            try {
                int index = barMenuItem.ordinal();
                barMenuItems[index] = menu.findItem(rid.getField(MENU_ITEM_XML_PREFIX + barMenuItem.toString()).getInt(rid));
            } catch (IllegalAccessException ex) {
                Logger.getLogger(MainActivity.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IllegalArgumentException ex) {
                Logger.getLogger(MainActivity.class.getName()).log(Level.SEVERE, null, ex);
            } catch (NoSuchFieldException ex) {
                Logger.getLogger(MainActivity.class.getName()).log(Level.SEVERE, null, ex);
            } catch (SecurityException ex) {
                Logger.getLogger(MainActivity.class.getName()).log(Level.SEVERE, null, ex);
            }
    }

    private void setupButtons() {
        final String BUTTON_XML_NAME_PREFIX = "BTN_";

        buttons = new SymbolButtonView[COMMANDS.values().length];
        Class rid = R.id.class;
        for (COMMANDS command : COMMANDS.values()) {
            try {
                int index = command.ordinal();
                buttons[index] = (SymbolButtonView) findViewById(rid.getField(BUTTON_XML_NAME_PREFIX + command.toString()).getInt(rid));
                buttons[index].setSVGImageResource(command.ID());
                final COMMANDS fcommand = command;
                buttons[index].setCustomOnClickListener(new SymbolButtonView.onCustomClickListener() {
                    @Override
                    public void onCustomClick() {
                        onButtonClick(fcommand);
                    }
                });
            } catch (IllegalAccessException ex) {
                Logger.getLogger(MainActivity.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IllegalArgumentException ex) {
                Logger.getLogger(MainActivity.class.getName()).log(Level.SEVERE, null, ex);
            } catch (NoSuchFieldException ex) {
                Logger.getLogger(MainActivity.class.getName()).log(Level.SEVERE, null, ex);
            } catch (SecurityException ex) {
                Logger.getLogger(MainActivity.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private void loadCurrentChronoTimer(int idct) {
        currentCtRecord = new CtRecord(this).loadFromChronoTimerRow(getChronoTimer(idct));
    }

    private void saveCurrentChronoTimer() {
        saveChronoTimer(currentCtRecord.convertToChronoTimerRow());
    }

    private void setupTimeDotMatrixDisplayView() {
        //  Pour Afficher HH:MM:SS:CC
        final int SYMBOL_RIGHT_MARGIN = 1;       //  1 colonne vide à droite de chaque symbole
        final int FULL_SYMBOLS_COUNT = 7;        //  HH, MM, SS et CC nécessitent la largeur normale de symbole + marge droite, sauf pour le dernier C
        final int DOT_SYMBOL_COUNT = 1;          //  '.' est présent 1 fois
        final int DOUBLE_DOT_SYMBOL_COUNT = 2;   //  ':' est présent 2 fois
        final int SHORT_DOT_WIDTH = 0;           //  '.' est à afficher sur le symbole précédent en bas à droite sur une ligne supplémentaire
        final int SHORT_DOUBLE_DOT_WIDTH = 1 + SYMBOL_RIGHT_MARGIN;    //  ':' est à afficher sur une seule colonne + marge droite

        timeDotMatrixDisplayView = (DotMatrixDisplayView) findViewById(R.id.DISPLAY_TIME);
        int symbolWidth = timeDotMatrixDisplayView.getSymbolWidth();
        int gridWidth = FULL_SYMBOLS_COUNT * (symbolWidth + SYMBOL_RIGHT_MARGIN) + symbolWidth + DOUBLE_DOT_SYMBOL_COUNT * SHORT_DOUBLE_DOT_WIDTH + DOT_SYMBOL_COUNT * SHORT_DOT_WIDTH;
        int gridHeight = timeDotMatrixDisplayView.getSymbolHeight() + 1;   // 1 ligne supplémentaire pour le point décimal
        timeDotMatrixDisplayView.setGridDimensions(gridWidth, gridHeight);
        timeDotMatrixDisplayView.setSymbolRightMargin(SYMBOL_RIGHT_MARGIN);
        timeDotMatrixDisplayView.setAllSymbolCompressionsOn();   //  Afficher '.' et ':'  sur un nombre réduit de colonnes
        timeDotMatrixDisplayView.setOnCustomClickListener(new DotMatrixDisplayView.onCustomClickListener() {
            @Override
            public void onCustomClick() {
                onTimeDotMatrixDisplayViewClick();
            }
        });
    }

    private void setupCtDisplayTimeRobot() {
        ctDisplayTimeRobot = new CtDisplayTimeRobot(timeDotMatrixDisplayView, currentCtRecord);
        ctDisplayTimeRobot.setUpdateInterval(UPDATE_CT_DISPLAY_VIEW_TIME_INTERVAL_MS);
        ctDisplayTimeRobot.setOnExpiredTimerListener(new CtDisplayTimeRobot.onExpiredTimerListener() {
            @Override
            public void onExpiredTimer() {
                onExpiredTimerCurrentChronoTimer();
            }
        });
    }

    private void setupStringShelfDatabase() {
        stringShelfDatabase = new StringShelfDatabase(this);
        stringShelfDatabase.open();
    }

    private void launchHelpActivity() {
        Intent callingIntent = new Intent(this, HelpActivity.class);
        callingIntent.putExtra(ACTIVITY_EXTRA_KEYS.TITLE.toString(), HELP_ACTIVITY_TITLE);
        callingIntent.putExtra(HELP_ACTIVITY_EXTRA_KEYS.HTML_ID.toString(), R.raw.helpctdisplayactivity);
        startActivityForResult(callingIntent, PEKISLIB_ACTIVITIES.HELP.ordinal());
    }

    private void launchColorPickerActivity(COLOR_ITEMS colorItem) {
        setColdStartForColorPicker();
        Intent callingIntent = new Intent(this, ColorPickerActivity.class);
        callingIntent.putExtra(ACTIVITY_EXTRA_KEYS.TITLE.toString(), caseFirstUpOtherLow(colorItem.toString()) + " Colors (RRGGBB)");
        callingIntent.putExtra(TABLE_EXTRA_KEYS.TABLE.toString(), colorItem.TABLE_NAME());
        startActivityForResult(callingIntent, (PEKISLIB_ACTIVITIES.COLORPICKER.ordinal() + 1) * ACTIVITY_CODE_MULTIPLIER + colorItem.ordinal());  //  Il faut différencier les 3 types de couleur
    }

    private void launchPresetsActivity() {
        final String SEPARATOR = " - ";

        setColdStartForPresets();
        Intent callingIntent = new Intent(this, PresetsActivity.class);
        callingIntent.putExtra(ACTIVITY_EXTRA_KEYS.TITLE.toString(), "Time / Message Presets");
        callingIntent.putExtra(PRESET_ACTIVITY_EXTRA_KEYS.SEPARATOR.toString(), SEPARATOR);
        callingIntent.putExtra(PRESET_ACTIVITY_EXTRA_KEYS.DATA_TYPE.toString(), PRESET_ACTIVITY_DATA_TYPES.NORMAL.toString());
        callingIntent.putExtra(TABLE_EXTRA_KEYS.TABLE.toString(), SWTIMER_TABLES.PRESETS_CT.toString());
        startActivityForResult(callingIntent, PEKISLIB_ACTIVITIES.PRESETS.ordinal());
    }

    private boolean isColdStart() {
        return (stringShelfDatabase.selectFieldByIdOrCreate(PEKISLIB_TABLES.ACTIVITY_INFOS.toString(), SWTIMER_ACTIVITIES.CTDISPLAY.toString(), TABLE_ACTIVITY_INFOS_DATA_FIELDS.START_TYPE.INDEX()).equals(ACTIVITY_START_TYPE.COLD.toString()));
    }

    private void setHotStart() {
        stringShelfDatabase.insertOrReplaceFieldById(PEKISLIB_TABLES.ACTIVITY_INFOS.toString(), SWTIMER_ACTIVITIES.CTDISPLAY.toString(), TABLE_ACTIVITY_INFOS_DATA_FIELDS.START_TYPE.INDEX(), ACTIVITY_START_TYPE.HOT.toString());
    }

    private void setColdStartForColorPicker() {
        stringShelfDatabase.insertOrReplaceFieldById(PEKISLIB_TABLES.ACTIVITY_INFOS.toString(), PEKISLIB_ACTIVITIES.COLORPICKER.toString(), TABLE_ACTIVITY_INFOS_DATA_FIELDS.START_TYPE.INDEX(), ACTIVITY_START_TYPE.COLD.toString());
    }

    private void setColdStartForPresets() {
        stringShelfDatabase.insertOrReplaceFieldById(PEKISLIB_TABLES.ACTIVITY_INFOS.toString(), PEKISLIB_ACTIVITIES.PRESETS.toString(), TABLE_ACTIVITY_INFOS_DATA_FIELDS.START_TYPE.INDEX(), ACTIVITY_START_TYPE.COLD.toString());
    }

    private String getCurrentPresetMessageFromPresets() {
        return stringShelfDatabase.selectFieldByIdOrCreate(SWTIMER_TABLES.PRESETS_CT.toString(), TABLE_IDS.CURRENT.toString() + PEKISLIB_ACTIVITIES.PRESETS.toString(), TABLE_PRESETS_CT_DATA_FIELDS.MESSAGE.INDEX());
    }

    private String getCurrentPresetTimeDefFromPresets() {
        return stringShelfDatabase.selectFieldByIdOrCreate(SWTIMER_TABLES.PRESETS_CT.toString(), TABLE_IDS.CURRENT.toString() + PEKISLIB_ACTIVITIES.PRESETS.toString(), TABLE_PRESETS_CT_DATA_FIELDS.TIME.INDEX());
    }

    private String[] getCurrentColorsFromColorPicker(COLOR_ITEMS colorItem) {
        return stringShelfDatabase.selectRowByIdOrCreate(colorItem.TABLE_NAME(), TABLE_IDS.CURRENT.toString() + PEKISLIB_ACTIVITIES.COLORPICKER.toString());
    }

    private String[] getCurrentColors(COLOR_ITEMS colorItem) {
        return stringShelfDatabase.selectRowByIdOrCreate(colorItem.TABLE_NAME(), TABLE_IDS.CURRENT.toString() + colorItem.toString() + SWTIMER_ACTIVITIES.CTDISPLAY.toString());
    }

    private String[] getDefaultColors(COLOR_ITEMS colorItem) {
        return stringShelfDatabase.selectRowByIdOrCreate(colorItem.TABLE_NAME(), TABLE_IDS.DEFAULT.toString() + colorItem.toString());
    }

    private void saveCurrentColors(COLOR_ITEMS colorItem, String[] values) {
        stringShelfDatabase.insertOrReplaceRowById(colorItem.TABLE_NAME(), TABLE_IDS.CURRENT.toString() + colorItem.toString() + SWTIMER_ACTIVITIES.CTDISPLAY.toString(), values);
    }

    private void setCurrentTimeDefForPresets(long value) {
        stringShelfDatabase.insertOrReplaceFieldById(SWTIMER_TABLES.PRESETS_CT.toString(), TABLE_IDS.CURRENT.toString() + PEKISLIB_ACTIVITIES.PRESETS.toString(), TABLE_PRESETS_CT_DATA_FIELDS.TIME.INDEX(), String.valueOf(value));
    }

    private void setCurrentMessageForPresets(String value) {
        stringShelfDatabase.insertOrReplaceFieldById(SWTIMER_TABLES.PRESETS_CT.toString(), TABLE_IDS.CURRENT.toString() + PEKISLIB_ACTIVITIES.PRESETS.toString(), TABLE_PRESETS_CT_DATA_FIELDS.MESSAGE.INDEX(), value);
    }

    private void setDefaultTimeForPresets(long value) {
        stringShelfDatabase.insertOrReplaceFieldById(SWTIMER_TABLES.PRESETS_CT.toString(), TABLE_IDS.DEFAULT.toString(), TABLE_PRESETS_CT_DATA_FIELDS.TIME.INDEX(), String.valueOf(value));
    }

    private void setDefaultMessageForPresets(String value) {
        stringShelfDatabase.insertOrReplaceFieldById(SWTIMER_TABLES.PRESETS_CT.toString(), TABLE_IDS.DEFAULT.toString(), TABLE_PRESETS_CT_DATA_FIELDS.MESSAGE.INDEX(), value);
    }

    private void setCurrentColorsForColorPicker(COLOR_ITEMS colorItem, String[] values) {
        stringShelfDatabase.insertOrReplaceRowById(colorItem.TABLE_NAME(), TABLE_IDS.CURRENT.toString() + PEKISLIB_ACTIVITIES.COLORPICKER.toString(), values);
    }

    private void setDefaultColorsForPresets(COLOR_ITEMS colorItem, String[] values) {
        stringShelfDatabase.insertOrReplaceRowById(colorItem.TABLE_NAME(), TABLE_IDS.DEFAULT.toString(), values);
    }

    private String[] getChronoTimer(int idct) {
        return stringShelfDatabase.selectRowByIdOrCreate(SWTIMER_TABLES.CHRONO_TIMERS.toString(), String.valueOf(idct));
    }

    private void saveChronoTimer(String[] values) {
        stringShelfDatabase.insertOrReplaceRow(SWTIMER_TABLES.CHRONO_TIMERS.toString(), values);
    }

    private boolean returnsFromPresets() {
        return (calledActivity.equals(PEKISLIB_ACTIVITIES.PRESETS.toString()));
    }

    private boolean returnsFromColorPicker() {
        String s = PEKISLIB_ACTIVITIES.COLORPICKER.toString();
        if (calledActivity.length() >= s.length()) {
            return (calledActivity.substring(0, s.length()).equals(s));
        } else return false;
    }

    private boolean returnsFromHelp() {
        return (calledActivity.equals(PEKISLIB_ACTIVITIES.HELP.toString()));
    }

}