package com.example.aipantry;

import com.example.aipantry.model.*;
import com.example.aipantry.services.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDate;
import java.util.*;

public class RuleEngineTests {
    @Test
    void scorePrefersPantryMatchAndPerishables() {
        RuleEngine engine = new RuleEngine();
        Map<String, PantryItem> pantry = new HashMap<>();
        pantry.put("spinach", new PantryItem("spinach", 200, "g", LocalDate.now().plusDays(1)));
        pantry.put("tortilla", new PantryItem("tortilla", 6, "piece", LocalDate.now().plusDays(7)));

        Recipe r = new Recipe("Spinach Wrap",
                List.of(new Ingredient("spinach", 100, "g"), new Ingredient("tortilla", 2, "piece")),
                15, Set.of("quick"));

        double score = engine.score(r, pantry, Set.of("quick"), 30);
        assertTrue(score >= 80.0);
    }
}
