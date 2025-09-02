package com.example.aipantry;

import com.example.aipantry.model.*;
import com.example.aipantry.services.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.stream.Collectors;

public class ShoppingListServiceTests {
  @Test
  void consolidateAndSubtractPantry() {
    ShoppingListService s = new ShoppingListService();
    Units units = new Units(Map.of("g", Map.of("to_g",1.0), "piece", Map.of("to_piece",1.0)));
    AliasResolver alias = new AliasResolver(Map.of());

    Map<String,PantryItem> pantry = new HashMap<>();
    pantry.put("spinach", new PantryItem("spinach", 150, "g", null));
    pantry.put("tortilla", new PantryItem("tortilla", 2, "piece", null));

    Recipe r1 = new Recipe("Wrap", List.of(
      new Ingredient("spinach", 200,"g"),
      new Ingredient("tortilla",4,"piece")
    ), 10, Set.of("quick"));

    Recipe r2 = new Recipe("Side", List.of(
      new Ingredient("spinach",50,"g")
    ), 5, Set.of());

    var lines = s.compute(List.of(r1,r2), pantry, units, alias);
    assertEquals(2, lines.size());

    var map = lines.stream().collect(Collectors.toMap(l -> l.name, l -> l));
    assertEquals(100.0, map.get("spinach").amount, 1e-6);   // 250 - 150
    assertEquals("g",    map.get("spinach").unit);
    assertEquals(2.0,   map.get("tortilla").amount, 1e-6);  // 4 - 2
  }
}
