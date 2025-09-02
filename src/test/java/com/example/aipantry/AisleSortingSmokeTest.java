package com.example.aipantry;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.util.*;

import org.junit.jupiter.api.Test;

import com.example.aipantry.services.*;
import com.example.aipantry.storage.JsonStorage;
import com.example.aipantry.model.*;

public class AisleSortingSmokeTest {
    @Test
    void loadsAislesAndSorts() throws Exception {
        JsonStorage s = new JsonStorage();
        try (InputStream ais = getClass().getResourceAsStream("/sample-data/aisles.json")) {
            var a = s.loadAisles(ais);
            assertNotNull(a);
            assertTrue(a.order.contains("Produce"));
        }
        ShoppingListService svc = new ShoppingListService();
        Units units = new Units(Map.of("g", Map.of("to_g",1.0)));
        AliasResolver ar = new AliasResolver(Map.of());
        Recipe r = new Recipe();
        r.title = "Test";
        r.ingredients = List.of(new Ingredient("spinach",1,"g"), new Ingredient("cheddar",1,"g"));
        Map<String,PantryItem> pantry = new HashMap<>();
        var lines = svc.compute(List.of(r), pantry, units, ar);
        assertEquals(2, lines.size());
    }
}
