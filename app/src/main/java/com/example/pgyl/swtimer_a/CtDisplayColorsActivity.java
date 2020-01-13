package com.example.pgyl.swtimer_a;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;

import com.example.pgyl.pekislib_a.DotMatrixDisplayView;
import com.example.pgyl.pekislib_a.HelpActivity;
import com.example.pgyl.pekislib_a.InputButtonsActivity;
import com.example.pgyl.pekislib_a.MainActivity;
import com.example.pgyl.pekislib_a.PresetsActivity;
import com.example.pgyl.pekislib_a.StringShelfDatabase;
import com.example.pgyl.pekislib_a.SymbolButtonView;

import java.util.logging.Level;
import java.util.logging.Logger;

import static com.example.pgyl.pekislib_a.ColorUtils.HSVToRGB;
import static com.example.pgyl.pekislib_a.Constants.ACTIVITY_EXTRA_KEYS;
import static com.example.pgyl.pekislib_a.Constants.COLOR_PREFIX;
import static com.example.pgyl.pekislib_a.Constants.COLOR_RGB_MASK;
import static com.example.pgyl.pekislib_a.Constants.HEX_RADIX;
import static com.example.pgyl.pekislib_a.Constants.NOT_FOUND;
import static com.example.pgyl.pekislib_a.Constants.PEKISLIB_ACTIVITIES;
import static com.example.pgyl.pekislib_a.Constants.SHP_FILE_NAME_SUFFIX;
import static com.example.pgyl.pekislib_a.HelpActivity.HELP_ACTIVITY_EXTRA_KEYS;
import static com.example.pgyl.pekislib_a.HelpActivity.HELP_ACTIVITY_TITLE;
import static com.example.pgyl.pekislib_a.PresetsActivity.PRESETS_ACTIVITY_DISPLAY_TYPE;
import static com.example.pgyl.pekislib_a.PresetsActivity.PRESETS_ACTIVITY_EXTRA_KEYS;
import static com.example.pgyl.pekislib_a.StringShelfDatabaseTables.ACTIVITY_START_STATUS;
import static com.example.pgyl.pekislib_a.StringShelfDatabaseTables.TABLE_EXTRA_KEYS;
import static com.example.pgyl.pekislib_a.StringShelfDatabaseUtils.getCurrentPresetInPresetsActivity;
import static com.example.pgyl.pekislib_a.StringShelfDatabaseUtils.getCurrentValueInInputButtonsActivity;
import static com.example.pgyl.pekislib_a.StringShelfDatabaseUtils.getLabels;
import static com.example.pgyl.pekislib_a.StringShelfDatabaseUtils.setCurrentPresetInPresetsActivity;
import static com.example.pgyl.pekislib_a.StringShelfDatabaseUtils.setCurrentValueInInputButtonsActivity;
import static com.example.pgyl.pekislib_a.StringShelfDatabaseUtils.setStartStatusInInputButtonsActivity;
import static com.example.pgyl.pekislib_a.StringShelfDatabaseUtils.setStartStatusInPresetsActivity;
import static com.example.pgyl.swtimer_a.StringShelfDatabaseTables.chronoTimerRowToCtRecord;
import static com.example.pgyl.swtimer_a.StringShelfDatabaseTables.getBackScreenColorBackIndex;
import static com.example.pgyl.swtimer_a.StringShelfDatabaseTables.getButtonsColorBackIndex;
import static com.example.pgyl.swtimer_a.StringShelfDatabaseTables.getButtonsColorOffIndex;
import static com.example.pgyl.swtimer_a.StringShelfDatabaseTables.getButtonsColorOnIndex;
import static com.example.pgyl.swtimer_a.StringShelfDatabaseTables.getColorTableName;
import static com.example.pgyl.swtimer_a.StringShelfDatabaseTables.getColorTableIndex;
import static com.example.pgyl.swtimer_a.StringShelfDatabaseTables.getColorTableLabel;
import static com.example.pgyl.swtimer_a.StringShelfDatabaseTables.getColorTablesCount;
import static com.example.pgyl.swtimer_a.StringShelfDatabaseTables.getColorsBackScreenTableName;
import static com.example.pgyl.swtimer_a.StringShelfDatabaseTables.getColorsButtonsTableName;
import static com.example.pgyl.swtimer_a.StringShelfDatabaseTables.getColorsDotMatrixDisplayTableName;
import static com.example.pgyl.swtimer_a.StringShelfDatabaseUtils.getChronoTimerById;
import static com.example.pgyl.swtimer_a.StringShelfDatabaseUtils.getCurrentValuesInCtDisplayColorsActivity;
import static com.example.pgyl.swtimer_a.StringShelfDatabaseUtils.isColdStartStatusInCtDisplayColorsActivity;
import static com.example.pgyl.swtimer_a.StringShelfDatabaseUtils.setCurrentValuesInCtDisplayColorsActivity;
import static com.example.pgyl.swtimer_a.StringShelfDatabaseUtils.setStartStatusInCtDisplayColorsActivity;

public class CtDisplayColorsActivity extends Activity {
    //region Constantes
    private enum COMMANDS {
        NEXT_COLOR_TABLE(""), NEXT_COLOR(""), NEXT_COLOR_SPACE(""), CANCEL("Cancel"), COLOR_VALUE(""), PRESETS("Presets"), OK("OK");

        private String valueText;

        COMMANDS(String valueText) {
            this.valueText = valueText;
        }

        public String TEXT() {
            return valueText;
        }

        public int INDEX() {
            return ordinal();
        }
    }

    private enum ICON_COMMANDS {
        RUN1(R.raw.ct_run), RUN2(R.raw.ct_run);

        private int valueId;

        ICON_COMMANDS(int valueId) {
            this.valueId = valueId;
        }

        public int ID() {
            return valueId;
        }

        public int INDEX() {
            return ordinal();
        }
    }

    private enum SEEKBARS {
        RED_HUE(Color.RED), GREEN_SAT(Color.GREEN), BLUE_VAL(Color.BLUE);

        private final String HSV_SEEKBAR_COLOR = "C0C0C0";
        private int rgbColorValue;
        private int hsvColorValue;

        SEEKBARS(int rgbColorValue) {
            this.rgbColorValue = rgbColorValue;
            hsvColorValue = Color.parseColor(COLOR_PREFIX + HSV_SEEKBAR_COLOR);
        }

        public int RGB_COLOR_VALUE() {
            return rgbColorValue;
        }

        public int HSV_COLOR_VALUE() {
            return hsvColorValue;
        }


        public int INDEX() {
            return ordinal();
        }
    }

    private enum COLOR_SPACES {RGB, HSV}

    private enum SHP_KEY_NAMES {COLOR_TABLE_INDEX, COLOR_INDEX, COLOR_SPACE}

    private final int COLOR_TABLE_INDEX_DEFAULT_VALUE = 0;
    private final int COLOR_INDEX_DEFAULT_VALUE = 1;  //  les records de couleur dans les tables de la DB stockent leur identifiant comme 1er élément (offset 0) => les couleurs commencent à l'offset 1
    private final COLOR_SPACES COLOR_SPACE_DEFAULT_VALUE = COLOR_SPACES.RGB;
    //endregion
    //region Variables
    private DotMatrixDisplayView dotMatrixDisplayView;
    private CtDisplayDotMatrixDisplayUpdater dotMatrixDisplayUpdater;
    private SymbolButtonView[] iconButtons;
    private Button[] buttons;
    private SeekBar[] seekBars;
    private Drawable[] processDrawables;
    private LinearLayout backLayoutPart1;
    private LinearLayout backLayoutPart2;
    private CtRecord currentCtRecord;
    private int colorTableIndex;
    private int colorIndex;   //  Au sein de la table pointée par colorTableIndex
    private String[][] colors = new String[getColorTablesCount()][];  //  Couleurs de DotMatrixDisplay, Boutons, Backscreen
    private String[][] colorTableFieldsLabels = new String[getColorTablesCount()][];
    private String[] colorTableLabels = new String[getColorTablesCount()];
    private COLOR_SPACES colorSpace;
    private float[] hsvStruc;
    private boolean validReturnFromCalledActivity;
    private String calledActivity;
    private StringShelfDatabase stringShelfDatabase;
    private String shpFileName;
    //endregion

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().requestFeature(Window.FEATURE_ACTION_BAR);
        getActionBar().setTitle("Set Colors");
        setContentView(R.layout.ctdisplaycolors);   //  Mode portrait uniquement (cf Manifest)
        setupDotMatrixDisplay();
        setupBackLayout();
        setupIconButtons();
        setupButtons();
        setupSeekBars();
        validReturnFromCalledActivity = false;
    }

    @Override
    protected void onPause() {
        super.onPause();

        dotMatrixDisplayUpdater.close();
        dotMatrixDisplayUpdater = null;
        currentCtRecord = null;
        saveCurrentColorsInDB();
        stringShelfDatabase.close();
        stringShelfDatabase = null;
        savePreferences();
    }

    @Override
    protected void onResume() {
        super.onResume();

        shpFileName = getPackageName() + "." + getClass().getSimpleName() + SHP_FILE_NAME_SUFFIX;
        setupStringShelfDatabase();
        int idct = getIntent().getIntExtra(CtDisplayActivity.CTDISPLAY_EXTRA_KEYS.CURRENT_CHRONO_TIMER_ID.toString(), NOT_FOUND);
        currentCtRecord = chronoTimerRowToCtRecord(getChronoTimerById(stringShelfDatabase, idct), this);
        setupDotMatrixDisplayUpdater();
        getDBCurrentColors();
        getDBCurrentColorLabels();

        if (isColdStartStatusInCtDisplayColorsActivity(stringShelfDatabase)) {
            setStartStatusInCtDisplayColorsActivity(stringShelfDatabase, ACTIVITY_START_STATUS.HOT);
            colorTableIndex = COLOR_TABLE_INDEX_DEFAULT_VALUE;
            colorIndex = COLOR_INDEX_DEFAULT_VALUE;
            colorSpace = COLOR_SPACE_DEFAULT_VALUE;
        } else {
            colorTableIndex = getSHPcolorTableIndex();
            colorIndex = getSHPcolorIndex();
            colorSpace = getSHPcolorSpace();
            if (validReturnFromCalledActivity) {
                validReturnFromCalledActivity = false;
                if (returnsFromInputButtonsActivity()) {
                    String colorText = getCurrentValueInInputButtonsActivity(stringShelfDatabase, getColorTableName(colorTableIndex), colorIndex);
                    if (colorSpace.equals(COLOR_SPACES.HSV)) {
                        colorText = HSVToRGB(colorText);    //  HSV dégradé
                    }
                    colors[colorTableIndex][colorIndex] = colorText;
                }
                if (returnsFromPresetsActivity()) {
                    colors[colorTableIndex] = getCurrentPresetInPresetsActivity(stringShelfDatabase, getColorTableName(colorTableIndex));
                }
            }
        }

        setupHSVColorSpace();
        dotMatrixDisplayUpdater.setGridDimensions();
        updateDisplayDotMatrixDisplayColors();
        updateDisplayIconButtonColors();
        updateDisplayBackScreenColors();
        updateDisplayButtonTextNextColorTable();
        updateDisplayButtonTextNextColor();
        updateDisplayButtonTextColorSpace();
        updateDisplaySeekBarsProgress();
        updateDisplayButtonTextColorValue();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent returnIntent) {
        validReturnFromCalledActivity = false;
        if (requestCode == PEKISLIB_ACTIVITIES.INPUT_BUTTONS.INDEX()) {
            calledActivity = PEKISLIB_ACTIVITIES.INPUT_BUTTONS.toString();
            if (resultCode == RESULT_OK) {
                validReturnFromCalledActivity = true;
            }
        }
        if (requestCode == PEKISLIB_ACTIVITIES.PRESETS.INDEX()) {
            calledActivity = PEKISLIB_ACTIVITIES.PRESETS.toString();
            if (resultCode == RESULT_OK) {
                validReturnFromCalledActivity = true;
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(com.example.pgyl.pekislib_a.R.menu.menu_help_only, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == com.example.pgyl.pekislib_a.R.id.HELP) {
            launchHelpActivity();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void onButtonClick(COMMANDS command) {
        if (command.equals(COMMANDS.NEXT_COLOR_TABLE)) {
            onButtonClickNextColorTable();
        }
        if (command.equals(COMMANDS.NEXT_COLOR)) {
            onButtonClickNextColor();
        }
        if (command.equals(COMMANDS.NEXT_COLOR_SPACE)) {
            onButtonClickNextColorSpace();
        }
        if (command.equals(COMMANDS.CANCEL)) {
            onButtonClickCancel();
        }
        if (command.equals(COMMANDS.COLOR_VALUE)) {
            onButtonClickColorValue();
        }
        if (command.equals(COMMANDS.PRESETS)) {
            onButtonClickPresets();
        }
        if (command.equals(COMMANDS.OK)) {
            onButtonClickOK();
        }
    }

    private void onButtonClickNextColorTable() {
        colorTableIndex = colorTableIndex + 1;
        if (colorTableIndex >= colors.length) {
            colorTableIndex = 0;
        }
        colorIndex = 1;
        updateDisplayButtonTextNextColorTable();
        updateDisplayButtonTextNextColor();
        updateDisplaySeekBarsProgress();
        updateDisplayButtonTextColorValue();
    }

    private void onButtonClickNextColor() {
        colorIndex = colorIndex + 1;
        if (colorIndex >= colors[colorTableIndex].length) {
            colorIndex = 1;
        }
        updateDisplayButtonTextNextColor();
        updateDisplaySeekBarsProgress();
        updateDisplayButtonTextColorValue();
    }

    private void onButtonClickNextColorSpace() {
        colorSpace = ((colorSpace.equals(COLOR_SPACES.RGB)) ? COLOR_SPACES.HSV : COLOR_SPACES.RGB);
        updateDisplayButtonTextColorSpace();
        updateDisplaySeekBarsProgress();
        updateDisplayButtonTextColorValue();
    }

    private void onButtonClickCancel() {
        finish();
    }

    private void onButtonClickColorValue() {
        setCurrentValueInInputButtonsActivity(stringShelfDatabase, getColorTableName(colorTableIndex), colorIndex, getSeekBarsProgressHexString());
        launchInputButtonsActivity();
    }

    private void onButtonClickPresets() {
        setCurrentPresetInPresetsActivity(stringShelfDatabase, getColorTableName(colorTableIndex), colors[colorTableIndex]);
        launchPresetsActivity();
    }

    private void onButtonClickOK() {
        Intent returnIntent = new Intent();
        setResult(RESULT_OK, returnIntent);
        finish();
    }

    private void onSeekBarProgressChanged(boolean fromUser) {
        if (fromUser) {
            if (colorSpace.equals(COLOR_SPACES.RGB)) {
                colors[colorTableIndex][colorIndex] = getSeekBarsProgressHexString();
            } else {
                hsvStruc[0] = (float) seekBars[SEEKBARS.RED_HUE.INDEX()].getProgress() / 65535f * 360f;
                hsvStruc[1] = (float) seekBars[SEEKBARS.GREEN_SAT.INDEX()].getProgress() / 65535f;
                hsvStruc[2] = (float) seekBars[SEEKBARS.BLUE_VAL.INDEX()].getProgress() / 65535f;
                colors[colorTableIndex][colorIndex] = String.format("%06X", Color.HSVToColor(hsvStruc) & COLOR_RGB_MASK);
            }
            updateDisplayButtonTextColorValue();
            updateDisplayColors();
        }
    }

    private void updateDisplayColors() {
        if (colorTableIndex == getColorTableIndex(getColorsDotMatrixDisplayTableName())) {
            updateDisplayDotMatrixDisplayColors();
        }
        if (colorTableIndex == getColorTableIndex(getColorsButtonsTableName())) {
            updateDisplayIconButtonColors();
        }
        if (colorTableIndex == getColorTableIndex(getColorsBackScreenTableName())) {
            updateDisplayBackScreenColors();
        }
    }

    private void updateDisplayDotMatrixDisplayColors() {
        final String EXTRA_FONT_TEST_TEXT = "12:34:.";
        final String DEFAULT_FONT_TEST_TEXT = "Abcd";

        dotMatrixDisplayUpdater.setGridColors(colors[getColorTableIndex(getColorsDotMatrixDisplayTableName())]);
        dotMatrixDisplayUpdater.writeTestText(EXTRA_FONT_TEST_TEXT, DEFAULT_FONT_TEST_TEXT);
        dotMatrixDisplayView.invalidate();
    }

    private void updateDisplayIconButtonColor(ICON_COMMANDS iconCommand) {  //   ON/BACK ou OFF/BACK
        int colorTableIndex = getColorTableIndex(getColorsButtonsTableName());
        iconButtons[iconCommand.INDEX()].setFrontColor(((getButtonState(iconCommand)) ? colors[colorTableIndex][getButtonsColorOnIndex()] : colors[colorTableIndex][getButtonsColorOffIndex()]));
        iconButtons[iconCommand.INDEX()].setBackColor(colors[colorTableIndex][getButtonsColorBackIndex()]);
        iconButtons[iconCommand.INDEX()].setExtraColor(((getButtonState(iconCommand)) ? colors[colorTableIndex][getButtonsColorOffIndex()] : colors[colorTableIndex][getButtonsColorOnIndex()]));
        iconButtons[iconCommand.INDEX()].invalidate();
    }

    private void updateDisplayIconButtonColors() {
        for (ICON_COMMANDS iconCommand : ICON_COMMANDS.values()) {
            updateDisplayIconButtonColor(iconCommand);
        }
    }

    private void updateDisplayBackScreenColors() {
        int color = Color.parseColor(COLOR_PREFIX + colors[getColorTableIndex(getColorsBackScreenTableName())][getBackScreenColorBackIndex()]);
        backLayoutPart2.setBackgroundColor(color);
        backLayoutPart1.setBackgroundColor(color);
    }

    private void updateDisplayButtonTextNextColorTable() {
        final String SYMBOL_NEXT = " >";               //  Pour signifier qu'on peut passer au suivant en poussant sur le bouton

        buttons[COMMANDS.NEXT_COLOR_TABLE.INDEX()].setText(colorTableLabels[colorTableIndex] + SYMBOL_NEXT);
    }

    private void updateDisplayButtonTextNextColor() {
        final String SYMBOL_NEXT = " >";               //  Pour signifier qu'on peut passer au suivant en poussant sur le bouton

        buttons[COMMANDS.NEXT_COLOR.INDEX()].setText(colorTableFieldsLabels[colorTableIndex][colorIndex] + SYMBOL_NEXT);
    }

    private void updateDisplayButtonTextColorValue() {
        buttons[COMMANDS.COLOR_VALUE.INDEX()].setText(getSeekBarsProgressHexString());
    }

    private void updateDisplayButtonTextColorSpace() {
        final String SYMBOL_NEXT = " >";               //  Pour signifier qu'on peut passer au suivant en poussant sur le bouton

        buttons[COMMANDS.NEXT_COLOR_SPACE.INDEX()].setText(colorSpace.toString() + SYMBOL_NEXT);
        for (SEEKBARS seekBar : SEEKBARS.values()) {
            int seekBarColor = ((colorSpace.equals(COLOR_SPACES.RGB)) ? seekBar.RGB_COLOR_VALUE() : seekBar.HSV_COLOR_VALUE());
            processDrawables[seekBar.INDEX()].setColorFilter(seekBarColor, PorterDuff.Mode.SRC_IN);  // Colorier uniquement la 1e partie de la seekbar
        }
    }

    private void updateDisplaySeekBarsProgress() {
        int red = Integer.parseInt(colors[colorTableIndex][colorIndex].substring(0, 2), HEX_RADIX);  //  0..255
        int green = Integer.parseInt(colors[colorTableIndex][colorIndex].substring(2, 4), HEX_RADIX);
        int blue = Integer.parseInt(colors[colorTableIndex][colorIndex].substring(4, 6), HEX_RADIX);
        if (colorSpace.equals(COLOR_SPACES.RGB)) {
            seekBars[SEEKBARS.RED_HUE.INDEX()].setProgress(257 * red);    //  257 = 65535 / 255
            seekBars[SEEKBARS.GREEN_SAT.INDEX()].setProgress(257 * green);
            seekBars[SEEKBARS.BLUE_VAL.INDEX()].setProgress(257 * blue);
        } else {
            Color.RGBToHSV(red, green, blue, hsvStruc);
            seekBars[SEEKBARS.RED_HUE.INDEX()].setProgress((int) (hsvStruc[0] * 65535f / 360f + 0.5f));
            seekBars[SEEKBARS.GREEN_SAT.INDEX()].setProgress((int) (hsvStruc[1] * 65535f + 0.5f));
            seekBars[SEEKBARS.BLUE_VAL.INDEX()].setProgress((int) (hsvStruc[2] * 65535f + 0.5f));
        }
    }

    private String getSeekBarsProgressHexString() {  //  Hex RRGGBB ou HHSSVV
        int redHueSeekBarValue = seekBars[SEEKBARS.RED_HUE.INDEX()].getProgress();   //  0..65535
        int greenSatSeekBarValue = seekBars[SEEKBARS.GREEN_SAT.INDEX()].getProgress();
        int blueValSeekBarValue = seekBars[SEEKBARS.BLUE_VAL.INDEX()].getProgress();
        String hexString = String.format("%02X", (int) ((float) redHueSeekBarValue / 257f + 0.5f)) +    //  257 = 65535 / 255
                String.format("%02X", (int) ((float) greenSatSeekBarValue / 257f + 0.5f)) +
                String.format("%02X", (int) ((float) blueValSeekBarValue / 257f + 0.5f));
        return hexString;
    }

    private boolean getButtonState(ICON_COMMANDS iconCommand) {
        if (iconCommand.equals(ICON_COMMANDS.RUN1)) {
            return true;   //  Toujours ON
        }
        if (iconCommand.equals(ICON_COMMANDS.RUN2)) {
            return false;   //  Toujours OFF
        }
        return false;
    }

    private void savePreferences() {
        SharedPreferences shp = getSharedPreferences(shpFileName, MODE_PRIVATE);
        SharedPreferences.Editor shpEditor = shp.edit();
        shpEditor.putInt(SHP_KEY_NAMES.COLOR_TABLE_INDEX.toString(), colorTableIndex);
        shpEditor.putInt(SHP_KEY_NAMES.COLOR_INDEX.toString(), colorIndex);
        shpEditor.putString(SHP_KEY_NAMES.COLOR_SPACE.toString(), colorSpace.toString());
        shpEditor.commit();
    }

    private int getSHPcolorTableIndex() {
        SharedPreferences shp = getSharedPreferences(shpFileName, MODE_PRIVATE);
        return shp.getInt(SHP_KEY_NAMES.COLOR_TABLE_INDEX.toString(), COLOR_TABLE_INDEX_DEFAULT_VALUE);
    }

    private int getSHPcolorIndex() {
        SharedPreferences shp = getSharedPreferences(shpFileName, MODE_PRIVATE);
        return shp.getInt(SHP_KEY_NAMES.COLOR_INDEX.toString(), COLOR_INDEX_DEFAULT_VALUE);
    }

    private COLOR_SPACES getSHPcolorSpace() {
        SharedPreferences shp = getSharedPreferences(shpFileName, MODE_PRIVATE);
        return COLOR_SPACES.valueOf(shp.getString(SHP_KEY_NAMES.COLOR_SPACE.toString(), COLOR_SPACE_DEFAULT_VALUE.toString()));
    }

    private void setupDotMatrixDisplay() {  //  Pour Afficher HH:MM:SS.CC et éventuellement un message
        dotMatrixDisplayView = findViewById(R.id.DOT_MATRIX_DISPLAY);
    }

    private void setupDotMatrixDisplayUpdater() {
        dotMatrixDisplayUpdater = new CtDisplayDotMatrixDisplayUpdater(dotMatrixDisplayView, currentCtRecord);
    }

    private void setupIconButtons() {
        final String ICON_BUTTON_XML_PREFIX = "ICON_BTN_";

        iconButtons = new SymbolButtonView[ICON_COMMANDS.values().length];
        Class rid = R.id.class;
        for (ICON_COMMANDS iconCommand : ICON_COMMANDS.values()) {
            try {
                iconButtons[iconCommand.INDEX()] = findViewById(rid.getField(ICON_BUTTON_XML_PREFIX + iconCommand.toString()).getInt(rid));
                iconButtons[iconCommand.INDEX()].setSVGImageResource(iconCommand.ID());
            } catch (IllegalAccessException ex) {
                Logger.getLogger(com.example.pgyl.swtimer_a.MainActivity.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IllegalArgumentException ex) {
                Logger.getLogger(com.example.pgyl.swtimer_a.MainActivity.class.getName()).log(Level.SEVERE, null, ex);
            } catch (NoSuchFieldException ex) {
                Logger.getLogger(com.example.pgyl.swtimer_a.MainActivity.class.getName()).log(Level.SEVERE, null, ex);
            } catch (SecurityException ex) {
                Logger.getLogger(com.example.pgyl.swtimer_a.MainActivity.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private void setupButtons() {
        final String BUTTON_XML_PREFIX = "BTN_";

        buttons = new Button[COMMANDS.values().length];
        Class rid = R.id.class;
        for (COMMANDS command : COMMANDS.values()) {
            try {
                buttons[command.INDEX()] = findViewById(rid.getField(BUTTON_XML_PREFIX + command.toString()).getInt(rid));   //  BTN_... dans le XML
                buttons[command.INDEX()].setText(command.TEXT());
                final COMMANDS fcommand = command;
                buttons[command.INDEX()].setOnClickListener(new Button.OnClickListener() {
                    @Override
                    public void onClick(View view) {
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

    private void setupHSVColorSpace() {
        hsvStruc = new float[3];
    }

    private void setupSeekBars() {
        final String SEEKBAR_XML_PREFIX = "SEEKB_";

        seekBars = new SeekBar[SEEKBARS.values().length];
        LayerDrawable[] progressDrawables = new LayerDrawable[SEEKBARS.values().length];
        processDrawables = new Drawable[SEEKBARS.values().length];
        Class rid = R.id.class;
        for (SEEKBARS colorParam : SEEKBARS.values()) {
            try {
                seekBars[colorParam.INDEX()] = findViewById(rid.getField(SEEKBAR_XML_PREFIX + colorParam.toString()).getInt(rid));
                seekBars[colorParam.INDEX()].setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        onSeekBarProgressChanged(fromUser);
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                });
                progressDrawables[colorParam.INDEX()] = (LayerDrawable) seekBars[colorParam.INDEX()].getProgressDrawable();
                processDrawables[colorParam.INDEX()] = progressDrawables[colorParam.INDEX()].findDrawableByLayerId(android.R.id.progress);
            } catch (IllegalAccessException ex) {
                Logger.getLogger(InputButtonsActivity.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IllegalArgumentException ex) {
                Logger.getLogger(InputButtonsActivity.class.getName()).log(Level.SEVERE, null, ex);
            } catch (NoSuchFieldException ex) {
                Logger.getLogger(InputButtonsActivity.class.getName()).log(Level.SEVERE, null, ex);
            } catch (SecurityException ex) {
                Logger.getLogger(InputButtonsActivity.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private void setupStringShelfDatabase() {
        stringShelfDatabase = new StringShelfDatabase(this);
        stringShelfDatabase.open();
    }

    private void setupBackLayout() {
        backLayoutPart1 = findViewById(R.id.BACK_LAYOUT_PART1);   //  2 layouts au lieu d'un, pour retrouver la même hauteur pour les iconButtons que dans CtDisplayActivity
        backLayoutPart2 = findViewById(R.id.BACK_LAYOUT_PART2);
    }

    private void saveCurrentColorsInDB() {
        for (int i = 0; i <= (getColorTablesCount() - 1); i = i + 1) {
            setCurrentValuesInCtDisplayColorsActivity(stringShelfDatabase, getColorTableName(i), colors[i]);
        }
    }

    private void getDBCurrentColors() {
        for (int i = 0; i <= (getColorTablesCount() - 1); i = i + 1) {
            colors[i] = getCurrentValuesInCtDisplayColorsActivity(stringShelfDatabase, getColorTableName(i));
        }
    }

    private void getDBCurrentColorLabels() {
        for (int i = 0; i <= (getColorTablesCount() - 1); i = i + 1) {
            colorTableFieldsLabels[i] = getLabels(stringShelfDatabase, getColorTableName(i));
            colorTableLabels[i] = getColorTableLabel(getColorTableName(i));
        }
    }

    private void launchInputButtonsActivity() {
        setStartStatusInInputButtonsActivity(stringShelfDatabase, ACTIVITY_START_STATUS.COLD);
        Intent callingIntent = new Intent(this, InputButtonsActivity.class);
        callingIntent.putExtra(ACTIVITY_EXTRA_KEYS.TITLE.toString(), colorTableFieldsLabels[colorTableIndex][colorIndex]);
        callingIntent.putExtra(TABLE_EXTRA_KEYS.TABLE.toString(), getColorTableName(colorTableIndex));
        callingIntent.putExtra(TABLE_EXTRA_KEYS.INDEX.toString(), colorIndex);
        startActivityForResult(callingIntent, PEKISLIB_ACTIVITIES.INPUT_BUTTONS.INDEX());
    }

    private void launchPresetsActivity() {
        final String SEPARATOR = " - ";

        setStartStatusInPresetsActivity(stringShelfDatabase, ACTIVITY_START_STATUS.COLD);
        Intent callingIntent = new Intent(this, PresetsActivity.class);
        callingIntent.putExtra(ACTIVITY_EXTRA_KEYS.TITLE.toString(), colorTableLabels[colorTableIndex] + "(RGB)");
        callingIntent.putExtra(PRESETS_ACTIVITY_EXTRA_KEYS.SEPARATOR.toString(), SEPARATOR);
        callingIntent.putExtra(PRESETS_ACTIVITY_EXTRA_KEYS.DISPLAY_TYPE.toString(), PRESETS_ACTIVITY_DISPLAY_TYPE.COLORS.toString());
        callingIntent.putExtra(TABLE_EXTRA_KEYS.TABLE.toString(), getColorTableName(colorTableIndex));
        startActivityForResult(callingIntent, PEKISLIB_ACTIVITIES.PRESETS.INDEX());
    }

    private void launchHelpActivity() {
        Intent callingIntent = new Intent(this, HelpActivity.class);
        callingIntent.putExtra(ACTIVITY_EXTRA_KEYS.TITLE.toString(), HELP_ACTIVITY_TITLE);
        callingIntent.putExtra(HELP_ACTIVITY_EXTRA_KEYS.HTML_ID.toString(), R.raw.helpctdisplaycolorsactivity);
        startActivity(callingIntent);
    }

    private boolean returnsFromInputButtonsActivity() {
        return (calledActivity.equals(PEKISLIB_ACTIVITIES.INPUT_BUTTONS.toString()));
    }

    private boolean returnsFromPresetsActivity() {
        return (calledActivity.equals(PEKISLIB_ACTIVITIES.PRESETS.toString()));
    }

}
