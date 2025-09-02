package com.example.aipantry.services;

import java.util.ArrayList;
import java.util.List;

/** Transparent breakdown for a recipe score. */
public class RuleExplanation {
    // ingredients
    public int haveCount;
    public int totalIngredients;
    public double coverage;           // 0..1
    public List<String> missing = new ArrayList<>();

    // bonuses
    public double perishablesBonus;   // usually 0 or 15
    public double timeBonus;          // 0 or 10
    public double tagBonus;           // 0 or 5
    public List<String> perishablesUsed = new ArrayList<>();

    // final
    public double baseFromCoverage;   // 70 * coverage
    public double totalScore;         // clamped 0..100

    @Override public String toString() {
        return String.format(
            "Coverage: %d/%d (%.0f%%)  |  +Perishables: %.0f  |  +Time: %.0f  |  +Tags: %.0f  =>  Total: %.0f",
            haveCount, totalIngredients, coverage*100.0, perishablesBonus, timeBonus, tagBonus, totalScore);
    }
}
