package com.example.aipantry;

import com.example.aipantry.model.*;
import com.example.aipantry.services.*;
import java.time.LocalDate;
import java.util.*;

/** Minimal CLI demo: scores a couple of recipes against a small pantry and prints explanations. */
public class CliDemo {
    public static void main(String[] args) {
        List<PantryItem> pantryList = List.of(
            new PantryItem("Onions", 2, "pc", LocalDate.now().plusDays(2)),
            new PantryItem("bell peppers", 1, "pc", LocalDate.now().plusDays(7)),
            new PantryItem("beef mince", 500, "g", LocalDate.now().plusDays(3)),
            new PantryItem("flour", 2, "cup", LocalDate.now().plusDays(180)),
            new PantryItem("oil", 200, "ml", null)
        );
        Map<String, PantryItem> pantry = new LinkedHashMap<>();
        for (PantryItem p: pantryList) pantry.put(p.name.toLowerCase(), p);

        Recipe fajitas = new Recipe(
            "Beef Fajitas",
            List.of(
                new Ingredient("beef mince", 400, "g"),
                new Ingredient("onion", 1, "pc"),
                new Ingredient("bell pepper", 1, "pc"),
                new Ingredient("oil", 1, "tbsp")
            ),
            25,
            Set.of("mexican", "skillet")
        );

        Recipe pancakes = new Recipe(
            "Easy Pancakes",
            List.of(
                new Ingredient("flour", 1.5, "cup"),
                new Ingredient("egg", 2, "pc"),
                new Ingredient("oil", 1, "tbsp")
            ),
            15,
            Set.of("breakfast")
        );

        RuleEngine engine = new RuleEngine();
        for (Recipe r : List.of(fajitas, pancakes)) {
            RuleExplanation ex = engine.explain(r, pantry, Set.of(), 30);
            System.out.printf("%s -> total=%.1f coverage=%.0f%% time=+%.0f perishables=+%.0f tags=+%.0f missing=%s\n",
                r.title, ex.totalScore, 100*ex.coverage, ex.timeBonus, ex.perishablesBonus, ex.tagBonus, ex.missing);
        }

        System.out.println("\nTop 1 by Planner:");
        Planner planner = new Planner();
        List<Recipe> top = planner.plan(List.of(fajitas, pancakes), pantry, 1, Set.of(), 30);
        for (Recipe r : top) System.out.println(" - " + r.title + " (" + r.cookMinutes + " min)");
    }
}
