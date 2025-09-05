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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

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
    private final ListView<Recipe> recipeList = new ListView<>();
    private final ListView<AutoRecipeGenerator.Generated> generatedList = new ListView<>();
    // Generator controls
    private final Spinner<Integer> genServingsSpinner = new Spinner<>(1, 20, 4);
    private final Spinner<Integer> genLimitSpinner = new Spinner<>(0, 500, 10); // 0 = unlimited
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
    // Ensure image placeholders exist for quick visual sanity checks (dev convenience)
    ensureDefaultImages();
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

        Button importCsv = new Button("Import Pantry CSV…");
        importCsv.setOnAction(e -> importPantryCSV());

        Button exportCsv = new Button("Export Pantry CSV…");
        exportCsv.setOnAction(e -> exportPantryCSV());

    Button seed = new Button("Seed Sample Pantry");
    seed.setOnAction(e -> seedSamplePantry());

    Button undoBtn = new Button("Undo");
    undoBtn.setOnAction(e -> doUndo());
    ToolBar tb = new ToolBar(add, del, undoBtn, new Separator(), save, importCsv, exportCsv, new Separator(), seed);
        BorderPane box = new BorderPane(pantryTable);
        box.setTop(tb);
        t.setContent(box);
        return t;
    }

    private void seedSamplePantry() {
        // Simple seed set; units align with current Units defaults (g, ml, piece)
        List<PantryItem> demo = List.of(
                new PantryItem("Onions", 2, "piece", java.time.LocalDate.now().plusDays(2)),
                new PantryItem("bell peppers", 1, "piece", java.time.LocalDate.now().plusDays(5)),
                new PantryItem("beef mince", 450, "g", java.time.LocalDate.now().plusDays(3)),
                new PantryItem("rice", 2, "cup", null),
                new PantryItem("tortilla", 8, "piece", java.time.LocalDate.now().plusDays(14)),
                new PantryItem("soy sauce", 100, "ml", null),
                new PantryItem("egg", 6, "piece", java.time.LocalDate.now().plusDays(20)),
                new PantryItem("broth", 500, "ml", java.time.LocalDate.now().plusDays(30)),
                new PantryItem("garlic", 4, "piece", java.time.LocalDate.now().plusDays(10)),
                new PantryItem("cheddar", 150, "g", java.time.LocalDate.now().plusDays(12))
        );
        for (PantryItem p : demo) pantry.put(p.name.toLowerCase(), p);
        refreshPantryTable();
        updateShoppingList();
        showInfo("Seeded", "Added sample pantry items.");
    }

    private void showAddItemDialog() {
        Dialog<PantryItem> d = new Dialog<>();
        d.setTitle("Add Pantry Item");
        GridPane g = new GridPane(); g.setHgap(8); g.setVgap(8); g.setPadding(new Insets(8));
    TextField name = new TextField(); name.setPromptText("e.g., spinach");
        TextField qty = new TextField("1");
    ComboBox<String> unit = new ComboBox<>(FXCollections.observableArrayList(
            "g","kg","oz","lb",
            "ml","l","tsp","tbsp","floz","cup","pint","quart","gallon",
            "piece","can","egg","clove"));
        unit.setEditable(true);
        unit.setValue("piece");
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
            String u = unit.getValue();
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

    // Import / Export pantry as CSV (name,quantity,unit,expires)
    public void importPantryCSV() {
        try {
            FileChooser fc = new FileChooser();
            fc.setTitle("Import Pantry CSV");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
            File f = fc.showOpenDialog(getWindow()); if (f == null) return;
            List<String> lines = java.nio.file.Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
            if (lines.isEmpty()) { showInfo("Import", "File is empty."); return; }
            Map<String, PantryItem> loaded = new LinkedHashMap<>();
            boolean first = true;
            for (String line : lines) {
                if (line == null) continue; line = line.trim(); if (line.isEmpty()) continue;
                if (first && line.toLowerCase(Locale.ROOT).startsWith("name,")) { first = false; continue; }
                first = false;
                String[] parts = parseCsvLine(line);
                String name = parts.length>0 ? parts[0].trim() : "";
                if (name.isEmpty()) continue;
                double qty = 1.0; try { qty = Double.parseDouble(parts.length>1? parts[1].trim() : "1"); } catch (Exception ignore) {}
                String unit = parts.length>2? parts[2].trim() : "piece";
                String exps = parts.length>3? parts[3].trim() : "";
                java.time.LocalDate d = null; if (!exps.isBlank()) try { d = java.time.LocalDate.parse(exps); } catch (Exception ignore) {}
                String nu = units.normalizeUnit(unit);
                loaded.put(name.toLowerCase(), new PantryItem(name, qty, nu, d));
            }
            pantry = loaded;
            refreshPantryTable();
            updateShoppingList();
            showInfo("Import", "Pantry loaded: " + pantry.size() + " items.");
        } catch (Exception ex) { showError(ex); }
    }

    public void exportPantryCSV() {
        try {
            FileChooser fc = new FileChooser();
            fc.setTitle("Export Pantry CSV");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
            fc.setInitialFileName("pantry.csv");
            File f = fc.showSaveDialog(getWindow()); if (f == null) return;
            try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
                w.write("name,quantity,unit,expires\n");
                java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ISO_DATE;
                for (PantryItem p : pantry.values()) {
                    String exp = p.expiresOn==null? "" : fmt.format(p.expiresOn);
                    w.write(escapeCsv(p.name)); w.write(",");
                    w.write(String.valueOf(p.quantity)); w.write(",");
                    w.write(escapeCsv(p.unit)); w.write(",");
                    w.write(escapeCsv(exp)); w.write("\n");
                }
            }
        } catch (Exception ex) { showError(ex); }
    }

    private static String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            s = s.replace("\"", "\"\"");
            return "\"" + s + "\"";
        }
        return s;
    }

    private static String[] parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i=0; i<line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i+1 < line.length() && line.charAt(i+1) == '"') { cur.append('"'); i++; }
                    else { inQuotes = false; }
                } else {
                    cur.append(c);
                }
            } else {
                if (c == '"') { inQuotes = true; }
                else if (c == ',') { out.add(cur.toString()); cur.setLength(0); }
                else { cur.append(c); }
            }
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
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

    // Configure generator controls
    genLimitSpinner.setEditable(true);
    genLimitSpinner.setTooltip(new Tooltip("0 = unlimited"));

    Button genBtn = new Button("Generate from Pantry");
        genBtn.setOnAction(e -> {
            try {
                AutoRecipeGenerator gen = new AutoRecipeGenerator();
        int servings = Math.max(1, genServingsSpinner.getValue());
        int limit = genLimitSpinner.getValue(); // 0 => unlimited
        List<AutoRecipeGenerator.Generated> gens = gen.generateDetailed(pantry, servings, limit);
                if (gens.isEmpty()) { showInfo("No ideas", "Not enough pantry variety to auto-generate meals."); return; }
                generatedList.setItems(FXCollections.observableArrayList(gens));
                // Feed generated recipes into planner source
                recipes = gens.stream().map(g -> g.recipe).collect(Collectors.toList());
                refreshRecipesList();
            } catch (Exception ex) { showError(ex); }
        });

        // Show "Title (Xm)" for each recipe
        recipeList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Recipe r, boolean empty) {
                super.updateItem(r, empty);
                setText(empty || r==null ? null : r.title + " (" + r.cookMinutes + "m)");
            }
        });
        // Generated list renderer + double-click handler
        generatedList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(AutoRecipeGenerator.Generated g, boolean empty) {
                super.updateItem(g, empty);
                setText(empty || g==null ? null : g.recipe.title + " (" + g.recipe.cookMinutes + "m)");
            }
        });
        generatedList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                AutoRecipeGenerator.Generated sel = generatedList.getSelectionModel().getSelectedItem();
                if (sel != null) showGeneratedRecipe(sel);
            }
        });
        // Open details on double-click
        recipeList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                Recipe sel = recipeList.getSelectionModel().getSelectedItem();
                if (sel != null) showRecipeDetails(sel);
            }
        });

    SplitPane lists = new SplitPane(recipeList, generatedList);
        lists.setDividerPositions(0.5);
    BorderPane box = new BorderPane(lists);
    box.setTop(new ToolBar(
        importBtn,
        new Separator(),
        new Label("Servings"), genServingsSpinner,
        new Label("Limit"), genLimitSpinner,
        genBtn,
        new Separator(),
        new Label("(Left: Imported · Right: Generated)")
    ));
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

    Button consolidated = new Button("Consolidated (Selected)");
    consolidated.setOnAction(e -> showConsolidatedShoppingDialog());

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
                refresh, copy, export, new Separator(), consolidated,
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

    private void showConsolidatedShoppingDialog() {
        // If specific recipes are selected in the plan list, use those; else use full plan
        List<Integer> idxs = new ArrayList<>(planList.getSelectionModel().getSelectedIndices());
        List<Recipe> subset;
        if (idxs.isEmpty()) subset = lastPlan == null ? List.of() : lastPlan;
        else {
            subset = new ArrayList<>();
            for (int i : idxs) if (i >= 0 && lastPlan != null && i < lastPlan.size()) subset.add(lastPlan.get(i));
        }
        if (subset == null || subset.isEmpty()) { showInfo("No recipes", "Select recipes in the Plan tab or create a plan first."); return; }
        List<ShoppingListService.Line> lines = shopping.compute(subset, pantry, units, aliases);
        int mult = Math.max(1, servingsSpinner.getValue());
        if (mult != 1) lines = shopping.scale(lines, mult);
        if (lines.isEmpty()) { showInfo("Shopping List", "You're all set — nothing missing for the chosen recipes!"); return; }
        StringBuilder sb = new StringBuilder();
        for (var ln : lines) sb.append(" • ").append(ln.name).append(": ").append(intOrDec(ln.amount)).append(" ").append(ln.unit).append("\n");
        TextArea ta = new TextArea(sb.toString());
        ta.setEditable(false); ta.setWrapText(true); ta.setPrefColumnCount(48); ta.setPrefRowCount(20);
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle("Consolidated Shopping List");
        dlg.getDialogPane().setContent(ta);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dlg.showAndWait();
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
            InputStream pFull = getClass().getResourceAsStream("/sample-data/pantry_full.json");
            InputStream pantryStream = (pFull != null) ? pFull : pIn;
            pantry = storage.loadPantry(pantryStream);
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
    recipeList.getItems().setAll(recipes);
    }
    private Window getWindow() { return getScene() != null ? getScene().getWindow() : null; }

    private void showError(Throwable ex) {
        ex.printStackTrace();
        Alert a = new Alert(Alert.AlertType.ERROR, String.valueOf(ex), ButtonType.OK);
        a.setHeaderText("Error"); a.showAndWait();
    }

    private void showRecipeDetails(Recipe r) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(r.title).append("\n\nIngredients:\n");
            for (var ing : r.ingredients) {
                sb.append(" • ")
                  .append(intOrDec(ing.amount))
                  .append(ing.unit==null?"":" "+ing.unit)
                  .append(" ")
                  .append(ing.name)
                  .append("\n");
            }
            // Resolve by tags, fallback to placeholder
            String ref = resolveRecipeImage(r);
            ImageView imageView = new ImageView(loadImageRef(ref));
            imageView.setPreserveRatio(true);
            imageView.setFitWidth(520);

            TextArea ta = new TextArea(sb.toString());
            ta.setEditable(false); ta.setWrapText(true);
            ta.setPrefColumnCount(60); ta.setPrefRowCount(24);

            VBox v = new VBox(10, imageView, ta);
            v.setPadding(new Insets(10));

            Dialog<Void> dlg = new Dialog<>();
            dlg.setTitle("Recipe");
            dlg.getDialogPane().setContent(v);
            dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            dlg.showAndWait();
        } catch (Exception ex) { showError(ex); }
    }

    private void showGeneratedRecipe(AutoRecipeGenerator.Generated g) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(g.recipe.title).append("\n\nIngredients:\n");
            for (var ri : g.recipe.ingredients) {
                sb.append(" • ")
                  .append(String.format(Locale.US, "%.2f %s %s", ri.amount, ri.unit, ri.name))
                  .append("\n");
            }
            sb.append("\nSteps:\n");
            int i=1; for (String step : g.steps) sb.append(" ").append(i++).append(". ").append(step).append("\n");

            String ref = resolveRecipeImage(g);
            ImageView img = new ImageView(loadImageRef(ref));
            img.setPreserveRatio(true); img.setFitWidth(520);

            TextArea ta = new TextArea(sb.toString()); ta.setEditable(false); ta.setWrapText(true);
            VBox box = new VBox(10, img, ta); box.setPadding(new Insets(10));

            Dialog<Void> d = new Dialog<>(); d.setTitle("Recipe"); d.getDialogPane().setContent(box);
            d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE); d.showAndWait();
        } catch (Exception ex) { showError(ex); }
    }

    // Helper: does generated recipe include an ingredient by exact name (case-insensitive)?
    private boolean hasIng(AutoRecipeGenerator.Generated g, String name){
        String n = name == null ? "" : name.toLowerCase();
        return g.recipe != null && g.recipe.ingredients != null &&
                g.recipe.ingredients.stream().anyMatch(ri -> ri != null && ri.name != null && ri.name.equalsIgnoreCase(n));
    }

    /** Choose a real meal photo path based on dish tags + key ingredients.
     * Prefers local resources (res:/images/...). If the resource is missing,
     * falls back to a local text card with the title + top ingredients.
     * Only respects explicit classpath images in g.imageUrl; ignores remote placeholders.
     */
    private String resolveRecipeImage(AutoRecipeGenerator.Generated g) {
        // Only respect explicit classpath images; ignore remote placeholders from the generator
        if (g.imageUrl != null && g.imageUrl.startsWith("res:")) return g.imageUrl;

        Set<String> tags = g.recipe.tags == null ? Set.of() : g.recipe.tags;

        String res = null;
        if (tags.contains("taco")) {
            if (hasIng(g, "beef mince") || hasIng(g, "ground beef")) res = "res:/images/tacos_beef.jpg";
            else if (hasIng(g, "black bean") || hasIng(g, "chickpea")) res = "res:/images/tacos_beans.jpg";
            else res = "res:/images/tacos_beans.jpg";
            return chooseResOrText(res, textLabelFor(g));
        }

        if (tags.contains("pasta")) {
            boolean tomato = hasIng(g, "tomato sauce") || hasIng(g, "canned tomato") || hasIng(g, "tomato") || hasIng(g, "tomatoes");
            boolean coconut = hasIng(g, "coconut milk");
            if (tomato && hasIng(g, "beef mince")) res = "res:/images/pasta_tomato_beef.jpg";
            else if (tomato && (hasIng(g, "chicken breast") || hasIng(g, "chicken thigh"))) res = "res:/images/pasta_tomato_chicken.jpg";
            else if (tomato) res = "res:/images/pasta_tomato_veg.jpg";
            else if (coconut) res = "res:/images/pasta_coconut_veg.jpg";
            else res = "res:/images/pasta_tomato_veg.jpg";
            return chooseResOrText(res, textLabelFor(g));
        }

        if (tags.contains("rice")) { // fried rice
            if (hasIng(g, "egg")) res = "res:/images/fried_rice_egg.jpg";
            else if (hasIng(g, "tuna")) res = "res:/images/fried_rice_tuna.jpg";
            else res = "res:/images/fried_rice_veg.jpg";
            return chooseResOrText(res, textLabelFor(g));
        }

        if (tags.contains("stir-fry")) {
            if (hasIng(g, "beef mince") || hasIng(g, "ground beef")) res = "res:/images/stir_fry_beef.jpg";
            else if (hasIng(g, "chicken breast") || hasIng(g, "chicken thigh")) res = "res:/images/stir_fry_chicken.jpg";
            else res = "res:/images/stir_fry_veg.jpg";
            return chooseResOrText(res, textLabelFor(g));
        }

        if (tags.contains("soup")) {
            res = "res:/images/soup_veg.jpg";
            return chooseResOrText(res, textLabelFor(g));
        }

        res = "res:/images/placeholder.jpg";
        return chooseResOrText(res, textLabelFor(g));
    }

    private boolean resourceExists(String resRef){
        if (resRef == null || !resRef.startsWith("res:")) return false;
        String p = resRef.substring(4);
        return getClass().getResource(p) != null;
    }
    private String chooseResOrText(String resRef, String label){
        return resourceExists(resRef) ? resRef : ("text:" + label);
    }
    private String textLabelFor(AutoRecipeGenerator.Generated g){
        String title = g.recipe == null || g.recipe.title == null ? "Recipe" : g.recipe.title;
        // pick top 4 ingredients by amount, skipping basics
        Set<String> skip = Set.of("oil","olive oil","butter","ghee","salt","pepper","water","broth");
        List<String> top = new ArrayList<>();
        if (g.recipe != null && g.recipe.ingredients != null){
            g.recipe.ingredients.stream()
                .filter(i -> i != null && i.name != null && !skip.contains(i.name.toLowerCase()))
                .sorted((a,b) -> Double.compare(b.amount, a.amount))
                .limit(4)
                .forEach(i -> top.add(i.name));
        }
        return top.isEmpty() ? title : (title + "\n" + String.join(" · ", top));
    }

    // Overload for imported recipes without imageUrl
    private String resolveRecipeImage(Recipe r) {
        Set<String> tags = r.tags == null ? Set.of() : r.tags;
        if (tags.contains("taco"))     return "res:/images/tacos.jpg";
        if (tags.contains("stir-fry")) return "res:/images/stir_fry.jpg";
        if (tags.contains("pasta"))    return "res:/images/pasta.jpg";
        if (tags.contains("rice"))     return "res:/images/fried_rice.jpg";
        if (tags.contains("soup"))     return "res:/images/soup.jpg";
        return generateRecipeImageUrl(r.title);
    }

    // Build a readable, unique image URL for a recipe title using a placeholder service; loader has offline fallback.
    private String generateRecipeImageUrl(String title) {
        String t = (title == null || title.isBlank()) ? "Recipe" : title;
        return "https://placehold.co/600x400?text=" + URLEncoder.encode(t, StandardCharsets.UTF_8);
    }


    // Load Image from a classpath ref (res:/...) or URL, with safe fallback
    private Image loadImageRef(String ref) {
        try {
            if (ref != null && ref.startsWith("res:")) {
                String path = ref.substring(4);
                var url = getClass().getResource(path);
                if (url != null) return new Image(url.toExternalForm(), true);
                // classpath resource missing → fall through to category placeholder
                String name = new File(path).getName();
                String label = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
                String text = label.replace('_', ' ');
                return generatePlaceholderImage(text, 800, 500);
            } else if (ref != null && ref.startsWith("text:")) {
                String text = ref.substring(5);
                return generatePlaceholderImage(text, 800, 500);
            } else if (ref != null && (ref.startsWith("http://") || ref.startsWith("https://"))) {
                Image remote = new Image(ref, true);
                // If remote fails, show a generic placeholder locally
                if (remote.isError()) return generatePlaceholderImage("Recipe", 800, 500);
                return remote;
            }
        } catch (Exception ignore) { }
        // ultimate fallback placeholder
        return generatePlaceholderImage("Recipe", 800, 500);
    }

    private Image generatePlaceholderImage(String label, int w, int h) {
        try {
            Canvas canvas = new Canvas(w, h);
            GraphicsContext g = canvas.getGraphicsContext2D();
            // Background
            g.setFill(Color.web("#e0e7ff"));
            g.fillRect(0, 0, w, h);
            // Border
            g.setStroke(Color.web("#94a3b8"));
            g.setLineWidth(2);
            g.strokeRect(3, 3, w-6, h-6);
            // Text
            String text = (label == null || label.isBlank()) ? "Recipe" : label;
            String[] lines = text.split("\\n");
            g.setFill(Color.web("#0f172a"));
            double margin = 24;
            double maxWidth = w - margin*2;
            // Draw title larger, subsequent lines smaller
            double y = margin * 2;
            for (int idx=0; idx<lines.length; idx++){
                String ln = lines[idx];
                double baseSize = idx==0 ? 36 : 20;
                // Simple downscale to fit width
                double size = baseSize;
                g.setFont(Font.font("System", size));
                while (computeTextWidthApprox(ln, size) > maxWidth && size > 12) {
                    size -= 1.0; g.setFont(Font.font("System", size));
                }
                // Wrap long ingredient lines by words
                for (String wrapped : wrapLine(ln, size, maxWidth)){
                    double tx = (w - Math.min(maxWidth, computeTextWidthApprox(wrapped, size))) / 2.0;
                    g.fillText(wrapped, tx, y);
                    y += size + 8;
                }
                y += (idx==0 ? 8 : 0);
            }
            WritableImage img = new WritableImage(w, h);
            return canvas.snapshot(null, img);
        } catch (Exception e) {
            WritableImage img = new WritableImage(1, 1);
            return img;
        }
    }

    // Approximate width since we don't have FontMetrics; good enough for centering
    private double computeTextWidthApprox(String s, double fontSize){
        if (s == null) return 0;
        return fontSize * 0.55 * s.length();
    }
    private List<String> wrapLine(String s, double fontSize, double maxWidth){
        List<String> out = new ArrayList<>();
        if (s == null || s.isBlank()) { out.add(""); return out; }
        String[] words = s.split(" ");
        StringBuilder cur = new StringBuilder();
        for (String w : words){
            String trial = cur.length()==0? w : cur + " " + w;
            if (computeTextWidthApprox(trial, fontSize) > maxWidth && cur.length()>0){
                out.add(cur.toString());
                cur.setLength(0);
                cur.append(w);
            } else {
                cur.setLength(0);
                cur.append(trial);
            }
        }
        if (cur.length()>0) out.add(cur.toString());
        return out;
    }
    private void showInfo(String header, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.setHeaderText(header); a.showAndWait();
    }

    private void saveSettingsQuiet() {
        try { settingsStorage.save(settings); } catch (Exception ignore) {}
    }

    // Developer convenience: create placeholder jpgs on first run if missing
    private void ensureDefaultImages() {
        try {
            String base = System.getProperty("user.dir");
            File imgDir = new File(base, "src/main/resources/images");
            if (!imgDir.exists()) return; // don't create dirs automatically
            String[] names = new String[] {
                "tacos_beef.jpg","tacos_beans.jpg",
                "pasta_tomato_beef.jpg","pasta_tomato_chicken.jpg","pasta_tomato_veg.jpg","pasta_coconut_veg.jpg",
                "fried_rice_egg.jpg","fried_rice_tuna.jpg","fried_rice_veg.jpg",
                "stir_fry_beef.jpg","stir_fry_chicken.jpg","stir_fry_veg.jpg",
                "soup_veg.jpg","placeholder.jpg"
            };
            for (String n : names) {
                File f = new File(imgDir, n);
                if (!f.exists()) savePlaceholderJpg(f, n.replace('_',' ').replace(".jpg",""));
            }
        } catch (Exception ignore) {}
    }

    private void savePlaceholderJpg(File file, String label) {
        try {
            Image fxImg = generatePlaceholderImage(label, 800, 500);
            java.awt.image.BufferedImage bimg = javafx.embed.swing.SwingFXUtils.fromFXImage(fxImg, null);
            file.getParentFile().mkdirs();
            javax.imageio.ImageIO.write(bimg, "jpg", file);
        } catch (Exception ignore) {}
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

