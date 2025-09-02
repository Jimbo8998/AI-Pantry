package com.example.aipantry.services;

import com.example.aipantry.model.*;
import java.util.*;
import java.util.stream.Collectors;

public class Planner {
    private final RuleEngine engine = new RuleEngine();
    public List<Recipe> plan(List<Recipe> recipes, Map<String, PantryItem> pantry, int meals, Set<String> requiredTags, int maxCookMinutes) {
        return recipes.stream()
            .sorted((a,b) -> Double.compare(
                engine.score(b, pantry, requiredTags, maxCookMinutes),
                engine.score(a, pantry, requiredTags, maxCookMinutes)))
            .limit(meals)
            .collect(Collectors.toList());
    }
}
