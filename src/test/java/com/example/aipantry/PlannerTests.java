package com.example.aipantry;

import com.example.aipantry.model.*;
import com.example.aipantry.services.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

public class PlannerTests {
  @Test
  void plannerReturnsTopN() {
    Map<String,PantryItem> pantry = new HashMap<>();
    pantry.put("spinach", new PantryItem("spinach", 200,"g", null));

    Recipe good = new Recipe("Good", List.of(new Ingredient("spinach",100,"g")), 10, Set.of("quick"));
    Recipe bad  = new Recipe("Bad",  List.of(new Ingredient("spinach",500,"g")), 10, Set.of());

    Planner p = new Planner();
    var out = p.plan(List.of(bad, good), pantry, 1, Set.of(), 30);
    assertEquals(1, out.size());
    assertEquals("Good", out.get(0).title);
  }
}
