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

import java.util.logging.Level;
import java.util.logging.Logger;

import static com.example.pgyl.lceb_a.StringDBTables.getPlatesTargetTableName;
import static com.example.pgyl.lceb_a.StringDBTables.getPlatesTargetValueIndex;
import static com.example.pgyl.lceb_a.StringDBTables.plateIDPrefix;
import static com.example.pgyl.lceb_a.StringDBTables.plateValueRowToPlateValue;
import static com.example.pgyl.lceb_a.StringDBTables.plateValueToPlateValueRow;
import static com.example.pgyl.lceb_a.StringDBTables.targetIDPrefix;
import static com.example.pgyl.lceb_a.StringDBTables.targetRowToTarget;
import static com.example.pgyl.lceb_a.StringDBTables.targetToTargetRow;
import static com.example.pgyl.lceb_a.StringDBUtils.createLCEBTableIfNotExists;
import static com.example.pgyl.lceb_a.StringDBUtils.getDBPlateInitCount;
import static com.example.pgyl.lceb_a.StringDBUtils.getDBPlateValueRow;
import static com.example.pgyl.lceb_a.StringDBUtils.getDBTargetValueRow;
import static com.example.pgyl.lceb_a.StringDBUtils.initializeTablePlatesTarget;
import static com.example.pgyl.lceb_a.StringDBUtils.saveDBPlateValueRow;
import static com.example.pgyl.lceb_a.StringDBUtils.saveDBTargetValueRow;
import static com.example.pgyl.pekislib_a.HelpActivity.HELP_ACTIVITY_TITLE;
import static com.example.pgyl.pekislib_a.MiscUtils.msgBox;
import static com.example.pgyl.pekislib_a.StringDBTables.getActivityInfosTableName;
import static com.example.pgyl.pekislib_a.StringDBUtils.createPekislibTableIfNotExists;
import static com.example.pgyl.pekislib_a.StringDBUtils.getCurrentFromActivity;
import static com.example.pgyl.pekislib_a.StringDBUtils.setCurrentForActivity;
import static com.example.pgyl.pekislib_a.StringDBUtils.setStartStatusOfActivity;

// A chaque ligne de résultat intermédiaire (lines[]), une paire de plaques (plates[]) disponibles
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

class Plate {           // Plaque
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
    int numPlate1;    //   1e plaque
    int numPlate2;    //   2e plaque
    Operators operator;
    boolean ordered;    //  True si (numPlate1 operator numPlate2), False si (numPlate2 operator numPlate1)
}

class SolutionText {       // Solution (exacte ou rapprochée) (en texte)
    String pub;
    //  Texte de solution prêt pour la publication (Toutes les lignes et résultats, en clair)
    //      p.ex. "5 - 3 = 2
    //             2 * 6 = 12"
    String sorted;
    //  Texte de solution trié par ligne pour vérifier si est bien différent des précédents,
    //      en commençant par la plus petite plaque au sein de chaque ligne.
    //      Pour le même exemple: "$2;*;6;12$3;-;5;2"
}

public class MainActivity extends Activity {
    private Button btnTarget;
    Button[] btnPlates; // Boutons de plaque
    private Button btnNewPlates;
    private Button btnNewTarget;
    private Button btnFindSolutions;
    private TextView txvSolutions;

    private final int SOLUTION_COUNT_TEMP_MAX = 50;  // Nombre max de solutions (augmenté automatiquement si nécessaire)

    private int plateInitCount;       //  Nombre de plaques initial
    private Plate[] plates;           //  Plaques initiales et intermédiaires
    private Line[] lines;             //  Lignes de résultat intermédiaire
    private SolutionText[] solutionTexts;  //  Solutions (exactes ou rapprochées) validées
    private String[] sortTexts;       //  Sert au tri par ligne d'une solution proposée (cf type TypeSolution)
    int numLine;          //  N° de ligne de résultat intermédiaire actuelle
    int target;           //  Cible à atteindre
    int diff;             //  Ecart par rapport à la cible à égaler ou réduire
    int minLineCount;     //  Nombre de lignes des solutions actuelles, à égaler ou réduire
    int opCount;          //  Nombre total d'opérations
    int solCount;         //  Nombre de solutions exactes ou rapprochées distinctes
    boolean isExact;      //  True => Il y a au moins une solution exacte
    boolean isEnd;        //  True => C'est la fin des calculs, faute de plaques disponibles
    boolean isNewLine;    //  Une nouvelle ligne commence

    private boolean validReturnFromCalledActivity;
    private String calledActivityName;
    private StringDB stringDB;
    private String valueName;
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

        updatePlateValuesWithPlateTexts();
        updateTargetValueWithTargetText();
        saveDBPlateValuesAndTarget();
        btnPlates = null;
        stringDB.close();
        stringDB = null;
        menu = null;
    }

    @Override
    protected void onResume() {
        super.onResume();

        setupStringDB();
        plateInitCount = getDBPlateInitCount(stringDB);
        initArrays();
        setupPlateButtons();
        getDBPlateValuesAndTarget();
        updatePlateTextsWithPlateValues();
        updateTargetTextWithTargetValue();
        if (validReturnFromCalledActivity) {
            validReturnFromCalledActivity = false;
            if (calledActivityName.equals(Constants.PEKISLIB_ACTIVITIES.INPUT_BUTTONS.toString())) {
                setControlTextByName(valueName, getCurrentFromActivity(stringDB, Constants.PEKISLIB_ACTIVITIES.INPUT_BUTTONS.toString(), getPlatesTargetTableName(), getPlatesTargetValueIndex()));
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
                valueName = returnIntent.getStringExtra(Constants.ACTIVITY_EXTRA_KEYS.TITLE.toString());   //  PLATE1,2,... ou TARGET
            }
        }
    }

    private void onBtnPlateClick(String valueName) {
        launchInputButtonsActivity(valueName, getControlTextByName(valueName));
    }

    private void onBtnTargetClick(String valueName) {
        launchInputButtonsActivity(valueName, getControlTextByName(valueName));
    }

    private void onBtnNewPlatesClick() {
        int[] plateValues = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 25, 25, 50, 50, 75, 75, 100, 100};  // Distribution des 28 plaques disponibles au départ

        invalidateSolutionDisplay();
        boolean[] reservedIndexes = new boolean[plateValues.length];
        for (int i = 1; i <= plateValues.length - 1; i = i + 1) {
            reservedIndexes[i] = false;  //  Toutes les 28 plaques sont disponibles au tirage
        }
        int index = 0;
        for (int i = 1; i <= plateInitCount; i = i + 1) {  // Remplissage des plaques
            do {
                index = (int) (Math.random() * (plateValues.length));
            } while (reservedIndexes[index]);    //  Jusqu'à trouver une plaque non encore tirée
            reservedIndexes[index] = true;
            btnPlates[i - 1].setText(String.valueOf(plateValues[index]));
        }
    }

    private void onBtnNewTargetClick() {
        invalidateSolutionDisplay();
        btnTarget.setText(String.valueOf(1 + (int) (Math.random() * (999))));  // Remplissage de la cible à trouver
    }

    private void onBtnFindSolutionsClick() {
        diff = 999999;
        minLineCount = 999999;
        opCount = 0;
        solCount = 0;
        numLine = 1;
        isExact = false;
        isEnd = false;
        isNewLine = true;

        invalidateSolutionDisplay();
        updatePlateValuesWithPlateTexts();
        updateTargetValueWithTargetText();
        while (!isEnd) {
            if (isNewLine) {
                isNewLine = false;
                getFirstPlate1And2();   //  Prendre les 2 premières plaques disponibles
            }
            int result = getResultFromNextOperator();   //
            if (lines[numLine].operator.equals(Operators.END)) {   //  Il n'y a plus d'opérateurs disponibles
                if (!getNextPlate2()) {   //  Essayer la plaque suivant la 2e plaque actuelle
                    if (!getNextPlate1And2()) {   //  Essayer les 2 plaques suivant les 2 plaques actuelles
                        if (numLine > 1)
                            numLine = numLine - 1;    //  Faute de plaques pour cette ligne, on revient à la ligne précédente
                        else isEnd = true;
                    }
                }
                continue;   // Reprendre à while(!isEnd)
            }
            plates[plateInitCount + numLine].value = result;     //  Le résultat de la ligne est une nouvelle plaque disponible
            plates[plateInitCount + numLine].used = false;
            opCount = opCount + 1;
            if (isSolution(result)) {
                SolutionText solutionText = getSolutionText();   //  Créer le texte complet de la solution proposée
                if (isUnique(solutionText)) {          //  Les solutions précédentes sont différentes => La proposition de solution est validée
                    register(solutionText);
                }
            }
            if (numLine < (plateInitCount - 1)) {    //  Une nouvelle ligne est possible
                numLine = numLine + 1;    //  Nouvelle ligne de résultat intermédiaire
                isNewLine = true;
            }
        }
        publishResults();   //  Publier toutes les solutions exactes ou (à défaut) rapprochées validées
    }

    private void saveDBPlateValuesAndTarget() {
        for (int i = 1; i <= plateInitCount; i = i + 1) {
            saveDBPlateValueRow(stringDB, plateValueToPlateValueRow(plates[i].value, i));
        }
        saveDBTargetValueRow(stringDB, targetToTargetRow(target));
    }

    private void getDBPlateValuesAndTarget() {
        for (int i = 1; i <= plateInitCount; i = i + 1) {
            plates[i].value = plateValueRowToPlateValue(getDBPlateValueRow(stringDB, i));
        }
        target = targetRowToTarget(getDBTargetValueRow(stringDB));
    }

    private void initArrays() {
        plates = new Plate[2 * plateInitCount]; //  => OK 1..2*plateCount-1  (cad plateCount plaques initiales + (plateCount-1) plaques de résultat intermédiaire
        lines = new Line[plateInitCount];       //  => OK 1..plateCount-1
        sortTexts = new String[plateInitCount];  // => OK 1..plateCount-1   Sert au tri par ligne d'une solution proposée (cf type TypeSolution)
        solutionTexts = new SolutionText[SOLUTION_COUNT_TEMP_MAX];   // => OK 1..SOLUTION_COUNT_TEMP_MAX-1

        for (int i = 1; i <= (2 * plateInitCount - 1); i = i + 1) {   //  Instanciation de chaque élément
            plates[i] = new Plate();
        }
        for (int i = 1; i <= (plateInitCount - 1); i = i + 1) {
            lines[i] = new Line();
        }
        for (int i = 1; i <= (SOLUTION_COUNT_TEMP_MAX - 1); i = i + 1) {
            solutionTexts[i] = new SolutionText();
        }
    }

    private String getControlTextByName(String valueName) {
        String controlText = "";
        if (valueName.equals(targetIDPrefix)) {
            controlText = btnTarget.getText().toString();
        }
        if (valueName.startsWith(plateIDPrefix)) {
            int numPlate = Integer.parseInt(valueName.substring(plateIDPrefix.length()));  // N° de plaque
            controlText = btnPlates[numPlate - 1].getText().toString();
        }
        return controlText;
    }

    private void setControlTextByName(String valueName, String value) {
        try {
            int val = Integer.parseInt(value);    // Le nombre sélectionné
            String sVal = String.valueOf(val);    // Assurer un bon format p.ex. "0005"->"5"
            if (valueName.startsWith(plateIDPrefix)) {    // Plaque
                int numPlate = Integer.parseInt(valueName.substring(plateIDPrefix.length()));   // N° de plaque
                if (!(sVal.equals(btnPlates[numPlate - 1].getText().toString()))) {  // Vrai changement
                    btnPlates[numPlate - 1].setText(sVal);
                    invalidateSolutionDisplay();
                }
            }
            if (valueName.equals(targetIDPrefix)) {  // Cible
                if (!(sVal.equals(btnTarget.getText().toString()))) {   // Vrai changement
                    btnTarget.setText(sVal);
                    invalidateSolutionDisplay();
                }
            }
        } catch (NumberFormatException e) {
            msgBox("ERROR: Invalid number", this);
        }
    }

    private void updatePlateValuesWithPlateTexts() {
        for (int i = 1; i <= plateInitCount; i = i + 1) {
            try {
                plates[i].value = Integer.parseInt(btnPlates[i - 1].getText().toString());    // Valeur de la plaque
                plates[i].used = false;    //  Disponible
                btnPlates[i - 1].setText(String.valueOf(plates[i].value));    //  Normalisé
            } catch (NumberFormatException ex) {   // KO
                msgBox("ERROR: Invalid Plate value " + i, this);
            }
        }
    }

    private void updateTargetValueWithTargetText() {
        try {
            target = Integer.parseInt(btnTarget.getText().toString());   // Valeur de la cible
            btnTarget.setText(String.valueOf(target));   //  Normalisé
        } catch (NumberFormatException ex) {   // KO
            msgBox("ERROR: Invalid Target value", this);
        }
    }

    private void updatePlateTextsWithPlateValues() {
        for (int i = 1; i <= plateInitCount; i = i + 1) {
            btnPlates[i - 1].setText(String.valueOf(plates[i].value));
        }
    }

    private void updateTargetTextWithTargetValue() {
        btnTarget.setText(String.valueOf(target));
    }

    int selectNextFreePlateNumber(int numPlate) {   // Sélectionner la 1e plaque disponible après le n° d'ordre numPlate
        int nextFreePlateNumber = 0;   // Retourner 0 si aucune plaque disponible
        int i = numPlate;
        while (i < (plateInitCount + numLine - 1)) {   // Pour la ligne numLine, les plaques à vérifier vont de 1 à plateCount+numLine-1
            i = i + 1;
            if (!plates[i].used) {
                plates[i].used = true;   // Sélectionner la plaque si disponible
                nextFreePlateNumber = i;
                break;
            }
        }
        return nextFreePlateNumber;
    }

    int selectFirstFreePlateNumber() {   // Sélectionner la 1ère plaque disponible
        return selectNextFreePlateNumber(0);   //  cad le 1er n° disponible après le n° 0
    }

    private void getFirstPlate1And2() {    //  Pour la ligne numLine (1..plateCount-1), plateCount-numLine+1 plaques sont disponibles sur un total de plateCount+numLine-1 plaques
        lines[numLine].numPlate1 = selectFirstFreePlateNumber();   // Prendre les 2 premières plaques disponibles (il y en a toujours au moins 2)
        lines[numLine].numPlate2 = selectNextFreePlateNumber(lines[numLine].numPlate1);  //  La 2e plaque doit toujours suivre la 1e
        lines[numLine].operator = Operators.BEGIN;
    }

    private int getResultFromNextOperator() {
        int result = 0;
        int valPlate1 = plates[lines[numLine].numPlate1].value;
        int valPlate2 = plates[lines[numLine].numPlate2].value;
        lines[numLine].ordered = true;
        Operators operator = lines[numLine].operator.getNext();  //  2 plaques sont prêtes, passer à l'opérateur suivant
        switch (operator) {
            case ADD:
                if ((valPlate1 == 0) || (valPlate2 == 0))
                    operator = Operators.END;   //  Les autres opérateurs ne seront pas examinés
                else result = valPlate1 + valPlate2;
                break;
            case SUB:
                if (valPlate1 == valPlate2)
                    operator = Operators.MUL;   //  On ne sait rien faire avec un résultat 0; Passer à MUL => Pas de break
                else {
                    if (valPlate1 < valPlate2) {
                        lines[numLine].ordered = false;
                        result = valPlate2 - valPlate1;
                    } else result = valPlate1 - valPlate2;
                    break;
                }
            case MUL:
                if ((valPlate1 == 1) || (valPlate2 == 1))
                    operator = Operators.END;   //  La division ne sera pas examinée
                else result = valPlate1 * valPlate2;
                break;
            case DIV:
                if (valPlate1 < valPlate2) {
                    if ((valPlate2 % valPlate1) != 0)
                        operator = Operators.END;    // La division doit être juste
                    else {
                        lines[numLine].ordered = false;
                        result = valPlate2 / valPlate1;
                    }
                } else {
                    if ((valPlate1 % valPlate2) != 0)
                        operator = Operators.END;  // La division doit être juste
                    else result = valPlate1 / valPlate2;
                }
                break;
        }
        lines[numLine].operator = operator;
        return result;
    }

    private boolean getNextPlate2() {
        boolean getNextPlate2 = false;
        int numPlate2 = lines[numLine].numPlate2;
        plates[numPlate2].used = false;   //  La 2e plaque actuelle redevient disponible
        numPlate2 = selectNextFreePlateNumber(numPlate2);
        if (numPlate2 != 0) {   // OK, on l'essaye en gardant la même 1e plaque
            lines[numLine].numPlate2 = numPlate2;
            lines[numLine].operator = Operators.BEGIN;
            getNextPlate2 = true;
        }
        return getNextPlate2;
    }

    private boolean getNextPlate1And2() {
        boolean getNextPlate1And2 = false;
        int numPlate1 = lines[numLine].numPlate1;
        int numPlate2 = lines[numLine].numPlate2;
        plates[numPlate1].used = false;   //  Les 2 plaques actuelles redeviennent disponibles
        plates[numPlate2].used = false;
        numPlate1 = selectNextFreePlateNumber(numPlate1);
        numPlate2 = selectNextFreePlateNumber(numPlate1);
        if ((numPlate1 != 0) && (numPlate2 != 0)) {   // OK, on les essaye
            lines[numLine].numPlate1 = numPlate1;
            lines[numLine].numPlate2 = numPlate2;
            lines[numLine].operator = Operators.BEGIN;
            getNextPlate1And2 = true;
        }
        return getNextPlate1And2;
    }

    private boolean isSolution(int result) {
        boolean isSolution = false;
        if (Math.abs(result - target) < diff) {     //  Résultat plus proche de la cible
            diff = Math.abs(result - target);
            if (diff == 0) isExact = true;          //  Solution exacte trouvée !
            solCount = 0;                           //  Effacer toutes les solutions précédentes
            minLineCount = 999999;
            isSolution = true;
        } else {  //  Résultat identique ou plus écarté de la cible
            if (Math.abs(result - target) == diff) {   //  Résultat identique
                if (numLine < minLineCount) {          //  Nombre de lignes plus petit
                    minLineCount = numLine;
                    solCount = 0;                      //  Effacer toutes les solutions précédentes
                    isSolution = true;
                } else {   //  Nombre de lignes identique ou plus grand
                    if (numLine == minLineCount)
                        isSolution = true;      //  Nombre de lignes identique
                }
            }
        }
        return isSolution;
    }

    private SolutionText getSolutionText() {
        String pubText = "";
        for (int i = 1; i <= numLine; i = i + 1) { // Construire tout le texte de la proposition de solution
            int valPlate1 = plates[lines[i].numPlate1].value;
            int valPlate2 = plates[lines[i].numPlate2].value;
            if (!lines[i].ordered) {   // Inverser les plaques
                int temp = valPlate1;
                valPlate1 = valPlate2;
                valPlate2 = temp;
            }
            pubText = pubText + " " + valPlate1 + " " + lines[i].operator.TEXT() + " " + valPlate2 + " = " + plates[plateInitCount + i].value + "\n";   //  Texte publiable de la proposition de solution:
            if (valPlate1 > valPlate2) {   // Commencer par la plus petite plaque dans chaque ligne de cette proposition de solution, avant tri par ligne (Cf type TypeSolution)
                int temp = valPlate1;
                valPlate1 = valPlate2;
                valPlate2 = temp;
            }
            sortTexts[i] = "$" + valPlate1 + ";" + lines[i].operator.TEXT() + ";" + valPlate2 + ";" + plates[plateInitCount + i].value;
        }
        java.util.Arrays.sort(sortTexts, 1, numLine);  // Trier la solution (préparée) par ligne (Cf type TypeSolution)
        String sortText = "";
        for (int i = 1; i <= numLine; i = i + 1) {   // Texte trié de la proposition de solution
            sortText = sortText + sortTexts[i];
        }
        SolutionText solutionText = new SolutionText();
        solutionText.pub = pubText;
        solutionText.sorted = sortText;
        return solutionText;
    }

    private boolean isUnique(SolutionText solutionText) {
        boolean isUnique = true;
        if (solCount > 0) {
            for (int i = 1; i <= solCount; i = i + 1) {
                if (solutionTexts[i].sorted.equals(solutionText.sorted)) { // Une solution précédente est identique => Proposition de solution rejetée
                    isUnique = false;
                    break;
                }
            }
        }
        return isUnique;
    }

    private void register(SolutionText solutionText) {
        solCount = solCount + 1;
        if ((solCount % SOLUTION_COUNT_TEMP_MAX) == 0)
            redimSolutionTexts();   //  Espace maximum atteint pour les solutions
        solutionTexts[solCount] = solutionText;
    }

    private void redimSolutionTexts() {
        int solCountMax = (solCount / SOLUTION_COUNT_TEMP_MAX + 1) * SOLUTION_COUNT_TEMP_MAX;  // Par paquet de SOLUTION_COUNT_TEMP_MAX solutions
        SolutionText solutionsTextsTemp[] = new SolutionText[solCountMax];
        for (int i = 1; i <= (solCountMax - 1); i = i + 1) {   // Instanciation
            solutionsTextsTemp[i] = new SolutionText();
        }
        for (int i = 1; i <= (solCount - 1); i = i + 1) {   // Recopiage de l'existant
            solutionsTextsTemp[i].pub = solutionTexts[i].pub;
            solutionsTextsTemp[i].sorted = solutionTexts[i].sorted;
        }
        solutionTexts = solutionsTextsTemp;   // Réaffectation
        solutionsTextsTemp = null;
    }

    private void publishResults() {
        if (solCount > 0) {
            solutionTexts[0] = new SolutionText();
            solutionTexts[0].pub = " " + solCount + (isExact ? "" : " nearly") + " exact solution" + (solCount > 1 ? "s" : "") + " in " + minLineCount + " line" + (minLineCount > 1 ? "s" : "") + " (optimum)" + "\n";
            solutionTexts[0].pub = solutionTexts[0].pub + " after " + opCount + " operation" + (opCount > 1 ? "s" : "") + "\n";
            String s = "";
            for (int i = 0; i <= solCount; i = i + 1) {
                s = s + solutionTexts[i].pub + "*************************" + "\n";
            }
            txvSolutions.setText(s);
            txvSolutions.scrollTo(0, 0);
        }
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
        btnNewPlates = findViewById(R.id.BTN_NEW_PLATES);
        btnNewTarget = findViewById(R.id.BTN_NEW_TARGET);
        btnFindSolutions = findViewById(R.id.BTN_FIND_SOLUTIONS);
        txvSolutions = findViewById(R.id.TXV_SOL);
        txvSolutions.setMovementMethod(new ScrollingMovementMethod());
        btnTarget.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBtnTargetClick(targetIDPrefix);
            }
        });
        btnNewPlates.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onBtnNewPlatesClick();
            }
        });
        btnNewTarget.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onBtnNewTargetClick();
            }
        });
        btnFindSolutions.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onBtnFindSolutionsClick();
            }
        });
    }

    private void setupPlateButtons() {
        Class rid = R.id.class;
        btnPlates = new Button[plateInitCount];
        for (int i = 1; i <= plateInitCount; i = i + 1) {    // Récupération des boutons Plaque du layout XML
            try {
                btnPlates[i - 1] = findViewById(rid.getField("BTN_PLATE" + i).getInt(rid));   //  BTN_PLATE1, BTN_PLATE2, ...
            } catch (NoSuchFieldException | IllegalArgumentException | SecurityException | IllegalAccessException ex) {
                Logger.getLogger(MainActivity.class.getName()).log(Level.SEVERE, null, ex);
            }
            final int ind = i;
            btnPlates[i - 1].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onBtnPlateClick(plateIDPrefix + ind);  // PLATE1, PLATE2, ...);
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
        if (!stringDB.tableExists(getPlatesTargetTableName())) {
            createLCEBTableIfNotExists(stringDB, getPlatesTargetTableName());
            initializeTablePlatesTarget(stringDB);
        }
    }

    private void launchHelpActivity() {
        Intent callingIntent = new Intent(this, HelpActivity.class);
        callingIntent.putExtra(Constants.ACTIVITY_EXTRA_KEYS.TITLE.toString(), HELP_ACTIVITY_TITLE);
        callingIntent.putExtra(HelpActivity.HELP_ACTIVITY_EXTRA_KEYS.HTML_ID.toString(), R.raw.helpmainactivity);
        startActivity(callingIntent);
    }

    private void launchInputButtonsActivity(String valueName, String value) {
        setCurrentForActivity(stringDB, Constants.PEKISLIB_ACTIVITIES.INPUT_BUTTONS.toString(), getPlatesTargetTableName(), getPlatesTargetValueIndex(), value);
        setStartStatusOfActivity(stringDB, Constants.PEKISLIB_ACTIVITIES.INPUT_BUTTONS.toString(), StringDBTables.ACTIVITY_START_STATUS.COLD);
        Intent callingIntent = new Intent(this, InputButtonsActivity.class);
        callingIntent.putExtra(StringDBTables.TABLE_EXTRA_KEYS.TABLE.toString(), getPlatesTargetTableName());
        callingIntent.putExtra(StringDBTables.TABLE_EXTRA_KEYS.INDEX.toString(), getPlatesTargetValueIndex());
        callingIntent.putExtra(Constants.ACTIVITY_EXTRA_KEYS.TITLE.toString(), valueName);
        startActivityForResult(callingIntent, Constants.PEKISLIB_ACTIVITIES.INPUT_BUTTONS.INDEX());
    }
}