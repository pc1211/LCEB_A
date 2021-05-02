package com.example.pgyl.lceb_a;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import com.example.pgyl.pekislib_a.Constants;
import com.example.pgyl.pekislib_a.HelpActivity;
import com.example.pgyl.pekislib_a.InputButtonsActivity;
import com.example.pgyl.pekislib_a.StringDB;
import com.example.pgyl.pekislib_a.StringDBTables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
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

class Tile {           // Plaque
    int value;          // Valeur de la plaque
    boolean used;       // True => Plaque n'est plus disponible
}

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

class Line {     // Ligne de résultat intermédiaire
    int numTile1;    //   1e plaque
    int numTile2;    //   2e plaque
    Operators operator;
    boolean ordered;    //  True si (numTile1 operator numTile2), False si (numTile2 operator numTile1)
}

class Solution {       // Solution (exacte ou rapprochée) (en texte)
    String publishedText;
    //  Texte de solution prêt pour la publication (Toutes les lignes et résultats, en clair)
    //      p.ex. "5 - 3 = 2
    //             2 * 6 = 12"
    String shortText;
    //  Texte de solution trié par ligne pour vérifier si est bien différent des précédents,
    //      en commençant par la plus petite plaque au sein de chaque ligne.
    //      Pour le même exemple: "$2;*;6;12$3;-;5;2"
    int opAddCount = 0;
    int opSubCount = 0;
    int opMulCount = 0;
    int opDivCount = 0;
}

public class MainActivity extends Activity {
    private Button btnTarget;
    Button[] btnTiles; // Boutons de plaque
    private Button btnRandomTiles;
    private Button btnRandomTarget;
    private Button btnFindSolutions;
    private TextView txvSolutions;

    private int tilesInitCount;       //  Nombre de plaques initial
    private Tile[] tiles;             //  Plaques initiales et intermédiaires
    private Line[] lines;             //  Lignes de résultat intermédiaire
    private final List<Solution> solutions = new ArrayList<Solution>();   //  Solutions (exactes ou rapprochées) validées
    private String[] shortTexts;       //  Sert au tri par ligne d'une solution proposée (cf type TypeSolution)
    int numLine;          //  N° de ligne de résultat intermédiaire actuelle
    int targetValue;           //  Cible à atteindre
    int diff;             //  Ecart par rapport à la cible à égaler ou réduire
    int minLineCount;     //  Nombre de lignes des solutions actuelles, à égaler ou réduire
    int opCount;          //  Nombre total d'opérations
    boolean isExact;      //  True => Il y a au moins une solution exacte
    boolean isEnd;        //  True => C'est la fin des calculs, faute de plaques disponibles
    boolean isNewLine;    //  Une nouvelle ligne commence

    private boolean validReturnFromCalledActivity;
    private String calledActivityName;
    private StringDB stringDB;
    private String controlName;
    private Menu menu;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final String ACTIVITY_TITLE = "Le compte est bon!";

        getWindow().requestFeature(Window.FEATURE_ACTION_BAR);
        getActionBar().setTitle(ACTIVITY_TITLE);
        setContentView(R.layout.activity_main);
        setupControls();
        validReturnFromCalledActivity = false;
    }

    @Override
    protected void onPause() {
        super.onPause();

        updateTileValuesWithTileTexts();
        updateTargetValueWithTargetText();
        saveDBTileAndTargetValues();
        btnTiles = null;
        stringDB.close();
        stringDB = null;
        menu = null;
    }

    @Override
    protected void onResume() {
        super.onResume();

        setupStringDB();
        tilesInitCount = getDBTilesInitCount(stringDB);
        inits();
        setupTileButtons();
        getDBTileAndTargetValues();
        updateTileTextsWithTileValues();
        updateTargetTextWithTargetValue();
        if (validReturnFromCalledActivity) {
            validReturnFromCalledActivity = false;
            if (calledActivityName.equals(Constants.PEKISLIB_ACTIVITIES.INPUT_BUTTONS.toString())) {
                setControlTextByName(controlName, getCurrentFromActivity(stringDB, Constants.PEKISLIB_ACTIVITIES.INPUT_BUTTONS.toString(), getTilesTargetTableName(), getTilesTargetValueIndex()));
            }
        }
        invalidateOptionsMenu();
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
        int[] tileValues = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 25, 25, 50, 50, 75, 75, 100, 100};  // Distribution des 28 plaques disponibles au départ

        invalidateSolutionDisplay();
        boolean[] reservedIndexes = new boolean[tileValues.length];
        for (int i = 1; i <= tileValues.length - 1; i = i + 1) {
            reservedIndexes[i] = false;  //  Toutes les 28 plaques sont disponibles au tirage
        }
        int index = 0;
        for (int i = 1; i <= tilesInitCount; i = i + 1) {  // Remplissage des plaques
            do {
                index = (int) (Math.random() * (tileValues.length));
            } while (reservedIndexes[index]);    //  Jusqu'à trouver une plaque non encore tirée
            reservedIndexes[index] = true;
            btnTiles[i - 1].setText(String.valueOf(tileValues[index]));
        }
    }

    private void onBtnRandomTargetClick() {
        invalidateSolutionDisplay();
        btnTarget.setText(String.valueOf(1 + (int) (Math.random() * (999))));  // Remplissage de la cible à trouver
    }

    private void onBtnFindSolutionsClick() {
        diff = 999999;
        minLineCount = 999999;
        opCount = 0;
        numLine = 1;
        isExact = false;
        isEnd = false;
        isNewLine = true;

        updateTileValuesWithTileTexts();
        updateTargetValueWithTargetText();
        invalidateSolutionDisplay();
        solutions.clear();
        while (!isEnd) {
            if (isNewLine) {
                isNewLine = false;
                getFirstTile1And2();   //  Prendre les 2 premières plaques disponibles
            }
            int result = getResultFromNextOperator();
            if (lines[numLine].operator.equals(Operators.END)) {   //  Il n'y a plus d'opérateurs disponibles
                if (!getNextTile2()) {   //  Essayer la plaque suivant la 2e plaque actuelle
                    if (!getNextTile1And2()) {   //  Essayer les 2 plaques suivant les 2 plaques actuelles
                        if (numLine > 1)
                            numLine = numLine - 1;    //  Faute de plaques pour cette ligne, on revient à la ligne précédente
                        else isEnd = true;
                    }
                }
                continue;   // Reprendre à while(!isEnd)
            }
            tiles[tilesInitCount + numLine].value = result;     //  Le résultat de la ligne est une nouvelle plaque disponible
            tiles[tilesInitCount + numLine].used = false;
            opCount = opCount + 1;
            if (isGood(result)) {
                Solution solution = getSolution();   //  Créer le texte complet de la solution proposée
                if (isUnique(solution))              //  Les solutions précédentes sont différentes => La proposition de solution est validée
                    solutions.add(solution);
            }
            if (numLine < (tilesInitCount - 1)) {    //  Une nouvelle ligne est possible
                numLine = numLine + 1;    //  Nouvelle ligne de résultat intermédiaire
                isNewLine = true;
            }
        }
        publishSolutions();   // Publier toutes les solutions exactes ou (à défaut) rapprochées validées
    }

    private void saveDBTileAndTargetValues() {
        for (int i = 1; i <= tilesInitCount; i = i + 1) {
            saveDBTileValueRow(stringDB, tileValueToTileValueRow(tiles[i].value, i));
        }
        saveDBTargetValueRow(stringDB, targetValueToTargetValueRow(targetValue));
    }

    private void getDBTileAndTargetValues() {
        for (int i = 1; i <= tilesInitCount; i = i + 1) {
            tiles[i].value = tileValueRowToTileValue(getDBTileValueRow(stringDB, i));
        }
        targetValue = targetValueRowToTargetValue(getDBTargetValueRow(stringDB));
    }

    private void inits() {
        tiles = new Tile[2 * tilesInitCount];     //  => OK 1..2*tilesInitCount-1  (cad tilesInitCount plaques initiales + (tilesInitCount-1) plaques de résultat intermédiaire
        lines = new Line[tilesInitCount];         //  => OK 1..tilesInitCount-1
        shortTexts = new String[tilesInitCount];  //  => OK 1..tilesInitCount-1   Sert au tri par ligne d'une solution proposée

        for (int i = 1; i <= (2 * tilesInitCount - 1); i = i + 1) {   //  Instanciation de chaque élément
            tiles[i] = new Tile();
        }
        for (int i = 1; i <= (tilesInitCount - 1); i = i + 1) {
            lines[i] = new Line();
        }
    }

    private String getControlTextByName(String controlName) {
        String controlText = "";
        if (controlName.equals(targetIDPrefix)) {
            controlText = btnTarget.getText().toString();
        }
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
                    invalidateSolutionDisplay();
                }
            }
            if (controlName.equals(targetIDPrefix)) {  // Cible
                if (!(sVal.equals(btnTarget.getText().toString()))) {   // Vrai changement
                    btnTarget.setText(sVal);
                    invalidateSolutionDisplay();
                }
            }
        } catch (NumberFormatException e) {
            msgBox("ERROR: Invalid number", this);
        }
    }

    private void updateTileValuesWithTileTexts() {
        for (int i = 1; i <= tilesInitCount; i = i + 1) {
            try {
                tiles[i].value = Integer.parseInt(btnTiles[i - 1].getText().toString());    // Valeur de la plaque
                tiles[i].used = false;    //  Disponible
                btnTiles[i - 1].setText(String.valueOf(tiles[i].value));    //  Normalisé
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
            btnTiles[i - 1].setText(String.valueOf(tiles[i].value));
        }
    }

    private void updateTargetTextWithTargetValue() {
        btnTarget.setText(String.valueOf(targetValue));
    }

    int selectNextFreeTileNumber(int numTile) {   // Sélectionner la 1e plaque disponible après le n° d'ordre numTile
        int nextFreeTileNumber = 0;   // Retourner 0 si aucune plaque disponible
        int i = numTile;
        while (i < (tilesInitCount + numLine - 1)) {   // Pour la ligne numLine, les plaques à vérifier vont de 1 à tilesInitCount+numLine-1
            i = i + 1;
            if (!tiles[i].used) {
                tiles[i].used = true;   // Sélectionner la plaque si disponible
                nextFreeTileNumber = i;
                break;
            }
        }
        return nextFreeTileNumber;
    }

    int selectFirstFreeTileNumber() {   // Sélectionner la 1ère plaque disponible
        return selectNextFreeTileNumber(0);   //  cad le 1er n° disponible après le n° 0
    }

    private void getFirstTile1And2() {    //  Pour la ligne numLine (1..tilesInitCount-1), tilesInitCount-numLine+1 plaques sont disponibles sur un total de tilesInitCount+numLine-1 plaques
        lines[numLine].numTile1 = selectFirstFreeTileNumber();   // Prendre les 2 premières plaques disponibles (il y en a toujours au moins 2)
        lines[numLine].numTile2 = selectNextFreeTileNumber(lines[numLine].numTile1);  //  La 2e plaque doit toujours suivre la 1e
        lines[numLine].operator = Operators.BEGIN;
    }

    private int getResultFromNextOperator() {
        int result = 0;
        int valTile1 = tiles[lines[numLine].numTile1].value;
        int valTile2 = tiles[lines[numLine].numTile2].value;
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
        return result;
    }

    private boolean getNextTile2() {
        boolean getNextTile2 = false;
        int numTile2 = lines[numLine].numTile2;
        tiles[numTile2].used = false;   //  La 2e plaque actuelle redevient disponible
        numTile2 = selectNextFreeTileNumber(numTile2);
        if (numTile2 != 0) {   // OK, on l'essaye en gardant la même 1e plaque
            lines[numLine].numTile2 = numTile2;
            lines[numLine].operator = Operators.BEGIN;
            getNextTile2 = true;
        }
        return getNextTile2;
    }

    private boolean getNextTile1And2() {
        boolean getNextTile1And2 = false;
        tiles[lines[numLine].numTile1].used = false;   //  Les 2 plaques actuelles redeviennent disponibles
        tiles[lines[numLine].numTile2].used = false;
        int numTile1 = selectNextFreeTileNumber(lines[numLine].numTile1);
        int numTile2 = selectNextFreeTileNumber(numTile1);
        if ((numTile1 != 0) && (numTile2 != 0)) {   // OK, on les essaye
            lines[numLine].numTile1 = numTile1;
            lines[numLine].numTile2 = numTile2;
            lines[numLine].operator = Operators.BEGIN;
            getNextTile1And2 = true;
        }
        return getNextTile1And2;
    }

    private boolean isGood(int result) {
        boolean isGood = false;
        if (Math.abs(result - targetValue) < diff) {     //  Résultat plus proche de la cible
            diff = Math.abs(result - targetValue);
            if (diff == 0) isExact = true;          //  Solution exacte trouvée !
            minLineCount = numLine;
            solutions.clear();                      //  Effacer toutes les solutions précédentes
            isGood = true;
        } else {  //  Résultat identique ou plus écarté de la cible
            if (Math.abs(result - targetValue) == diff) {   //  Résultat identique
                if (numLine < minLineCount) {          //  Nombre de lignes plus petit
                    minLineCount = numLine;
                    solutions.clear();                      //  Effacer toutes les solutions précédentes
                    isGood = true;
                } else {   //  Nombre de lignes identique ou plus grand
                    if (numLine == minLineCount) isGood = true;      //  Nombre de lignes identique
                }
            }
        }
        return isGood;
    }

    private Solution getSolution() {
        Solution solution = new Solution();
        String publishedText = "";
        for (int i = 1; i <= numLine; i = i + 1) {    //  Construire tout le texte de la proposition de solution
            Operators operator = lines[i].operator;
            int valTile1 = tiles[lines[i].numTile1].value;
            int valTile2 = tiles[lines[i].numTile2].value;
            int result = tiles[tilesInitCount + i].value;
            if (!lines[i].ordered) {   //  Inverser les plaques
                int temp = valTile1;
                valTile1 = valTile2;
                valTile2 = temp;
            }
            publishedText = publishedText + " " + valTile1 + " " + operator.TEXT() + " " + valTile2 + " = " + result + "\n";   //  Texte publiable de la proposition de solution:
            if (valTile1 > valTile2) {   //  Commencer par la plus petite plaque dans chaque ligne de cette proposition de solution, avant tri par ligne
                int temp = valTile1;
                valTile1 = valTile2;
                valTile2 = temp;
            }
            shortTexts[i] = "$" + valTile1 + ";" + operator.TEXT() + ";" + valTile2 + ";" + result;   //  Coder la solution en vue d'un tri par ligne de ses opérations
            if (operator.equals(Operators.ADD))
                solution.opAddCount = solution.opAddCount + 1;
            if (operator.equals(Operators.SUB))
                solution.opSubCount = solution.opSubCount + 1;
            if (operator.equals(Operators.MUL))
                solution.opMulCount = solution.opMulCount + 1;
            if (operator.equals(Operators.DIV))
                solution.opDivCount = solution.opDivCount + 1;
        }
        java.util.Arrays.sort(shortTexts, 1, numLine);  // Trier la solution (codée) par ligne d'opération
        String shortText = "";
        for (int i = 1; i <= numLine; i = i + 1) {   // Texte trié de la proposition de solution codée
            shortText = shortText + shortTexts[i];
        }
        solution.publishedText = publishedText;
        solution.shortText = shortText;
        return solution;
    }

    private boolean isUnique(Solution solution) {
        boolean isUnique = true;
        for (Solution sol : solutions) {
            if (sol.shortText.equals(solution.shortText)) {
                isUnique = false;
                break;
            }
        }
        return isUnique;
    }

    private void sortSolutions() {  //  Trier par opAddCount ASC, opSubCount ASC, opMulCount ASC, opDivCount ASC
        if (solutions.size() >= 2) {
            Collections.sort(solutions, new Comparator<Solution>() {
                public int compare(Solution solution1, Solution solution2) {
                    int res = Integer.compare(solution1.opAddCount, solution2.opAddCount);
                    if (res == 0) {
                        res = Integer.compare(solution1.opSubCount, solution2.opSubCount);
                        if (res == 0) {
                            res = Integer.compare(solution1.opMulCount, solution2.opMulCount);
                            if (res == 0) {
                                res = Integer.compare(solution1.opDivCount, solution2.opDivCount);
                            }
                        }
                    }
                    return res;
                }
            });
        }
    }

    private void publishSolutions() {
        sortSolutions();
        String intro = " " + solutions.size() + (isExact ? "" : " nearly") + " exact solution" + (solutions.size() > 1 ? "s" : "") + " in " + minLineCount + " line" + (minLineCount > 1 ? "s" : "") + " (optimum)" + "\n";
        intro = intro + " after " + opCount + " operation" + (opCount > 1 ? "s" : "") + "\n";
        String s = intro;
        for (Solution sol : solutions) {
            s = s + "********* " + sol.opAddCount + "+ " + sol.opSubCount + "- " + sol.opMulCount + "* " + sol.opDivCount + "/ " + "*********" + "\n";
            s = s + sol.publishedText;
        }
        txvSolutions.setText(s);
        txvSolutions.scrollTo(0, 0);
        btnTarget.getBackground().setColorFilter(isExact ? Color.GREEN : Color.RED, PorterDuff.Mode.MULTIPLY);
        btnTarget.invalidate();
    }

    private void invalidateSolutionDisplay() {
        txvSolutions.setText("");       // Vider l'affichage de toutes les lignes de toutes les solutions exactes ou approchées
        btnTarget.getBackground().clearColorFilter();
        btnTarget.invalidate();
    }

    private void setupControls() {
        btnTarget = findViewById(R.id.BTN_TARGET);
        btnRandomTiles = findViewById(R.id.BTN_RANDOM_TILES);
        btnRandomTarget = findViewById(R.id.BTN_RANDOM_TARGET);
        btnFindSolutions = findViewById(R.id.BTN_FIND_SOLUTIONS);
        txvSolutions = findViewById(R.id.TXV_SOL);
        txvSolutions.setMovementMethod(new ScrollingMovementMethod());
        btnTarget.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBtnTargetClick(targetIDPrefix);
            }
        });
        btnRandomTiles.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onBtnRandomTilesClick();
            }
        });
        btnRandomTarget.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onBtnRandomTargetClick();
            }
        });
        btnFindSolutions.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onBtnFindSolutionsClick();
            }
        });
    }

    private void setupTileButtons() {
        Class rid = R.id.class;
        btnTiles = new Button[tilesInitCount];
        for (int i = 1; i <= tilesInitCount; i = i + 1) {    // Récupération des boutons Plaque du layout XML
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