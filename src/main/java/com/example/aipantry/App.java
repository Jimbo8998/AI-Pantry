package com.example.aipantry;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.layout.BorderPane;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCombination;
import javafx.geometry.Insets;
import com.example.aipantry.ui.MainView;

public class App extends Application {
    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        var main = new MainView();
        root.setTop(buildMenu(main));
        root.setCenter(main);
        BorderPane.setMargin(main, new Insets(8));
        Scene scene = new Scene(root, 1000, 650);
        stage.setTitle("AI Pantry");
        stage.setScene(scene);
        stage.show();
    }

    private MenuBar buildMenu(MainView main) {
        Menu file = new Menu("File");
    MenuItem load = new MenuItem("Load Sample Data"); load.setOnAction(e -> main.loadSampleData());
    MenuItem openPantry = new MenuItem("Open Pantry JSON…"); openPantry.setOnAction(e -> main.openPantryJson());
    MenuItem openRecipes = new MenuItem("Open Recipes JSON…"); openRecipes.setOnAction(e -> main.openRecipesJson());
    MenuItem export = new MenuItem("Export Shopping List (CSV)"); export.setOnAction(e -> main.exportShoppingListCSV());
        MenuItem exportPlan = new MenuItem("Export Plan (CSV)"); exportPlan.setOnAction(e -> main.exportPlanCSV());
        MenuItem printPlan = new MenuItem("Print Plan"); printPlan.setOnAction(e -> main.printPlan());
        MenuItem exit = new MenuItem("Exit"); exit.setAccelerator(KeyCombination.keyCombination("Ctrl+Q")); exit.setOnAction(e -> System.exit(0));
        file.getItems().addAll(load, openPantry, openRecipes, export, exportPlan, printPlan, new SeparatorMenuItem(), exit);

        Menu plan = new Menu("Plan");
    MenuItem plan3 = new MenuItem("Plan 3 Meals (Quick)"); plan3.setOnAction(e -> main.planMeals(3, 30));
    MenuItem planNow = new MenuItem("Plan (use controls)"); planNow.setOnAction(e -> main.planWithControlsAsync());
    plan.getItems().addAll(plan3, planNow);

        return new MenuBar(file, plan);
    }

    public static void main(String[] args) { launch(args); }
}
