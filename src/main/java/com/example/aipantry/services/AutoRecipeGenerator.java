package com.example.aipantry.services;

import com.example.aipantry.model.Ingredient;
import com.example.aipantry.model.PantryItem;
import com.example.aipantry.model.Recipe;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Auto-generates quick recipes from pantry contents (no library required).
 * Heuristic templates: stir-fry, tacos/wraps, pasta skillet, fried rice, soup.
 */
public class AutoRecipeGenerator {
    private static final Set<String> PROTEINS = set("chicken breast","chicken thigh","chicken","beef mince","ground beef","beef","pork","tofu","shrimp","salmon","tuna","eggs","egg");
    private static final Set<String> VEG = set("onion","onions","bell pepper","bell peppers","broccoli","carrot","carrots","tomato","tomatoes","spinach","mushroom","mushrooms","zucchini","corn","peas");
    private static final Set<String> AROMATICS = set("garlic","ginger","scallion","green onion");
    private static final Set<String> FATS = set("oil","olive oil","butter","ghee");
    private static final Set<String> CHEESE = set("parmesan","cheddar","mozzarella","feta");

    private static Set<String> set(String... s){ return new HashSet<>(Arrays.asList(s)); }

    /** Returns synthesized recipes, sorted to consume soon-to-expire items. */
    public List<Recipe> generate(Map<String, PantryItem> pantry, int servings, int limit){
        if (pantry == null) pantry = Map.of();
        int sv = Math.max(1, servings);

        // Index pantry by lowercased name and convenience lists
        Map<String, PantryItem> p = new LinkedHashMap<>();
        for (var e: pantry.entrySet()) p.put(e.getKey().toLowerCase(), e.getValue());
        List<PantryItem> proteins = filterBy(p, PROTEINS);
    // starches are referenced on-demand via firstPresent
        List<PantryItem> vegs = filterBy(p, VEG);
        List<PantryItem> arom = filterBy(p, AROMATICS);
        List<PantryItem> fats = filterBy(p, FATS);
        List<PantryItem> cheeses = filterBy(p, CHEESE);

        List<Recipe> out = new ArrayList<>();

        // Template: Stir-fry Bowl (protein + veg + rice/noodles)
        if (!proteins.isEmpty() && (!vegs.isEmpty() || !arom.isEmpty()) && hasAnyName(p, Set.of("rice","noodles","spaghetti","pasta"))) {
            PantryItem prot = soonest(proteins);
            PantryItem veg = !vegs.isEmpty()? soonest(vegs) : null;
            String carbName = firstPresent(p, List.of("rice","noodles","spaghetti","pasta"));
            List<Ingredient> ings = new ArrayList<>();
            addProtein(ings, prot, sv, 120);
            addVeg(ings, veg, sv, 80);
            if (arom.stream().anyMatch(x -> x.name.equalsIgnoreCase("garlic"))) ings.add(new Ingredient("garlic", Math.max(1, Math.round(0.5*sv)), "clove"));
            if (carbName != null) ings.add(new Ingredient(carbName, 75 * sv, "g"));
            if (!fats.isEmpty()) ings.add(new Ingredient(fats.get(0).name, 1, "tbsp"));
            out.add(new Recipe(cap("%s Stir-fry with %s".formatted(prot.name, veg != null? veg.name: "veg")), ings, 25, Set.of("skillet","stir-fry","weeknight")));
        }

        // Template: Tacos/Wraps (protein + veg + tortilla)
        if (hasAnyName(p, Set.of("tortilla")) && !proteins.isEmpty()) {
            PantryItem prot = soonest(proteins);
            PantryItem veg = !vegs.isEmpty()? soonest(vegs) : null;
            List<Ingredient> ings = new ArrayList<>();
            addProtein(ings, prot, sv, 100);
            addVeg(ings, veg, sv, 60);
            ings.add(new Ingredient("tortilla", 2 * sv, "pc"));
            if (!cheeses.isEmpty()) ings.add(new Ingredient(cheeses.get(0).name, 20 * sv, "g"));
            out.add(new Recipe(cap("%s Tacos".formatted(prot.name)), ings, 20, Set.of("mexican","weeknight")));
        }

        // Template: Pasta Skillet (protein optional) + tomato + pasta + cheese
        if (hasAnyName(p, Set.of("pasta","spaghetti"))) {
            PantryItem prot = proteins.isEmpty()? null : soonest(proteins);
            String pasta = firstPresent(p, List.of("pasta","spaghetti"));
            String tomato = firstPresent(p, List.of("tomato","tomatoes"));
            List<Ingredient> ings = new ArrayList<>();
            if (prot != null) addProtein(ings, prot, sv, 100);
            if (tomato != null) ings.add(new Ingredient(tomato, 150 * sv, "g"));
            ings.add(new Ingredient(pasta, 80 * sv, "g"));
            if (!cheeses.isEmpty()) ings.add(new Ingredient(cheeses.get(0).name, 15 * sv, "g"));
            out.add(new Recipe(cap("One-pan %s Skillet".formatted(pasta)), ings, 22, Set.of("pasta","skillet","weeknight")));
        }

        // Template: Fried Rice (veg + egg or protein + rice)
        if (hasAnyName(p, Set.of("rice"))) {
            PantryItem prot = proteins.isEmpty()? null : soonest(proteins);
            boolean hasEgg = hasAnyName(p, Set.of("egg","eggs"));
            List<Ingredient> ings = new ArrayList<>();
            if (prot != null) addProtein(ings, prot, sv, 80);
            if (hasEgg) ings.add(new Ingredient("egg", Math.max(1, (int)Math.round(0.5*sv)), "pc"));
            PantryItem veg = !vegs.isEmpty()? soonest(vegs) : null;
            addVeg(ings, veg, sv, 60);
            ings.add(new Ingredient("rice", 75 * sv, "g"));
            if (!fats.isEmpty()) ings.add(new Ingredient(fats.get(0).name, 1, "tbsp"));
            out.add(new Recipe("Fried Rice" + (prot!=null? " with "+prot.name : ""), ings, 18, Set.of("stir-fry","weeknight")));
        }

        // Template: Hearty Soup (veg + starch)
        if (!vegs.isEmpty()) {
            PantryItem veg = soonest(vegs);
            String starch = firstPresent(p, List.of("potato","potatoes","rice","pasta"));
            List<Ingredient> ings = new ArrayList<>();
            addVeg(ings, veg, sv, 90);
            if (starch != null) ings.add(new Ingredient(starch, 60 * sv, "g"));
            out.add(new Recipe(cap(veg.name + " Soup"), ings, 30, Set.of("soup","comfort")));
        }

        // Sort by soon-to-expire hits, then shorter cook time
        out.sort(Comparator.comparingInt((Recipe r) -> -soonHits(r, p, 3))
                .thenComparingInt(r -> r.cookMinutes));
        if (limit > 0 && out.size() > limit) return out.subList(0, limit);
        return out;
    }

    private static List<PantryItem> filterBy(Map<String, PantryItem> p, Set<String> names){
        return p.entrySet().stream()
                .filter(e -> names.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .sorted(Comparator.comparing((PantryItem x) -> x.expiresOn == null ? LocalDate.MAX : x.expiresOn))
                .collect(Collectors.toList());
    }
    private static boolean hasAnyName(Map<String, PantryItem> p, Set<String> names){
        for (String n: names) if (p.containsKey(n)) return true; return false;
    }
    private static String firstPresent(Map<String, PantryItem> p, List<String> names){
        for (String n: names) if (p.containsKey(n)) return n; return null;
    }
    private static PantryItem soonest(List<PantryItem> list){ return (list==null||list.isEmpty())? null : list.get(0); }
    private static String cap(String s){ if (s==null||s.isBlank()) return s; return Character.toUpperCase(s.charAt(0))+s.substring(1); }

    private static void addProtein(List<Ingredient> ings, PantryItem p, int servings, double gramsPerServing){
        if (p==null) return; double amt = gramsPerServing * servings; ings.add(new Ingredient(p.name.toLowerCase(), amt, unitFor(p))); }
    private static void addVeg(List<Ingredient> ings, PantryItem p, int servings, double gramsPerServing){
        if (p==null) return; if (isPiece(p)) ings.add(new Ingredient(p.name.toLowerCase(), Math.max(1, Math.round(0.5*servings)), "pc"));
        else ings.add(new Ingredient(p.name.toLowerCase(), gramsPerServing * servings, "g")); }
    private static boolean isPiece(PantryItem p){ return p.unit!=null && p.unit.equalsIgnoreCase("pc"); }
    private static String unitFor(PantryItem p){ return isPiece(p)? "pc" : "g"; }

    private static int soonHits(Recipe r, Map<String, PantryItem> pantry, int days){
        int hits = 0;
        for (var ing : r.ingredients){
            PantryItem pi = pantry.get(ing.name.toLowerCase());
            if (pi == null || pi.expiresOn == null) continue;
            long d = ChronoUnit.DAYS.between(LocalDate.now(), pi.expiresOn);
            if (d <= days) hits++;
        }
        return hits;
    }
}
