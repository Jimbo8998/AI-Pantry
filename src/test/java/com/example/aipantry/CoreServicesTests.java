package com.example.aipantry;

import com.example.aipantry.model.*;
import com.example.aipantry.services.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.*;

public class CoreServicesTests {

    @Test
    void shoppingList_consolidates_units_scales_and_rounds() {
        Units units = new Units(Map.of(
                "g", Map.of("to_g", 1.0),
                "kg", Map.of("to_g", 1000.0),
                "piece", Map.of("to_piece", 1.0)
        ));
        AliasResolver aliases = new AliasResolver(Map.of("red pepper", List.of("bell pepper")));
        ShoppingListService svc = new ShoppingListService();

        Recipe r1 = new Recipe("A", List.of(
                new Ingredient("red pepper", 0.5, "kg"),
                new Ingredient("spinach", 200, "g")
        ), 20, Set.of());
        Recipe r2 = new Recipe("B", List.of(
                new Ingredient("bell pepper", 250, "g"),
                new Ingredient("tortilla", 2, "piece")
        ), 15, Set.of());

        Map<String, PantryItem> pantry = new HashMap<>();
        pantry.put("spinach", new PantryItem("spinach", 50, "g", null));
        pantry.put("tortilla", new PantryItem("tortilla", 1, "piece", null));

        List<ShoppingListService.Line> lines = svc.compute(List.of(r1, r2), pantry, units, aliases);
        // peppers: 500g + 250g = 750g needed; pantry none => 750g
        assertTrue(lines.stream().anyMatch(l -> l.name.equals("red pepper") && Math.abs(l.amount - 750.0) < 0.01 && l.unit.equals("kg") == false));
        // spinach: 200g - 50g pantry = 150g
        assertTrue(lines.stream().anyMatch(l -> l.name.equals("spinach") && Math.abs(l.amount - 150.0) < 0.01));
        // tortilla: need 2 - have 1 = 1
        assertTrue(lines.stream().anyMatch(l -> l.name.equals("tortilla") && Math.abs(l.amount - 1.0) < 0.01));
    }

    @Test
    void units_convert_g_kg_ml_l_piece_identity_and_unknown() {
        Units units = new Units(Map.of(
                "g", Map.of("to_g", 1.0),
                "kg", Map.of("to_g", 1000.0),
                "ml", Map.of("to_ml", 1.0),
                "l", Map.of("to_ml", 1000.0),
                "piece", Map.of("to_piece", 1.0)
        ));
        assertEquals(1000.0, units.convert(1, "kg", "g"), 1e-9);
        assertEquals(1.0, units.convert(1000, "ml", "l"), 1e-9);
        assertEquals(3.0, units.convert(3, "piece", "piece"), 1e-9);
        // Unknown stays same
        assertEquals(5.0, units.convert(5, "unknown", "g"), 1e-9);
    }

    @Test
    void aliasResolver_matches_canonical() {
        AliasResolver a = new AliasResolver(Map.of("red pepper", List.of("bell pepper", "capsicum")));
        assertEquals("red pepper", a.canonical("Red Pepper"));
        assertEquals("red pepper", a.canonical("bell pepper"));
        assertEquals("carrot", a.canonical("carrot"));
    }

    @Test
    void ruleEngine_explain_clamps_to_100_and_calculates_coverage() {
        RuleEngine eng = new RuleEngine();
        Map<String, PantryItem> pantry = new HashMap<>();
        pantry.put("spinach", new PantryItem("spinach", 200, "g", LocalDate.now().plusDays(1)));
        Recipe r = new Recipe("Spinach Quick",
                List.of(new Ingredient("spinach", 100, "g")), 10, Set.of("quick"));

        RuleExplanation ex = eng.explain(r, pantry, Set.of("quick"), 30);
        assertEquals(1, ex.haveCount);
        assertEquals(1, ex.totalIngredients);
        assertEquals(1.0, ex.coverage, 1e-9);
        assertTrue(ex.totalScore <= 100.0);
        assertTrue(ex.totalScore >= 70.0); // base 70 + bonuses
    }
}
