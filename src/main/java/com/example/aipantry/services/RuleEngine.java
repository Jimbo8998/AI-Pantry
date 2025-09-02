package com.example.aipantry.services;

import com.example.aipantry.model.*;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

public class RuleEngine {

    /** Backwards-compatible: returns only the final score. */
    public double score(Recipe recipe, Map<String, PantryItem> pantry, Set<String> requiredTags, int maxCookMinutes) {
        return explain(recipe, pantry, requiredTags, maxCookMinutes).totalScore;
    }

    /** Returns a transparent breakdown for UI display. */
    public RuleExplanation explain(Recipe recipe, Map<String, PantryItem> pantry, Set<String> requiredTags, int maxCookMinutes) {
        RuleExplanation ex = new RuleExplanation();

        // 1) Pantry coverage & missing items
        ex.totalIngredients = recipe.ingredients.size();
        for (Ingredient ing : recipe.ingredients) {
            PantryItem item = pantry.get(ing.name.toLowerCase());
            boolean ok = false;
            if (item != null) {
                // simple unit-aware check: if units match or no units, compare; otherwise assume not enough
                if (item.unit == null || ing.unit == null || item.unit.equalsIgnoreCase(ing.unit)) {
                    ok = item.quantity >= ing.amount;
                }
            }
            if (ok) ex.haveCount++;
            else    ex.missing.add(ing.name + " (" + ing.amount + " " + ing.unit + ")");
        }
        ex.coverage = ex.totalIngredients == 0 ? 1.0 : (double) ex.haveCount / ex.totalIngredients;
        ex.baseFromCoverage = 70.0 * ex.coverage;

        // 2) Perishables bonus (ingredient used that expires in <= 3 days)
        for (PantryItem item : pantry.values()) {
            if (item.expiresOn != null && item.expiresOn.isBefore(LocalDate.now().plusDays(3))) {
                boolean used = recipe.ingredients.stream().anyMatch(i -> i.name.equalsIgnoreCase(item.name));
                if (used) {
                    ex.perishablesBonus = 15.0;
                    ex.perishablesUsed.add(item.name + " exp " + item.expiresOn);
                    break;
                }
            }
        }

        // 3) Cook time preference
        if (recipe.cookMinutes <= maxCookMinutes) ex.timeBonus = 10.0;

        // 4) Tag requirement
        if (requiredTags == null || recipe.tags.containsAll(requiredTags)) ex.tagBonus = 5.0;

        // 5) Final
        ex.totalScore = Math.min(100.0, ex.baseFromCoverage + ex.perishablesBonus + ex.timeBonus + ex.tagBonus);
        return ex;
    }
}
