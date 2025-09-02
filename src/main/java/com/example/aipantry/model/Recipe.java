package com.example.aipantry.model;
import java.util.List;
import java.util.Set;

public class Recipe {
    public String title;
    public List<Ingredient> ingredients;
    public int cookMinutes;
    public Set<String> tags;
    public Recipe() {}
    public Recipe(String title, List<Ingredient> ingredients, int cookMinutes, Set<String> tags) {
        this.title=title; this.ingredients=ingredients; this.cookMinutes=cookMinutes; this.tags=tags;
    }
}
