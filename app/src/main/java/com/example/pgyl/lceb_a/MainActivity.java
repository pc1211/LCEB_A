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
import java.util.Comparator;
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

// A chaque ligne de résultat intermédiaire (lines[]), une paire de plaques (tiles[]) disponibles
// est sélectionnée et utilisée pour une opération.
// Si le résultat de la ligne (qui devient une nouvelle plaque disponible)
// atteint ou réduit l'écart minimum actuel par rapport à la cible
// et si le nombre de lignes nécessaires atteint ou réduit le minimum actuel,
// alors ces lignes sont enregistrées comme solution (solutionTexts[]) (si cette solution est différente des précédentes)
// Après une ligne, une nouvelle ligne est essayée avec la première paire de plaques disponible
// Si le nombre maximum de lignes est atteint, on essaye un autre opérateur pour les mêmes plaques de la même ligne
// S'il n'y a plus d'opérateur disponible, on essaye une autre paire de plaques pour la même ligne
//   (d'abord en changeant de 2e plaque, puis à la fois de 1e plaque et de 2e plaque)
// S'il n'y a plus de paire de plaques disponible pour la même ligne, on revient à la ligne précédente
// S'il n'y a plus de ligne précédente, c'est fini !

enum Operators {   //  Opérateurs entre 2 plaques
    BEGIN("b"), ADD("+"), SUB("-"), MUL("*"), DIV("/"), END("e");

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

class Line {   // Ligne de résultat intermédiaire
    int[] opNumTiles = new int[2];   //  Les numéros de plaques utilisés pour l'opération de la ligne
    Operators operator;
    boolean ordered;         //  True si (tile1 op tile2), False si (tile2 op tile1)
}

class Solution {   // Solution (exacte ou rapprochée)
    String publishedText;  //  "5 - 3 = 2£2 * 6 = 12£"   Texte de solution prêt pour la publication (Toutes les lignes et résultats, en clair)
    String shortText;      //  "*(2,6)12£-(2,5)3£"       Texte court de solution trié par ligne pour vérifier si est bien différent des précédents, en commençant par la plus petite plaque au sein de chaque ligne
    int addOpCount;
    int subOpCount;
    int mulOpCount;
    int divOpCount;
    int result;   //  Meilleur résultat obtenu
}

public class MainActivity extends Activity {
    private Button btnTarget;
    private Button btnFindSolutions;
    private Button[] btnTiles;        //  Boutons de plaque
    private TextListView tlvResults;
    private int tilesInitCount;       //  Nombre de plaques initial
    private Tile[] tiles;             //  Plaques initiales et intermédiaires
    private Line[] lines;             //  Lignes de résultat intermédiaire.  A la ligne i correspond la plaque de résultat i+tilesInitCount
    private final ArrayList<Solution> solutions = new ArrayList<Solution>();   //  Solutions (exactes ou rapprochées) validées
    private final ArrayList<String> results = new ArrayList<String>();   //  L'ensemble des lignes de résultats à afficher de toutes les solutions (exactes ou rapprochées) validées
    private String[] shortTexts;       //  Sert au tri par ligne d'une solution proposée (cf type Solution)
    int numLine;          //  N° de ligne de résultat intermédiaire actuelle
    int targetValue;      //  Cible à atteindre
    int diff;             //  Ecart (en valeur absolue) par rapport à la cible, à égaler ou réduire
    int result;           //  Résultat de l'opération en cours
    int minLineCount;     //  Nombre minimum de lignes des solutions actuelles, à égaler ou réduire
    int opCount;          //  Nombre total d'opérations
    boolean isExact;      //  True => Il y a au moins une solution exacte
    boolean isEnd;        //  True => C'est la fin des calculs, faute de plaques disponibles
    boolean isNewLine;    //  True => Une nouvelle ligne commence

    private boolean validReturnFromCalledActivity;
    private String calledActivityName;
    private StringDB stringDB;
    private String controlName;
    private Menu menu;
    private final String SEPARATOR = "£";
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
        tiles = new Tile[tilesInitCount + lines.length];  //  tilesInitCount plaques initiales + (tilesInitCount-1) plaques de résultat intermédiaire
        for (int i = 1; i <= tiles.length; i = i + 1) {   //  Instanciation de chaque élément
            tiles[i - 1] = new Tile();
        }
        shortTexts = new String[lines.length];  //  Sert au tri par ligne d'une solution proposée
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {  //  Non appelé après changement d'orientation
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
        int[] tileValues = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 25, 50, 75, 100};  // Distribution des plaques disponibles au départ

        invalidateResultsDisplay();
        boolean[] reservedIndexes = new boolean[tileValues.length];
        for (int i = 1; i <= reservedIndexes.length; i = i + 1) {
            reservedIndexes[i - 1] = false;  //  Toutes les 28 plaques sont disponibles au tirage
        }
        int index = 0;
        for (int i = 1; i <= tilesInitCount; i = i + 1) {  // Remplissage des plaques
            do {
                index = (int) (Math.random() * (tileValues.length));   //  0..(values.length-1)
            } while (reservedIndexes[index]);    //  Jusqu'à trouver une plaque non encore tirée
            reservedIndexes[index] = true;
            btnTiles[i - 1].setText(String.valueOf(tileValues[index]));
        }
    }

    private void onBtnRandomTargetClick() {
        invalidateResultsDisplay();
        btnTarget.setText(String.valueOf(1 + (int) (Math.random() * (999))));  // Remplissage de la cible à trouver   1..999
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
                getTile1And2(TileSearchType.FIRST_AVAILABLE);   //  Prendre les 2 premières plaques disponibles
            }
            if (!getNextOperatorAndResult()) {   //  Obtenir si possible le prochain opérateur et son résultat
                if (!getTile2(TileSearchType.NEXT_AVAILABLE)) {           //  Libérer la 2e plaque et si possible prendre la suivante
                    if (!getTile1And2(TileSearchType.NEXT_AVAILABLE)) {   //  Libérer les 2 plaques et si possible prendre les suivantes
                        if (numLine > 0)
                            numLine = numLine - 1;    //  Faute de plaques pour cette ligne, on revient à la ligne précédente
                        else isEnd = true;
                    }
                }
                continue;   // Reprendre à while(!isEnd)
            }
            formNewTileFromResult();
            if (foundSolution())
                addUniqueSolution(buildSolution());   //  Si les solutions précédentes sont différentes => La proposition de solution est validée
            if (numLine < (lines.length - 1)) {     //  Une nouvelle ligne est possible
                numLine = numLine + 1;    //  Nouvelle ligne de résultat intermédiaire
                isNewLine = true;
            }
        }
        publishResults();   // Publier toutes les solutions exactes ou (à défaut) rapprochées validées
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
        tiles[tilesInitCount + numLine].value = result;     //  Le résultat de la ligne est une nouvelle plaque disponible
        tiles[tilesInitCount + numLine].used = false;
        opCount = opCount + 1;
    }

    private boolean getTile2(TileSearchType tileSearchType) {    //  Libérer la 2e plaque et prendre la suivante
        int numTile2 = lines[numLine].opNumTiles[OP_NUM_TILE2_INDEX];
        if (tileSearchType.equals(TileSearchType.NEXT_AVAILABLE)) {
            tiles[numTile2].used = false;   //  Libérer la 2e plaque
        } else {   //  tileSearchType=FIRST_AVAILABLE
            numTile2 = -1;
        }
        int numNextTile2 = getNextAvailableTileNumber(numTile2);   //  2e plaque suivante
        if (numNextTile2 != NOT_AVAILABLE) {
            tiles[numNextTile2].used = true;   //  Réserver la 2e plaque
            lines[numLine].opNumTiles[OP_NUM_TILE2_INDEX] = numNextTile2;
            lines[numLine].operator = Operators.BEGIN;
            return true;
        }
        return false;
    }

    private boolean getTile1And2(TileSearchType tileSearchType) {   //  Libérer les 2 plaques (si tileSearchType=NEXT_AVAILABLE) et prendre les suivantes. Pour la ligne numLine, tilesInitCount-numLine+2 plaques sont disponibles sur un total de tilesInitCount+numLine plaques
        int numTile1 = lines[numLine].opNumTiles[OP_NUM_TILE1_INDEX];
        int numTile2 = lines[numLine].opNumTiles[OP_NUM_TILE2_INDEX];
        if (tileSearchType.equals(TileSearchType.NEXT_AVAILABLE)) {
            tiles[numTile1].used = false;    //  Libérer les 2 plaques
            tiles[numTile2].used = false;
        } else {   //  tileSearchType=FIRST_AVAILABLE
            numTile1 = -1;
        }
        int numNextTile1 = getNextAvailableTileNumber(numTile1);       //  1e plaque suivante : Si numTile1 = -1 => Vérifier toutes les plaques (à partir de 0), sinon après la 1e plaque actuelle
        if (numNextTile1 != NOT_AVAILABLE) {
            int numNextTile2 = getNextAvailableTileNumber(numNextTile1);   //  2e plaque, suivant la 1e
            if (numNextTile2 != NOT_AVAILABLE) {
                tiles[numNextTile1].used = true;   //  Réserver les 2 plaques
                tiles[numNextTile2].used = true;
                lines[numLine].opNumTiles[OP_NUM_TILE1_INDEX] = numNextTile1;
                lines[numLine].opNumTiles[OP_NUM_TILE2_INDEX] = numNextTile2;
                lines[numLine].operator = Operators.BEGIN;
                return true;
            }
        }
        return false;
    }

    private int getNextAvailableTileNumber(int numTile) {   // Trouver la 1e plaque disponible après numTile
        int numNextTile = NOT_AVAILABLE;   // Retourner -1000 si aucune plaque disponible
        int i = numTile;
        while (i < (tilesInitCount + numLine - 1)) {   // Pour la ligne numLine, les plaques à vérifier vont de minimum 0 à maximum tilesInitCount+numLine-1
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
        Operators operator = lines[numLine].operator.getNext();  //  2 plaques sont prêtes, passer à l'opérateur suivant
        switch (operator) {
            case ADD:
                if ((valTile1 == 0) || (valTile2 == 0))
                    operator = Operators.END;   //  Les autres opérateurs ne seront pas examinés
                else result = valTile1 + valTile2;
                break;
            case SUB:
                if (valTile1 == valTile2)
                    operator = Operators.MUL;   //  On ne sait rien faire avec un résultat 0; Passer à MUL => Pas de break
                else {
                    if (valTile1 < valTile2) {
                        lines[numLine].ordered = false;
                        result = valTile2 - valTile1;
                    } else result = valTile1 - valTile2;
                    break;
                }
            case MUL:
                if ((valTile1 == 1) || (valTile2 == 1))
                    operator = Operators.END;   //  La division ne sera pas examinée
                else result = valTile1 * valTile2;
                break;
            case DIV:
                if (valTile1 < valTile2) {
                    if ((valTile2 % valTile1) != 0)
                        operator = Operators.END;    // La division doit être juste
                    else {
                        lines[numLine].ordered = false;
                        result = valTile2 / valTile1;
                    }
                } else {
                    if ((valTile1 % valTile2) != 0)
                        operator = Operators.END;  // La division doit être juste
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
        if (Math.abs(result - targetValue) < diff) {     //  Résultat plus proche de la cible
            diff = Math.abs(result - targetValue);
            if (diff == 0) isExact = true;          //  Solution exacte trouvée !
            minLineCount = numLine + 1;
            solutions.clear();                      //  Effacer toutes les solutions précédentes
            foundSolution = true;
        } else {  //  Résultat identique ou plus écarté de la cible
            if (Math.abs(result - targetValue) == diff) {   //  Résultat identique
                if ((numLine + 1) < minLineCount) {          //  Nombre de lignes plus petit
                    minLineCount = numLine + 1;
                    solutions.clear();                      //  Effacer toutes les solutions précédentes
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
            shortTexts[i] = operator.TEXT() + "(" + valTile1 + "," + valTile2 + ")" + opResult + SEPARATOR;   //  Coder la solution en vue d'un tri par ligne de ses opérations
            if (operator.equals(Operators.ADD))
                solution.addOpCount = solution.addOpCount + 1;
            if (operator.equals(Operators.SUB))
                solution.subOpCount = solution.subOpCount + 1;
            if (operator.equals(Operators.MUL))
                solution.mulOpCount = solution.mulOpCount + 1;
            if (operator.equals(Operators.DIV))
                solution.divOpCount = solution.divOpCount + 1;
        }
        java.util.Arrays.sort(shortTexts, 0, numLine);  // Trier la solution (codée) par ligne d'opération
        String shortText = "";
        for (int i = 0; i <= numLine; i = i + 1) {   // Texte trié de la proposition de solution codée
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

    private void sortSolutions() {  //  Trier par result ASC, addOpCount ASC, subOpCount ASC, mulOpCount ASC, divOpCount ASC
        if (solutions.size() >= 2) {
            Collections.sort(solutions, new Comparator<Solution>() {
                public int compare(Solution solution1, Solution solution2) {
                    int res = Integer.compare(solution1.result, solution2.result);
                    if (res == 0) {
                        res = Integer.compare(solution1.addOpCount, solution2.addOpCount);
                        if (res == 0) {
                            res = Integer.compare(solution1.subOpCount, solution2.subOpCount);
                            if (res == 0) {
                                res = Integer.compare(solution1.mulOpCount, solution2.mulOpCount);
                                if (res == 0) {
                                    res = Integer.compare(solution1.divOpCount, solution2.divOpCount);
                                }
                            }
                        }
                    }
                    return res;
                }
            });
        }
    }

    private void publishResults() {
        sortSolutions();
        results.clear();
        results.add(solutions.size() + (isExact ? "" : " nearly") + " exact solution" + (solutions.size() > 1 ? "s" : "") + " in " + minLineCount + " line" + (minLineCount > 1 ? "s" : "") + " (optimum)");
        results.add("after " + opCount + " operation" + (opCount > 1 ? "s" : ""));
        int result = targetValue;
        for (Solution sol : solutions) {
            if (sol.result != result) {   //  C'est une solution rapprochée
                result = sol.result;
                results.add("******************************");
                results.add("****** Best result:  " + result + " ******");
            }
            results.add("********* " + sol.addOpCount + "+ " + sol.subOpCount + "- " + sol.mulOpCount + "* " + sol.divOpCount + "/ " + "*********");
            results.addAll(Arrays.asList(sol.publishedText.split(SEPARATOR)));
        }
        tlvResults.setItems(results);
        btnFindSolutions.getBackground().setColorFilter(isExact ? Color.GREEN : Color.RED, PorterDuff.Mode.MULTIPLY);
        btnFindSolutions.invalidate();
    }

    private void invalidateResultsDisplay() {
        tlvResults.removeAllItems();   // Vider l'affichage de toutes les lignes de toutes les solutions exactes ou approchées
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
            int numTile = Integer.parseInt(controlName.substring(tileIDPrefix.length()));  // N° de plaque
            controlText = btnTiles[numTile - 1].getText().toString();
        }
        return controlText;
    }

    private void setControlTextByName(String controlName, String value) {
        try {
            int val = Integer.parseInt(value);    // Le nombre sélectionné
            String sVal = String.valueOf(val);    // Assurer un bon format p.ex. "0005"->"5"
            if (controlName.startsWith(tileIDPrefix)) {    // Plaque
                int numTile = Integer.parseInt(controlName.substring(tileIDPrefix.length()));   // N° de plaque
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
                btnTiles[i - 1].setText(String.valueOf(tiles[i - 1].value));    //  Normalisé
            } catch (NumberFormatException ex) {   // KO
                msgBox("ERROR: Invalid Tile value " + i, this);
            }
        }
    }

    private void updateTargetValueWithTargetText() {
        try {
            targetValue = Integer.parseInt(btnTarget.getText().toString());   // Valeur de la cible
            btnTarget.setText(String.valueOf(targetValue));   //  Normalisé
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
        for (int i = 1; i <= btnTiles.length; i = i + 1) {    // Récupération des boutons Plaque du layout XML
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