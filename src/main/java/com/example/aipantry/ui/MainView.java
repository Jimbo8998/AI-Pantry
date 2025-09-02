package com.example.aipantry.ui;

import javafx.scene.layout.BorderPane;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.geometry.Insets;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.control.Separator;
import javafx.scene.control.ProgressBar;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.net.URLEncoder;
import java.net.URI;
import java.awt.Desktop;

import com.example.aipantry.model.*;
import com.example.aipantry.services.*;
import com.example.aipantry.storage.JsonStorage;
import com.example.aipantry.storage.Settings;
import com.example.aipantry.storage.SettingsStorage;

public class MainView extends BorderPane {

    private final TableView<PantryItem> pantryTable = new TableView<>();
    private final ListView<String> recipeList = new ListView<>();
    private final ListView<String> planList = new ListView<>();
    private final TableView<ShoppingListService.Line> shoppingTable = new TableView<>();

    private final JsonStorage storage = new JsonStorage();
    private Map<String, PantryItem> pantry = new LinkedHashMap<>();
    private List<Recipe> recipes = new ArrayList<>();
    private AliasResolver aliases = new AliasResolver(Map.of());
    private Units units = new Units(Map.of(
        "g", Map.of("to_g", 1.0),
        "piece", Map.of("to_piece", 1.0),
        "ml", Map.of("to_ml", 1.0)
    ));
    private final Planner planner = new Planner();
    private final ShoppingListService shopping = new ShoppingListService();
    private final RuleEngine engine = new RuleEngine();
    private List<Recipe> lastPlan = new ArrayList<>();
    private List<ShoppingListService.Line> lastShopping = new ArrayList<>();
    private final SettingsStorage settingsStorage = new SettingsStorage();
    private Settings settings = new Settings();
    private JsonStorage.Aisles aisles = new JsonStorage.Aisles();
    private final java.util.Deque<Runnable> undoStack = new java.util.ArrayDeque<>();

    // Plan controls
    private final Spinner<Integer> mealsSpinner = new Spinner<>(1, 14, 3);
    private final Spinner<Integer> maxMinSpinner = new Spinner<>(5, 240, 30);
    private final TextField tagsField = new TextField();

    // Explain panel controls
    private final Label explTitle = new Label("Select a recipe to see why it was chosen");
    private final ProgressBar scoreBar = new ProgressBar(0);
    private final Label scoreLabel = new Label("Score: –");
    private final Label coverageLabel = new Label();
    private final Label bonusesLabel = new Label();
    private final ListView<String> missingList = new ListView<>();
    private final ListView<String> perishablesList = new ListView<>();
    private final ProgressIndicator planSpinner = new ProgressIndicator();
    private final Label shoppingStatus = new Label();

    // Store selector + servings for shopping links
    private final ComboBox<String> storeBox =
            new ComboBox<>(FXCollections.observableArrayList("Walmart", "Target", "Kroger", "Amazon"));
    private final Spinner<Integer> servingsSpinner = new Spinner<>(1, 16, 1); // multiplier
    // Recipe scaling display in Plan tab
    private final Spinner<Integer> recipeServingsSpinner = new Spinner<>(1, 16, 1);
    private final ListView<String> recipeIngredientsList = new ListView<>();

    public MainView() {
        setPadding(new Insets(8));
        storeBox.setValue("Walmart");
    planSpinner.setVisible(false);
    planSpinner.setPrefSize(24,24);
        TabPane tabs = new TabPane();
        tabs.getTabs().addAll(buildPantryTab(), buildRecipesTab(), buildPlanTab(), buildShoppingTab());
        setCenter(tabs);

        // Load persisted UI settings
        try {
            settings = settingsStorage.load();
            if (settings.store != null) storeBox.setValue(settings.store);
            if (settings.meals != null) mealsSpinner.getValueFactory().setValue(Math.max(1, Math.min(14, settings.meals)));
            if (settings.maxMinutes != null) maxMinSpinner.getValueFactory().setValue(Math.max(5, Math.min(240, settings.maxMinutes)));
            if (settings.servings != null) servingsSpinner.getValueFactory().setValue(Math.max(1, Math.min(16, settings.servings)));
        } catch (Exception ignore) {}

        // Persist UI setting changes (best-effort)
        storeBox.valueProperty().addListener((o, a, b) -> { settings.store = b; saveSettingsQuiet(); });
        mealsSpinner.valueProperty().addListener((o, a, b) -> { settings.meals = b; saveSettingsQuiet(); });
        maxMinSpinner.valueProperty().addListener((o, a, b) -> { settings.maxMinutes = b; saveSettingsQuiet(); });
        servingsSpinner.valueProperty().addListener((o, a, b) -> { settings.servings = b; saveSettingsQuiet(); });
    }

    // ---------- PANTRY TAB ----------
    private Tab buildPantryTab() {
        Tab t = new Tab("Pantry");
        t.setClosable(false);

        TableColumn<PantryItem, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().name));
        name.setCellFactory(TextFieldTableCell.forTableColumn());
        name.setOnEditCommit(evt -> {
            PantryItem p = evt.getRowValue();
            String oldKey = p.name == null ? "" : p.name.toLowerCase();
            String newName = evt.getNewValue() == null ? null : evt.getNewValue().trim();
            if (newName == null || newName.isBlank()) { showError(new IllegalArgumentException("Name cannot be blank.")); refreshPantryTable(); return; }
            String prevName = p.name;
            p.name = newName;
            pantry.remove(oldKey);
            pantry.put(p.name.toLowerCase(), p);
            pushUndo(() -> {
                pantry.remove(p.name.toLowerCase());
                p.name = prevName;
                pantry.put(prevName.toLowerCase(), p);
                refreshPantryTable();
            });
            refreshPantryTable();
        });

        TableColumn<PantryItem, String> qty = new TableColumn<>("Quantity");
        qty.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(String.valueOf(round2(c.getValue().quantity))));
        qty.setCellFactory(TextFieldTableCell.forTableColumn());
    qty.setOnEditCommit(evt -> {
            PantryItem p = evt.getRowValue();
            try {
        double val = Double.parseDouble(evt.getNewValue().trim());
                if (val < 0) throw new IllegalArgumentException("Quantity must be non-negative.");
                double prev = p.quantity;
                p.quantity = val;
                pushUndo(() -> { p.quantity = prev; refreshPantryTable(); });
            } catch (Exception ex) { showError(new IllegalArgumentException("Quantity must be a non-negative number.")); }
            refreshPantryTable();
        });

        TableColumn<PantryItem, String> unit = new TableColumn<>("Unit");
        unit.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().unit));
        unit.setCellFactory(TextFieldTableCell.forTableColumn());
        unit.setOnEditCommit(evt -> {
            String u = evt.getNewValue() == null ? null : evt.getNewValue().trim();
            if (u == null || u.isBlank()) { showError(new IllegalArgumentException("Unit cannot be blank.")); refreshPantryTable(); return; }
            String nu = units.normalizeUnit(u);
            if (!units.isKnownUnit(nu)) { showError(new IllegalArgumentException("Unknown unit: " + u)); refreshPantryTable(); return; }
            PantryItem row = evt.getRowValue();
            String prev = row.unit;
            row.unit = nu;
            pushUndo(() -> { row.unit = prev; refreshPantryTable(); });
            refreshPantryTable();
        });

        TableColumn<PantryItem, String> exp = new TableColumn<>("Expires");
        exp.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(String.valueOf(c.getValue().expiresOn)));
        exp.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setText(null); setStyle(""); return; }
                setText(item);
                PantryItem p = getTableView().getItems().get(getIndex());
                var today = java.time.LocalDate.now();
                boolean soon = p.expiresOn != null && !p.expiresOn.isBefore(today) && !p.expiresOn.isAfter(today.plusDays(2));
                boolean past = p.expiresOn != null && p.expiresOn.isBefore(today);
                if (soon || past) setStyle("-fx-text-fill: red; -fx-font-weight: bold;"); else setStyle("");
            }
        });

        pantryTable.getColumns().setAll(List.of(name, qty, unit, exp));
        pantryTable.setEditable(true);
        pantryTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        // Keyboard UX: Enter commits edits; Del deletes selected
        pantryTable.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case DELETE:
                    PantryItem sel = pantryTable.getSelectionModel().getSelectedItem();
                    if (sel != null) {
                        PantryItem snapshot = new PantryItem(sel.name, sel.quantity, sel.unit, sel.expiresOn);
                        pantry.remove(sel.name.toLowerCase());
                        pushUndo(() -> { pantry.put(snapshot.name.toLowerCase(), snapshot); refreshPantryTable(); });
                        refreshPantryTable();
                    }
                    e.consume();
                    break;
                default:
                    // fall-through: let default behavior handle Enter to commit edits
                    break;
            }
        });

        Button add = new Button("Add Item");
        add.setOnAction(e -> showAddItemDialog());

    Button del = new Button("Delete Selected");
    del.setOnAction(e -> {
            PantryItem sel = pantryTable.getSelectionModel().getSelectedItem();
            if (sel != null) {
        PantryItem snapshot = new PantryItem(sel.name, sel.quantity, sel.unit, sel.expiresOn);
        pantry.remove(sel.name.toLowerCase());
        pushUndo(() -> { pantry.put(snapshot.name.toLowerCase(), snapshot); refreshPantryTable(); });
                refreshPantryTable();
            }
        });

        Button save = new Button("Save Pantry JSON…");
        save.setOnAction(e -> {
            try {
                FileChooser fc = new FileChooser();
                fc.setTitle("Save Pantry JSON");
                fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
                File f = fc.showSaveDialog(getWindow());
                if (f != null) storage.savePantry(pantry, f);
            } catch (Exception ex) { showError(ex); }
        });

    Button undoBtn = new Button("Undo");
    undoBtn.setOnAction(e -> doUndo());
    ToolBar tb = new ToolBar(add, del, undoBtn, new Separator(), save);
        BorderPane box = new BorderPane(pantryTable);
        box.setTop(tb);
        t.setContent(box);
        return t;
    }

    private void showAddItemDialog() {
        Dialog<PantryItem> d = new Dialog<>();
        d.setTitle("Add Pantry Item");
        GridPane g = new GridPane(); g.setHgap(8); g.setVgap(8); g.setPadding(new Insets(8));
    TextField name = new TextField(); name.setPromptText("e.g., spinach");
        TextField qty = new TextField("1");
    TextField unit = new TextField("piece");
        DatePicker dp = new DatePicker();
        g.addRow(0, new Label("Name"), name);
        g.addRow(1, new Label("Quantity"), qty);
        g.addRow(2, new Label("Unit"), unit);
        g.addRow(3, new Label("Expires"), dp);
        d.getDialogPane().setContent(g);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        d.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return null;
            String n = name.getText() == null ? null : name.getText().trim();
            if (n == null || n.isBlank()) { showError(new IllegalArgumentException("Name cannot be blank.")); return null; }
            double q;
            try { q = Double.parseDouble(qty.getText().trim()); } catch (Exception ex) { showError(new IllegalArgumentException("Quantity must be a number.")); return null; }
            if (q < 0) { showError(new IllegalArgumentException("Quantity must be non-negative.")); return null; }
            String u = unit.getText() == null ? null : unit.getText().trim();
            if (u == null || u.isBlank()) { showError(new IllegalArgumentException("Unit cannot be blank.")); return null; }
            String nu = units.normalizeUnit(u);
            if (!units.isKnownUnit(nu)) { showError(new IllegalArgumentException("Unknown unit: " + u)); return null; }
            return new PantryItem(n, q, nu, dp.getValue());
        });
        d.showAndWait().ifPresent(p -> {
            if (p.name != null && !p.name.isBlank()) {
                pantry.put(p.name.toLowerCase(), p);
                refreshPantryTable();
            }
        });
    }

    // (no-op placeholder removed)

    // ---------- RECIPES TAB ----------
    private Tab buildRecipesTab() {
        Tab t = new Tab("Recipes");
        t.setClosable(false);

        Button importBtn = new Button("Import sample recipes");
        importBtn.setOnAction(e -> {
            try (InputStream in = getClass().getResourceAsStream("/sample-data/recipes.json")) {
                recipes = storage.loadRecipes(in);
                refreshRecipesList();
            } catch (Exception ex) { showError(ex); }
        });

        BorderPane box = new BorderPane(recipeList);
        box.setTop(new ToolBar(importBtn));
        t.setContent(box);
        return t;
    }

    // ---------- PLAN TAB ----------
    private Tab buildPlanTab() {
        Tab t = new Tab("Plan");
        t.setClosable(false);

        tagsField.setPromptText("tags (e.g., quick,vegetarian)");
    Button go = new Button("Plan");
        go.setOnAction(e -> {
            // Confirm if re-planning after edits
            if (lastPlan != null && !lastPlan.isEmpty()) {
                var confirm = new Alert(Alert.AlertType.CONFIRMATION, "Clear current plan and re-run?", ButtonType.OK, ButtonType.CANCEL);
                confirm.setHeaderText("Clear plan?");
                var res = confirm.showAndWait();
                if (res.isEmpty() || res.get() != ButtonType.OK) return;
            }
            planWithControlsAsync();
        });

    ToolBar bar = new ToolBar(
                new Label("Meals"), mealsSpinner,
                new Label("Max min"), maxMinSpinner,
                new Label("Tags"), tagsField,
        new Separator(), new Label("Servings"), recipeServingsSpinner,
        go
        );

        // Right explanation panel
    VBox explBox = new VBox(6,
                explTitle,
                scoreBar,
                scoreLabel,
                new Separator(),
                coverageLabel,
                bonusesLabel,
        new Label("Ingredients: \u2713 have / \u2717 missing"),
                missingList,
                new Label("Perishables used:"),
        perishablesList,
        new Label("Recipe ingredients (scaled)"),
        recipeIngredientsList,
        planSpinner
        );
        explBox.setPadding(new Insets(8));
    scoreBar.setPrefWidth(320);
    // Tooltip for score breakdown
    Tooltip.install(scoreBar, new Tooltip(""));

        SplitPane split = new SplitPane();
        split.getItems().addAll(planList, explBox);
        split.setDividerPositions(0.45);

        // When user selects a recipe in the plan, compute & show explanation
        planList.getSelectionModel().selectedIndexProperty().addListener((obs, old, idx) -> {
            int i = idx == null ? -1 : idx.intValue();
            if (i >= 0 && i < lastPlan.size()) showExplanation(lastPlan.get(i));
        });
        recipeServingsSpinner.valueProperty().addListener((o,a,b) -> {
            int i = planList.getSelectionModel().getSelectedIndex();
            if (i >= 0 && i < lastPlan.size()) showExplanation(lastPlan.get(i));
        });

        BorderPane box = new BorderPane(split);
        box.setTop(bar);
        t.setContent(box);
        return t;
    }

    private void showExplanation(Recipe r) {
        Set<String> req = Arrays.stream(tagsField.getText().split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        int maxMin = maxMinSpinner.getValue();

        RuleExplanation ex = engine.explain(r, pantry, req, maxMin);
        explTitle.setText("Why: " + r.title);
        scoreBar.setProgress(Math.max(0, Math.min(1, ex.totalScore / 100.0)));
        scoreLabel.setText(String.format("Score: %.0f / 100", ex.totalScore));
        coverageLabel.setText(String.format("Coverage: %d/%d (%.0f%%)", ex.haveCount, ex.totalIngredients, ex.coverage * 100.0));
        bonusesLabel.setText(String.format("Bonuses → Perishables: +%.0f  |  Time: +%.0f  |  Tags: +%.0f",
                ex.perishablesBonus, ex.timeBonus, ex.tagBonus));
        // Per-ingredient ticks for explain panel
        List<String> statusLines = new ArrayList<>();
        for (var ing : r.ingredients) {
            boolean have = pantry.containsKey(ing.name.toLowerCase());
            statusLines.add((have ? "✓ " : "✗ ") + ing.name + " – " + intOrDec(ing.amount) + (ing.unit == null? "" : (" " + ing.unit)));
        }
        missingList.getItems().setAll(statusLines);
        perishablesList.getItems().setAll(ex.perishablesUsed);
        // Update tooltip text with breakdown
        Tooltip tt = new Tooltip(ex.toString());
        Tooltip.install(scoreBar, tt);
        // Scaled ingredients by servings
        int m = Math.max(1, recipeServingsSpinner.getValue());
        List<String> scaled = new ArrayList<>();
        for (var ing : r.ingredients) {
            double a = ing.amount * m;
            scaled.add(intOrDec(a) + (ing.unit == null ? "" : (" " + ing.unit)) + " — " + ing.name);
        }
        recipeIngredientsList.getItems().setAll(scaled);
    }

    // ---------- SHOPPING TAB ----------
    private Tab buildShoppingTab() {
        Tab t = new Tab("Shopping List");
        t.setClosable(false);

        TableColumn<ShoppingListService.Line, String> item = new TableColumn<>("Item");
        item.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().name));
        TableColumn<ShoppingListService.Line, String> amt = new TableColumn<>("Amount");
        amt.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(String.valueOf(round2(c.getValue().amount))));
        TableColumn<ShoppingListService.Line, String> unit = new TableColumn<>("Unit");
        unit.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().unit));

        // Buy column -> hyperlink to selected store’s search
        TableColumn<ShoppingListService.Line, Void> buy = new TableColumn<>("Buy");
        buy.setCellFactory(col -> new TableCell<>() {
            private final Hyperlink link = new Hyperlink("Open");
            { link.setOnAction(e -> {
                    var ln = getTableView().getItems().get(getIndex());
                    openUrl(buildStoreSearchUrl(storeBox.getValue(), ln));
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : link);
            }
        });

    TableColumn<ShoppingListService.Line, String> aisleCol = new TableColumn<>("Aisle");
    aisleCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(categoryFor(c.getValue().name)));
    shoppingTable.getColumns().setAll(List.of(item, amt, unit, aisleCol, buy));

        Button refresh = new Button("Update from Plan");
        refresh.setOnAction(e -> updateShoppingList());

        Button copy = new Button("Copy to Clipboard");
        copy.setOnAction(e -> copyShoppingListToClipboard());

        Button export = new Button("Export CSV…");
        export.setOnAction(e -> exportShoppingListCSV());

        Button openAll = new Button("Open All in " + storeBox.getValue());
        storeBox.setOnAction(e -> openAll.setText("Open All in " + storeBox.getValue()));
        openAll.setOnAction(e -> {
            for (var ln : shoppingTable.getItems()) {
                openUrl(buildStoreSearchUrl(storeBox.getValue(), ln));
            }
        });

        // When servings change, re-scale live
        servingsSpinner.valueProperty().addListener((obs, a, b) -> updateShoppingList());

        ToolBar bar = new ToolBar(
                new Label("Store"), storeBox,
                new Separator(),
                new Label("Servings"), servingsSpinner,
                new Separator(),
                refresh, copy, export,
                new Separator(), openAll
        );

        // Status text: X recipes planned · Y items on shopping list
        shoppingTable.getItems().addListener((javafx.collections.ListChangeListener<? super ShoppingListService.Line>) c -> {
            shoppingStatus.setText((lastPlan==null?0:lastPlan.size()) + " recipes planned · " + shoppingTable.getItems().size() + " items on shopping list");
        });
        BorderPane box = new BorderPane(shoppingTable);
        box.setTop(bar);
        box.setBottom(shoppingStatus);
        t.setContent(box);
        return t;
    }

    private void updateShoppingList() {
        if (lastPlan == null || lastPlan.isEmpty()) {
            shoppingTable.getItems().clear();
            return;
        }
        // base list from recipes/pantry
        lastShopping = shopping.compute(lastPlan, pantry, units, aliases);
    // scale by servings multiplier
    int mult = Math.max(1, servingsSpinner.getValue());
    List<ShoppingListService.Line> lines = mult == 1 ? lastShopping : shopping.scale(lastShopping, mult);
    lines.sort((a,b) -> {
        int ca = aisleIndex(categoryFor(a.name));
        int cb = aisleIndex(categoryFor(b.name));
        if (ca != cb) return Integer.compare(ca, cb);
        return a.name.compareToIgnoreCase(b.name);
    });
    shoppingTable.getItems().setAll(lines);
    shoppingStatus.setText((lastPlan==null?0:lastPlan.size()) + " recipes planned · " + shoppingTable.getItems().size() + " items on shopping list");
    }

    private void copyShoppingListToClipboard() {
        if (shoppingTable.getItems().isEmpty()) return;
        StringBuilder sb = new StringBuilder("name,amount,unit\n");
        for (var ln : shoppingTable.getItems()) sb.append(ln.toString()).append("\n");
        ClipboardContent cc = new ClipboardContent(); cc.putString(sb.toString());
        Clipboard.getSystemClipboard().setContent(cc);
        showInfo("Copied", "Shopping list copied to clipboard as CSV.");
    }

    // ---------- Common ----------
    public void planMeals(int meals, int maxMinutes) {
        lastPlan = planner.plan(recipes, pantry, meals, Set.of(), maxMinutes);
        planList.getItems().setAll(lastPlan.stream()
                .map(r -> r.title + " (" + r.cookMinutes + "m)").collect(Collectors.toList()));
        if (!lastPlan.isEmpty()) {
            planList.getSelectionModel().select(0);
            showExplanation(lastPlan.get(0));
        }
        updateShoppingList();
    }

    public void loadSampleData() {
        try (InputStream pIn = getClass().getResourceAsStream("/sample-data/pantry.json");
             InputStream rIn = getClass().getResourceAsStream("/sample-data/recipes.json");
             InputStream aIn = getClass().getResourceAsStream("/sample-data/aliases.json");
             InputStream uIn = getClass().getResourceAsStream("/sample-data/units.json");
             InputStream sIn = getClass().getResourceAsStream("/sample-data/aisles.json")) {
            pantry = storage.loadPantry(pIn);
            recipes = storage.loadRecipes(rIn);
            aliases = new AliasResolver(storage.loadAliases(aIn));
            units = new Units(storage.loadUnits(uIn));
            if (sIn != null) aisles = storage.loadAisles(sIn);
            refreshPantryTable();
            refreshRecipesList();
            updateShoppingList();
        } catch (Exception ex) { showError(ex); }
    }

    public void openPantryJson() {
        try {
            FileChooser fc = new FileChooser();
            fc.setTitle("Open Pantry JSON");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
            if (settings.lastPantryPath != null) {
                File prev = new File(settings.lastPantryPath);
                if (prev.getParentFile() != null && prev.getParentFile().exists()) fc.setInitialDirectory(prev.getParentFile());
                fc.setInitialFileName(prev.getName());
            }
            File f = fc.showOpenDialog(getWindow()); if (f == null) return;
            try (InputStream in = new FileInputStream(f)) {
                pantry = storage.loadPantry(in);
            }
            settings.lastPantryPath = f.getAbsolutePath(); saveSettingsQuiet();
            refreshPantryTable();
            updateShoppingList();
        } catch (Exception ex) { showError(ex); }
    }

    public void openRecipesJson() {
        try {
            FileChooser fc = new FileChooser();
            fc.setTitle("Open Recipes JSON");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
            if (settings.lastRecipesPath != null) {
                File prev = new File(settings.lastRecipesPath);
                if (prev.getParentFile() != null && prev.getParentFile().exists()) fc.setInitialDirectory(prev.getParentFile());
                fc.setInitialFileName(prev.getName());
            }
            File f = fc.showOpenDialog(getWindow()); if (f == null) return;
            try (InputStream in = new FileInputStream(f)) {
                recipes = storage.loadRecipes(in);
            }
            settings.lastRecipesPath = f.getAbsolutePath(); saveSettingsQuiet();
            refreshRecipesList();
            updateShoppingList();
        } catch (Exception ex) { showError(ex); }
    }

    public void exportShoppingListCSV() {
        try {
            List<ShoppingListService.Line> lines = new ArrayList<>(shoppingTable.getItems());
            if (lines.isEmpty()) {
                if (lastPlan == null || lastPlan.isEmpty()) { showInfo("No plan yet", "Click Plan first, then export."); return; }
                lines = shopping.compute(lastPlan, pantry, units, aliases);
                int mult = Math.max(1, servingsSpinner.getValue());
                if (mult != 1) lines = shopping.scale(lines, mult);
                if (lines.isEmpty()) { showInfo("Nothing to export", "Your pantry already covers everything."); return; }
            }
            FileChooser fc = new FileChooser();
            fc.setTitle("Save Shopping List CSV");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
            File f = fc.showSaveDialog(getWindow()); if (f == null) return;
            try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
                w.write("name,amount,unit\n");
                for (var ln : lines) { w.write(ln.toString()); w.write("\n"); }
            }
        } catch (Exception ex) { showError(ex); }
    }

    // Export Plan (CSV)
    public void exportPlanCSV() {
        try {
            if (lastPlan == null || lastPlan.isEmpty()) { showInfo("No plan", "Nothing to export yet."); return; }
            FileChooser fc = new FileChooser();
            fc.setTitle("Save Plan CSV");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
            File f = fc.showSaveDialog(getWindow()); if (f == null) return;
            try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
                w.write("title,cook_minutes,tags\n");
                for (var r : lastPlan) {
                    String tags = String.join(";", r.tags);
                    w.write(r.title.replace(","," ") + "," + r.cookMinutes + "," + tags + "\n");
                }
            }
        } catch (Exception ex) { showError(ex); }
    }

    // Print Plan: write a minimal HTML and open default browser/print dialog
    public void printPlan() {
        try {
            if (lastPlan == null || lastPlan.isEmpty()) { showInfo("No plan", "Nothing to print yet."); return; }
            File tmp = File.createTempFile("plan", ".html");
            try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
                w.write("<html><head><meta charset='utf-8'><title>AI Pantry Plan</title></head><body>");
                w.write("<h2>Meal Plan</h2><ol>");
                for (var r : lastPlan) {
                    w.write("<li><strong>" + escapeHtml(r.title) + "</strong> (" + r.cookMinutes + "m)" );
                    if (!r.tags.isEmpty()) w.write(" – tags: " + escapeHtml(String.join(", ", r.tags)));
                    w.write("</li>");
                }
                w.write("</ol><script>window.onload=function(){window.print();};</script></body></html>");
            }
            openUrl(tmp.toURI().toString());
        } catch (Exception ex) { showError(ex); }
    }

    private String escapeHtml(String s) {
        return s==null? "" : s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }

    private void refreshPantryTable() { pantryTable.getItems().setAll(pantry.values()); }
    private void refreshRecipesList() {
        recipeList.getItems().setAll(recipes.stream()
                .map(r -> r.title + " (" + r.cookMinutes + "m)").collect(Collectors.toList()));
    }
    private Window getWindow() { return getScene() != null ? getScene().getWindow() : null; }

    private void showError(Throwable ex) {
        ex.printStackTrace();
        Alert a = new Alert(Alert.AlertType.ERROR, String.valueOf(ex), ButtonType.OK);
        a.setHeaderText("Error"); a.showAndWait();
    }
    private void showInfo(String header, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.setHeaderText(header); a.showAndWait();
    }

    private void saveSettingsQuiet() {
        try { settingsStorage.save(settings); } catch (Exception ignore) {}
    }

    // Async planning to keep UI responsive
    public void planWithControlsAsync() {
        Set<String> req = Arrays.stream(tagsField.getText().split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        int meals = mealsSpinner.getValue();
        int maxMin = maxMinSpinner.getValue();
        Task<List<Recipe>> task = new Task<>() {
            @Override protected List<Recipe> call() {
                return planner.plan(recipes, pantry, meals, req, maxMin);
            }
        };
        planSpinner.setVisible(true);
        task.setOnSucceeded(ev -> {
            planSpinner.setVisible(false);
            lastPlan = task.getValue();
            planList.getItems().setAll(lastPlan.stream().map(r -> r.title + " (" + r.cookMinutes + "m)").collect(Collectors.toList()));
            if (!lastPlan.isEmpty()) {
                planList.getSelectionModel().select(0);
                showExplanation(lastPlan.get(0));
            }
            updateShoppingList();
        });
        task.setOnFailed(ev -> {
            planSpinner.setVisible(false);
            showError(task.getException());
        });
        new Thread(task, "planner-thread").start();
    }

    // ---------- Store link helpers ----------
    private String buildStoreSearchUrl(String store, ShoppingListService.Line ln) {
        String q = ln.name + " " + normalizeAmount(ln.amount, ln.unit);
        String encoded = URLEncoder.encode(q, StandardCharsets.UTF_8);
        switch (store) {
            case "Walmart": return "https://www.walmart.com/search?q=" + encoded;
            case "Target":  return "https://www.target.com/s?searchTerm=" + encoded;
            case "Amazon":  return "https://www.amazon.com/s?k=" + encoded;
            case "Kroger":  return "https://www.kroger.com/search?query=" + encoded;
            default:        return "https://www.google.com/search?q=" + encoded;
        }
    }
    private String normalizeAmount(double amt, String unit) {
        if (unit == null || unit.isBlank()) return intOrDec(amt);
        String u = unit.toLowerCase();
        if ("g".equals(u)) {
            double oz = amt / 28.3495;
            if (oz >= 4 && oz <= 64) return intOrDec(Math.round(oz)) + " oz";
        }
        return intOrDec(amt) + " " + unit;
    }
    private String intOrDec(double d) {
        return (Math.abs(d - Math.rint(d)) < 1e-9) ? String.valueOf((int)Math.rint(d)) : String.valueOf(round2(d));
    }
    private double round2(double d){ return Math.round(d*100.0)/100.0; }
    private int aisleIndex(String cat) {
        if (aisles == null || aisles.order == null) return Integer.MAX_VALUE/2;
        int i = aisles.order.indexOf(cat);
        return i < 0 ? Integer.MAX_VALUE/2 : i;
    }
    private String categoryFor(String name) {
        if (aisles == null || aisles.map == null || name == null) return "Other";
        String key = name.toLowerCase();
        return aisles.map.getOrDefault(key, "Other");
    }
    private void openUrl(String url) {
        try {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(new URI(url));
            else showInfo("Open this link", url);
        } catch (Exception ex) { showError(ex); }
    }

    // ---------- Undo helpers ----------
    private void pushUndo(Runnable r) {
        undoStack.clear();
        undoStack.push(r);
    }
    private void doUndo() {
        if (undoStack.isEmpty()) return;
        try { undoStack.pop().run(); }
        finally { undoStack.clear(); }
    }
}

