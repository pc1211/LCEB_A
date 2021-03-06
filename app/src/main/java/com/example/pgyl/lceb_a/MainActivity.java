package com.example.pgyl.lceb_a;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;

import com.example.pgyl.pekislib_a.Constants;
import com.example.pgyl.pekislib_a.HelpActivity;
import com.example.pgyl.pekislib_a.InputButtonsActivity;
import com.example.pgyl.pekislib_a.StringDB;
import com.example.pgyl.pekislib_a.StringDBTables;
import com.example.pgyl.pekislib_a.TextListView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.example.pgyl.lceb_a.StringDBTables.getTilesTargetTableName;
import static com.example.pgyl.lceb_a.StringDBTables.getTilesTargetValueIndex;
import static com.example.pgyl.lceb_a.StringDBTables.targetIDPrefix;
import static com.example.pgyl.lceb_a.StringDBTables.targetValueRowToTargetValue;
import static com.example.pgyl.lceb_a.StringDBTables.targetValueToTargetValueRow;
import static com.example.pgyl.lceb_a.StringDBTables.tileIDPrefix;
import static com.example.pgyl.lceb_a.StringDBTables.tileValueRowToTileValue;
import static com.example.pgyl.lceb_a.StringDBTables.tileValueToTileValueRow;
import static com.example.pgyl.lceb_a.StringDBUtils.createLCEBTableIfNotExists;
import static com.example.pgyl.lceb_a.StringDBUtils.getDBTargetValueRow;
import static com.example.pgyl.lceb_a.StringDBUtils.getDBTileValueRow;
import static com.example.pgyl.lceb_a.StringDBUtils.getDBTilesInitCount;
import static com.example.pgyl.lceb_a.StringDBUtils.initializeTableTilesTarget;
import static com.example.pgyl.lceb_a.StringDBUtils.saveDBTargetValueRow;
import static com.example.pgyl.lceb_a.StringDBUtils.saveDBTileValueRow;
import static com.example.pgyl.pekislib_a.Constants.PEKISLIB_ACTIVITIES;
import static com.example.pgyl.pekislib_a.HelpActivity.HELP_ACTIVITY_TITLE;
import static com.example.pgyl.pekislib_a.MiscUtils.msgBox;
import static com.example.pgyl.pekislib_a.StringDBTables.getActivityInfosTableName;
import static com.example.pgyl.pekislib_a.StringDBUtils.createPekislibTableIfNotExists;
import static com.example.pgyl.pekislib_a.StringDBUtils.getCurrentFromActivity;
import static com.example.pgyl.pekislib_a.StringDBUtils.setCurrentForActivity;
import static com.example.pgyl.pekislib_a.StringDBUtils.setStartStatusOfActivity;

// A chaque ligne de r??sultat interm??diaire (lines[]), une paire de plaques (tiles[]) disponibles
// est s??lectionn??e et utilis??e pour une op??ration.
// Si le r??sultat de la ligne (qui devient une nouvelle plaque disponible)
// atteint ou r??duit l'??cart minimum actuel par rapport ?? la cible
// et si le nombre de lignes n??cessaires atteint ou r??duit le minimum actuel,
// alors ces lignes sont enregistr??es comme solution (solutionTexts[]) (si cette solution est diff??rente des pr??c??dentes)
// Apr??s une ligne, une nouvelle ligne est essay??e avec la premi??re paire de plaques disponible
// Si le nombre maximum de lignes est atteint, on essaye un autre op??rateur pour les m??mes plaques de la m??me ligne
// S'il n'y a plus d'op??rateur disponible, on essaye une autre paire de plaques pour la m??me ligne
//   (d'abord en changeant de 2e plaque, puis ?? la fois de 1e plaque et de 2e plaque)
// S'il n'y a plus de paire de plaques disponible pour la m??me ligne, on revient ?? la ligne pr??c??dente
// S'il n'y a plus de ligne pr??c??dente, c'est fini !

enum Operators {   //  Op??rateurs entre 2 plaques
    BEGIN("b"), ADD("+"), SUB("-"), MUL("x"), DIV("/"), END("e");

    private final String operatorText;

    Operators(String operatorText) {
        this.operatorText = operatorText;
    }

    public Operators getNext() {
        return (this.ordinal() < (Operators.values().length - 1) ? Operators.values()[this.ordinal() + 1] : null);
    }

    public String TEXT() {
        return operatorText;
    }
}

enum TileSearchType {   //  Type de recherche de plaques disponibles
    FIRST_AVAILABLE, NEXT_AVAILABLE
}

class Tile {   // Plaque
    int value;          // Valeur de la plaque
    boolean used;       // True => La plaque n'est plus disponible
}

class Line {   // Ligne de r??sultat interm??diaire
    int[] opNumTiles = new int[2];   //  Les num??ros de plaques utilis??s pour l'op??ration de la ligne
    Operators operator;
    boolean ordered;         //  True si (tile1 op tile2), False si (tile2 op tile1)
}

class Solution implements Comparable {   // Solution (exacte ou rapproch??e)
    String publishedText;  //  "5 - 3 = 2??2 * 6 = 12??"   Texte de solution pr??t pour la publication (Toutes les lignes et r??sultats, en clair)
    String shortText;      //  "*(2,6)12??-(2,5)3??"       Texte court de solution tri?? par ligne pour v??rifier si est bien diff??rent des pr??c??dents, en commen??ant par la plus petite plaque au sein de chaque ligne
    int result;            //  Meilleur r??sultat obtenu
    int addOpCount;
    int subOpCount;
    int mulOpCount;
    int divOpCount;

    @Override
    public int compareTo(Object o) {   //  Pour comparer 2 solutions entre elles; result ASC, addOpCount ASC, subOpCount ASC, mulOpCount ASC, divOpCount ASC
        Solution solution2 = (Solution) o;
        int res = Integer.compare(result, solution2.result);
        if (res == 0) {
            res = Integer.compare(addOpCount, solution2.addOpCount);
            if (res == 0) {
                res = Integer.compare(subOpCount, solution2.subOpCount);
                if (res == 0) {
                    res = Integer.compare(mulOpCount, solution2.mulOpCount);
                    if (res == 0) {
                        res = Integer.compare(divOpCount, solution2.divOpCount);
                    }
                }
            }
        }
        return res;
    }
}


public class MainActivity extends Activity {
    private Button btnTarget;
    private Button btnFindSolutions;
    private Button[] btnTiles;        //  Boutons de plaque
    private TextListView tlvResults;
    private int tilesInitCount;       //  Nombre de plaques initial
    private Tile[] tiles;             //  Plaques initiales et interm??diaires
    private Line[] lines;             //  Lignes de r??sultat interm??diaire.  A la ligne i correspond la plaque de r??sultat i+tilesInitCount
    private final ArrayList<Solution> solutions = new ArrayList<Solution>();   //  Solutions (exactes ou rapproch??es) valid??es
    private final ArrayList<String> results = new ArrayList<String>();   //  L'ensemble des lignes de r??sultats ?? afficher de toutes les solutions (exactes ou rapproch??es) valid??es
    private String[] shortTexts;       //  Sert au tri par ligne d'une solution propos??e (cf type Solution)

    int numLine;          //  N?? de ligne de r??sultat interm??diaire actuelle
    int targetValue;      //  Cible ?? atteindre
    int diff;             //  Ecart (en valeur absolue) par rapport ?? la cible, ?? ??galer ou r??duire
    int result;           //  R??sultat de l'op??ration en cours
    int minLineCount;     //  Nombre minimum de lignes des solutions actuelles, ?? ??galer ou r??duire
    int opCount;          //  Nombre total d'op??rations
    boolean isExact;      //  True => Il y a au moins une solution exacte
    boolean isEnd;        //  True => C'est la fin des calculs, faute de plaques disponibles
    boolean isNewLine;    //  True => Une nouvelle ligne commence

    private boolean validReturnFromCalledActivity;
    private String calledActivityName;
    private StringDB stringDB;
    private String controlName;
    private Menu menu;
    private final String SEPARATOR = "??";
    private final int OP_NUM_TILE1_INDEX = 0;
    private final int OP_NUM_TILE2_INDEX = 1;
    private final int NOT_AVAILABLE = -1000;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final String ACTIVITY_TITLE = "Le compte est bon!";

        getWindow().requestFeature(Window.FEATURE_ACTION_BAR);
        getActionBar().setTitle(ACTIVITY_TITLE);
        setContentView(R.layout.activity_main);
        setupButtons();
        validReturnFromCalledActivity = false;
    }

    @Override
    protected void onPause() {
        super.onPause();

        updateTileValuesWithTileTexts();
        updateTargetValueWithTargetText();
        saveDBTileAndTargetValues();
        tlvResults.close();
        tlvResults = null;
        stringDB.close();
        stringDB = null;
        btnTiles = null;
        menu = null;
    }

    @Override
    protected void onResume() {
        super.onResume();

        setupStringDB();
        tilesInitCount = getDBTilesInitCount(stringDB);
        inits();
        setupResultsTextListView();
        setupTileButtons();
        getDBTileAndTargetValues();
        updateTileTextsWithTileValues();
        updateTargetTextWithTargetValue();
        if (validReturnFromCalledActivity) {
            validReturnFromCalledActivity = false;
            if (calledActivityName.equals(PEKISLIB_ACTIVITIES.INPUT_BUTTONS.toString())) {
                setControlTextByName(controlName, getCurrentFromActivity(stringDB, PEKISLIB_ACTIVITIES.INPUT_BUTTONS.toString(), getTilesTargetTableName(), getTilesTargetValueIndex()));
            }
        }
        invalidateOptionsMenu();
    }

    private void inits() {
        lines = new Line[tilesInitCount - 1];
        for (int i = 1; i <= lines.length; i = i + 1) {
            lines[i - 1] = new Line();
            for (int j = 1; j <= lines[i - 1].opNumTiles.length; j = j + 1) {
                lines[i - 1].opNumTiles[j - 1] = 0;
            }
        }
        tiles = new Tile[tilesInitCount + lines.length];  //  tilesInitCount plaques initiales + (tilesInitCount-1) plaques de r??sultat interm??diaire
        for (int i = 1; i <= tiles.length; i = i + 1) {   //  Instanciation de chaque ??l??ment
            tiles[i - 1] = new Tile();
        }
        shortTexts = new String[lines.length];  //  Sert au tri par ligne d'une solution propos??e
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {  //  Non appel?? apr??s changement d'orientation
        getMenuInflater().inflate(R.menu.menu_main, menu);
        this.menu = menu;
        //setupBarMenuItems();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.HELP) {
            launchHelpActivity();
            return true;
        }
        if (item.getItemId() == R.id.ABOUT) {
            msgBox("Version: " + BuildConfig.VERSION_NAME, this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent returnIntent) {
        validReturnFromCalledActivity = false;
        if (requestCode == Constants.PEKISLIB_ACTIVITIES.INPUT_BUTTONS.INDEX()) {
            calledActivityName = Constants.PEKISLIB_ACTIVITIES.INPUT_BUTTONS.toString();
            if (resultCode == RESULT_OK) {
                validReturnFromCalledActivity = true;
                controlName = returnIntent.getStringExtra(Constants.ACTIVITY_EXTRA_KEYS.TITLE.toString());   //  TILE1,2,... ou TARGET
            }
        }
    }

    private void onBtnTileClick(String controlName) {
        launchInputButtonsActivity(controlName, getControlTextByName(controlName));
    }

    private void onBtnTargetClick(String controlName) {
        launchInputButtonsActivity(controlName, getControlTextByName(controlName));
    }

    private void onBtnRandomTilesClick() {
        int[] tileValues = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 25, 50, 75, 100};  // Distribution des plaques disponibles au d??part

        invalidateResultsDisplay();
        boolean[] reservedIndexes = new boolean[tileValues.length];
        for (int i = 1; i <= reservedIndexes.length; i = i + 1) {
            reservedIndexes[i - 1] = false;  //  Toutes les 28 plaques sont disponibles au tirage
        }
        int index = 0;
        for (int i = 1; i <= tilesInitCount; i = i + 1) {  // Remplissage des plaques
            do {
                index = (int) (Math.random() * (tileValues.length));   //  0..(values.length-1)
            } while (reservedIndexes[index]);    //  Jusqu'?? trouver une plaque non encore tir??e
            reservedIndexes[index] = true;
            btnTiles[i - 1].setText(String.valueOf(tileValues[index]));
        }
    }

    private void onBtnRandomTargetClick() {
        invalidateResultsDisplay();
        btnTarget.setText(String.valueOf(1 + (int) (Math.random() * (999))));  // Remplissage de la cible ?? trouver   1..999
    }

    private void onBtnFindSolutionsClick() {
        updateTileValuesWithTileTexts();
        updateTargetValueWithTargetText();
        invalidateResultsDisplay();
        solutions.clear();
        initControlVariables();
        while (!isEnd) {
            if (isNewLine) {
                isNewLine = false;
                getTile1And2(TileSearchType.FIRST_AVAILABLE);   //  Prendre les 2 premi??res plaques disponibles
            }
            if (!getNextOperatorAndResult()) {   //  Obtenir si possible le prochain op??rateur et son r??sultat
                if (!getTile2(TileSearchType.NEXT_AVAILABLE)) {           //  Lib??rer la 2e plaque et si possible prendre la suivante
                    if (!getTile1And2(TileSearchType.NEXT_AVAILABLE)) {   //  Lib??rer les 2 plaques et si possible prendre les suivantes
                        if (numLine > 0)
                            numLine = numLine - 1;    //  Faute de plaques pour cette ligne, on revient ?? la ligne pr??c??dente
                        else isEnd = true;
                    }
                }
                continue;   // Reprendre ?? while(!isEnd)
            }
            formNewTileFromResult();
            if (foundSolution())
                addUniqueSolution(buildSolution());   //  Si les solutions pr??c??dentes sont diff??rentes => La proposition de solution est valid??e
            if (numLine < (lines.length - 1)) {     //  Une nouvelle ligne est possible
                numLine = numLine + 1;    //  Nouvelle ligne de r??sultat interm??diaire
                isNewLine = true;
            }
        }
        publishResults();   // Publier toutes les solutions exactes ou (?? d??faut) rapproch??es valid??es
    }

    private void initControlVariables() {
        diff = 99999999;
        minLineCount = 99999999;
        opCount = 0;
        numLine = 0;
        isExact = false;
        isEnd = false;
        isNewLine = true;
    }

    private void formNewTileFromResult() {
        tiles[tilesInitCount + numLine].value = result;     //  Le r??sultat de la ligne est une nouvelle plaque disponible
        tiles[tilesInitCount + numLine].used = false;
        opCount = opCount + 1;
    }

    private boolean getTile2(TileSearchType tileSearchType) {    //  Lib??rer la 2e plaque et prendre la suivante
        int numTile2 = lines[numLine].opNumTiles[OP_NUM_TILE2_INDEX];
        if (tileSearchType.equals(TileSearchType.NEXT_AVAILABLE)) {
            tiles[numTile2].used = false;   //  Lib??rer la 2e plaque
        } else {   //  tileSearchType=FIRST_AVAILABLE
            numTile2 = -1;
        }
        int numNextTile2 = getNextAvailableTileNumber(numTile2);   //  2e plaque suivante
        if (numNextTile2 != NOT_AVAILABLE) {
            tiles[numNextTile2].used = true;   //  R??server la 2e plaque
            lines[numLine].opNumTiles[OP_NUM_TILE2_INDEX] = numNextTile2;
            lines[numLine].operator = Operators.BEGIN;
            return true;
        }
        return false;
    }

    private boolean getTile1And2(TileSearchType tileSearchType) {   //  Lib??rer les 2 plaques (si tileSearchType=NEXT_AVAILABLE) et prendre les suivantes. Pour la ligne numLine, tilesInitCount-numLine+2 plaques sont disponibles sur un total de tilesInitCount+numLine plaques
        int numTile1 = lines[numLine].opNumTiles[OP_NUM_TILE1_INDEX];
        int numTile2 = lines[numLine].opNumTiles[OP_NUM_TILE2_INDEX];
        if (tileSearchType.equals(TileSearchType.NEXT_AVAILABLE)) {
            tiles[numTile1].used = false;    //  Lib??rer les 2 plaques
            tiles[numTile2].used = false;
        } else {   //  tileSearchType=FIRST_AVAILABLE
            numTile1 = -1;
        }
        int numNextTile1 = getNextAvailableTileNumber(numTile1);       //  1e plaque suivante : Si numTile1 = -1 => V??rifier toutes les plaques (?? partir de 0), sinon apr??s la 1e plaque actuelle
        if (numNextTile1 != NOT_AVAILABLE) {
            int numNextTile2 = getNextAvailableTileNumber(numNextTile1);   //  2e plaque, suivant la 1e
            if (numNextTile2 != NOT_AVAILABLE) {
                tiles[numNextTile1].used = true;   //  R??server les 2 plaques
                tiles[numNextTile2].used = true;
                lines[numLine].opNumTiles[OP_NUM_TILE1_INDEX] = numNextTile1;
                lines[numLine].opNumTiles[OP_NUM_TILE2_INDEX] = numNextTile2;
                lines[numLine].operator = Operators.BEGIN;
                return true;
            }
        }
        return false;
    }

    private int getNextAvailableTileNumber(int numTile) {   // Trouver la 1e plaque disponible apr??s numTile
        int numNextTile = NOT_AVAILABLE;   // Retourner -1000 si aucune plaque disponible
        int i = numTile;
        while (i < (tilesInitCount + numLine - 1)) {   // Pour la ligne numLine, les plaques ?? v??rifier vont de minimum 0 ?? maximum tilesInitCount+numLine-1
            i = i + 1;
            if (!tiles[i].used) {
                numNextTile = i;
                break;
            }
        }
        return numNextTile;
    }

    private boolean getNextOperatorAndResult() {
        boolean getNextOperatorAndResult = true;
        int valTile1 = tiles[lines[numLine].opNumTiles[OP_NUM_TILE1_INDEX]].value;
        int valTile2 = tiles[lines[numLine].opNumTiles[OP_NUM_TILE2_INDEX]].value;
        lines[numLine].ordered = true;
        Operators operator = lines[numLine].operator.getNext();  //  2 plaques sont pr??tes, passer ?? l'op??rateur suivant
        switch (operator) {
            case ADD:
                if ((valTile1 == 0) || (valTile2 == 0))
                    operator = Operators.END;   //  Les autres op??rateurs ne seront pas examin??s
                else result = valTile1 + valTile2;
                break;
            case SUB:
                if (valTile1 == valTile2)
                    operator = Operators.MUL;   //  On ne sait rien faire avec un r??sultat 0; Passer ?? MUL => Pas de break
                else {
                    if (valTile1 < valTile2) {
                        lines[numLine].ordered = false;
                        result = valTile2 - valTile1;
                    } else result = valTile1 - valTile2;
                    break;
                }
            case MUL:
                if ((valTile1 == 1) || (valTile2 == 1))
                    operator = Operators.END;   //  La division ne sera pas examin??e
                else result = valTile1 * valTile2;
                break;
            case DIV:
                if (valTile1 < valTile2) {
                    if ((valTile2 % valTile1) != 0)
                        operator = Operators.END;    // La division doit ??tre juste
                    else {
                        lines[numLine].ordered = false;
                        result = valTile2 / valTile1;
                    }
                } else {
                    if ((valTile1 % valTile2) != 0)
                        operator = Operators.END;  // La division doit ??tre juste
                    else result = valTile1 / valTile2;
                }
                break;
        }
        lines[numLine].operator = operator;
        if (operator.equals(Operators.END)) getNextOperatorAndResult = false;
        return getNextOperatorAndResult;
    }

    private boolean foundSolution() {
        boolean foundSolution = false;
        if (Math.abs(result - targetValue) < diff) {     //  R??sultat plus proche de la cible
            diff = Math.abs(result - targetValue);
            if (diff == 0) isExact = true;          //  Solution exacte trouv??e !
            minLineCount = numLine + 1;
            solutions.clear();                      //  Effacer toutes les solutions pr??c??dentes
            foundSolution = true;
        } else {  //  R??sultat identique ou plus ??cart?? de la cible
            if (Math.abs(result - targetValue) == diff) {   //  R??sultat identique
                if ((numLine + 1) < minLineCount) {          //  Nombre de lignes plus petit
                    minLineCount = numLine + 1;
                    solutions.clear();                      //  Effacer toutes les solutions pr??c??dentes
                    foundSolution = true;
                } else {   //  Nombre de lignes identique ou plus grand
                    if ((numLine + 1) == minLineCount)
                        foundSolution = true;      //  Nombre de lignes identique
                }
            }
        }
        return foundSolution;
    }

    private Solution buildSolution() {
        Solution solution = new Solution();
        solution.result = result;
        solution.addOpCount = 0;
        solution.subOpCount = 0;
        solution.mulOpCount = 0;
        solution.divOpCount = 0;
        String publishedText = "";
        for (int i = 0; i <= numLine; i = i + 1) {    //  Construire tout le texte de la proposition de solution
            Operators operator = lines[i].operator;
            int valTile1 = tiles[lines[i].opNumTiles[OP_NUM_TILE1_INDEX]].value;
            int valTile2 = tiles[lines[i].opNumTiles[OP_NUM_TILE2_INDEX]].value;
            int opResult = tiles[tilesInitCount + i].value;
            if (!lines[i].ordered) {   //  Inverser les plaques
                int temp = valTile1;
                valTile1 = valTile2;
                valTile2 = temp;
            }
            publishedText = publishedText + valTile1 + " " + operator.TEXT() + " " + valTile2 + " = " + opResult + SEPARATOR;   //  Texte publiable de la proposition de solution:
            if (valTile1 > valTile2) {   //  Commencer par la plus petite plaque dans chaque ligne de cette proposition de solution, avant tri par ligne
                int temp = valTile1;
                valTile1 = valTile2;
                valTile2 = temp;
            }
            shortTexts[i] = operator.TEXT() + "(" + valTile1 + "," + valTile2 + ")" + opResult + SEPARATOR;   //  Coder la solution en vue d'un tri par ligne de ses op??rations
            if (operator.equals(Operators.ADD))
                solution.addOpCount = solution.addOpCount + 1;
            if (operator.equals(Operators.SUB))
                solution.subOpCount = solution.subOpCount + 1;
            if (operator.equals(Operators.MUL))
                solution.mulOpCount = solution.mulOpCount + 1;
            if (operator.equals(Operators.DIV))
                solution.divOpCount = solution.divOpCount + 1;
        }
        java.util.Arrays.sort(shortTexts, 0, numLine);  // Trier la solution (cod??e) par ligne d'op??ration
        String shortText = "";
        for (int i = 0; i <= numLine; i = i + 1) {   // Texte tri?? de la proposition de solution cod??e
            shortText = shortText + shortTexts[i];
        }
        solution.publishedText = publishedText;
        solution.shortText = shortText;
        return solution;
    }

    private void addUniqueSolution(Solution solution) {
        boolean isUnique = true;
        for (Solution sol : solutions) {
            if (sol.shortText.equals(solution.shortText)) {
                isUnique = false;
                break;
            }
        }
        if (isUnique) solutions.add(solution);
    }

    private void publishResults() {
        Collections.sort(solutions);
        results.clear();
        results.add(solutions.size() + (isExact ? "" : " nearly") + " exact solution" + (solutions.size() > 1 ? "s" : "") + " in " + minLineCount + " line" + (minLineCount > 1 ? "s" : "") + " (optimum)");
        results.add("after " + opCount + " operation" + (opCount > 1 ? "s" : ""));
        Solution refSol = new Solution();
        for (Solution sol : solutions) {
            String s = "***";
            if (sol.compareTo(refSol) != 0) {   //  sol a d'autres caract??ristiques (result,addOpCount,subOpCount,mulOpCount,divOpCount) que refSol => Afficher ses caract??ristiques
                refSol = sol;
                s = s + "****** " + sol.addOpCount + Operators.ADD.TEXT() + " " + sol.subOpCount + Operators.SUB.TEXT() + " " + sol.mulOpCount + Operators.MUL.TEXT() + " " + sol.divOpCount + Operators.DIV.TEXT() + " " + "********" + (isExact ? "" : " " + sol.result + " ");
            }
            results.add(s);
            results.addAll(Arrays.asList(sol.publishedText.split(SEPARATOR)));
        }
        tlvResults.setItems(results);
        btnFindSolutions.getBackground().setColorFilter(isExact ? Color.GREEN : Color.RED, PorterDuff.Mode.MULTIPLY);
        btnFindSolutions.invalidate();
    }

    private void invalidateResultsDisplay() {
        tlvResults.removeAllItems();   // Vider l'affichage de toutes les lignes de toutes les solutions exactes ou approch??es
        btnFindSolutions.getBackground().clearColorFilter();
        btnFindSolutions.invalidate();
    }

    private void saveDBTileAndTargetValues() {
        for (int i = 1; i <= tilesInitCount; i = i + 1) {
            saveDBTileValueRow(stringDB, tileValueToTileValueRow(tiles[i - 1].value, i));
        }
        saveDBTargetValueRow(stringDB, targetValueToTargetValueRow(targetValue));
    }

    private void getDBTileAndTargetValues() {
        for (int i = 1; i <= tilesInitCount; i = i + 1) {
            tiles[i - 1].value = tileValueRowToTileValue(getDBTileValueRow(stringDB, i));
        }
        targetValue = targetValueRowToTargetValue(getDBTargetValueRow(stringDB));
    }

    private String getControlTextByName(String controlName) {
        String controlText = "";
        if (controlName.equals(targetIDPrefix))
            controlText = btnTarget.getText().toString();
        if (controlName.startsWith(tileIDPrefix)) {
            int numTile = Integer.parseInt(controlName.substring(tileIDPrefix.length()));  // N?? de plaque
            controlText = btnTiles[numTile - 1].getText().toString();
        }
        return controlText;
    }

    private void setControlTextByName(String controlName, String value) {
        try {
            int val = Integer.parseInt(value);    // Le nombre s??lectionn??
            String sVal = String.valueOf(val);    // Assurer un bon format p.ex. "0005"->"5"
            if (controlName.startsWith(tileIDPrefix)) {    // Plaque
                int numTile = Integer.parseInt(controlName.substring(tileIDPrefix.length()));   // N?? de plaque
                if (!(sVal.equals(btnTiles[numTile - 1].getText().toString()))) {  // Vrai changement
                    btnTiles[numTile - 1].setText(sVal);
                    invalidateResultsDisplay();
                }
            }
            if (controlName.equals(targetIDPrefix)) {  // Cible
                if (!(sVal.equals(btnTarget.getText().toString()))) {   // Vrai changement
                    btnTarget.setText(sVal);
                    invalidateResultsDisplay();
                }
            }
        } catch (NumberFormatException e) {
            msgBox("ERROR: Invalid number", this);
        }
    }

    private void updateTileValuesWithTileTexts() {
        for (int i = 1; i <= tilesInitCount; i = i + 1) {
            try {
                tiles[i - 1].value = Integer.parseInt(btnTiles[i - 1].getText().toString());    // Valeur de la plaque
                tiles[i - 1].used = false;    //  Disponible
                btnTiles[i - 1].setText(String.valueOf(tiles[i - 1].value));    //  Normalis??
            } catch (NumberFormatException ex) {   // KO
                msgBox("ERROR: Invalid Tile value " + i, this);
            }
        }
    }

    private void updateTargetValueWithTargetText() {
        try {
            targetValue = Integer.parseInt(btnTarget.getText().toString());   // Valeur de la cible
            btnTarget.setText(String.valueOf(targetValue));   //  Normalis??
        } catch (NumberFormatException ex) {   // KO
            msgBox("ERROR: Invalid Target value", this);
        }
    }

    private void updateTileTextsWithTileValues() {
        for (int i = 1; i <= tilesInitCount; i = i + 1) {
            btnTiles[i - 1].setText(String.valueOf(tiles[i - 1].value));
        }
    }

    private void updateTargetTextWithTargetValue() {
        btnTarget.setText(String.valueOf(targetValue));
    }

    private void setupButtons() {
        btnTarget = findViewById(R.id.BTN_TARGET);
        btnTarget.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBtnTargetClick(targetIDPrefix);
            }
        });
        Button btnRandomTiles = findViewById(R.id.BTN_RANDOM_TILES);
        btnRandomTiles.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onBtnRandomTilesClick();
            }
        });
        Button btnRandomTarget = findViewById(R.id.BTN_RANDOM_TARGET);
        btnRandomTarget.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onBtnRandomTargetClick();
            }
        });
        btnFindSolutions = findViewById(R.id.BTN_FIND_SOLUTIONS);
        btnFindSolutions.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onBtnFindSolutionsClick();
            }
        });
    }

    private void setupResultsTextListView() {
        tlvResults = findViewById(R.id.TLV_RESULTS);
        tlvResults.init();
    }

    private void setupTileButtons() {
        Class rid = R.id.class;
        btnTiles = new Button[tilesInitCount];
        for (int i = 1; i <= btnTiles.length; i = i + 1) {    // R??cup??ration des boutons Plaque du layout XML
            try {
                btnTiles[i - 1] = findViewById(rid.getField("BTN_TILE" + i).getInt(rid));   //  BTN_TILE1, BTN_TILE2, ...
            } catch (NoSuchFieldException | IllegalArgumentException | SecurityException | IllegalAccessException ex) {
                Logger.getLogger(MainActivity.class.getName()).log(Level.SEVERE, null, ex);
            }
            final int ind = i;
            btnTiles[i - 1].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onBtnTileClick(tileIDPrefix + ind);  // TILE1, TILE2, ...);
                }
            });
        }
    }

    private void setupStringDB() {
        stringDB = new StringDB(this);
        stringDB.open();

        if (!stringDB.tableExists(getActivityInfosTableName())) {
            createPekislibTableIfNotExists(stringDB, getActivityInfosTableName());
        }
        if (!stringDB.tableExists(getTilesTargetTableName())) {
            createLCEBTableIfNotExists(stringDB, getTilesTargetTableName());
            initializeTableTilesTarget(stringDB);
        }
    }

    private void launchHelpActivity() {
        Intent callingIntent = new Intent(this, HelpActivity.class);
        callingIntent.putExtra(Constants.ACTIVITY_EXTRA_KEYS.TITLE.toString(), HELP_ACTIVITY_TITLE);
        callingIntent.putExtra(HelpActivity.HELP_ACTIVITY_EXTRA_KEYS.HTML_ID.toString(), R.raw.helpmainactivity);
        startActivity(callingIntent);
    }

    private void launchInputButtonsActivity(String controlName, String value) {
        setCurrentForActivity(stringDB, Constants.PEKISLIB_ACTIVITIES.INPUT_BUTTONS.toString(), getTilesTargetTableName(), getTilesTargetValueIndex(), value);
        setStartStatusOfActivity(stringDB, Constants.PEKISLIB_ACTIVITIES.INPUT_BUTTONS.toString(), StringDBTables.ACTIVITY_START_STATUS.COLD);
        Intent callingIntent = new Intent(this, InputButtonsActivity.class);
        callingIntent.putExtra(StringDBTables.TABLE_EXTRA_KEYS.TABLE.toString(), getTilesTargetTableName());
        callingIntent.putExtra(StringDBTables.TABLE_EXTRA_KEYS.INDEX.toString(), getTilesTargetValueIndex());
        callingIntent.putExtra(Constants.ACTIVITY_EXTRA_KEYS.TITLE.toString(), controlName);
        startActivityForResult(callingIntent, Constants.PEKISLIB_ACTIVITIES.INPUT_BUTTONS.INDEX());
    }
}