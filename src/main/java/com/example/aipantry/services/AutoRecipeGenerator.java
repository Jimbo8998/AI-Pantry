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

    /** Wrapper for a detailed generated recipe. */
    public static class Generated {
        public final Recipe recipe;
        public final List<String> steps;
        public final String imageUrl;
        public Generated(Recipe r, List<String> steps, String imageUrl){ this.recipe=r; this.steps=steps; this.imageUrl=imageUrl; }
    }

    /** Returns synthesized recipes, sorted to consume soon-to-expire items. */
    public List<Recipe> generate(Map<String, PantryItem> pantry, int servings, int limit){
        if (pantry == null) pantry = Map.of();
        int sv = Math.max(1, servings);
    boolean unlimited = limit <= 0;
    int cap = unlimited ? Integer.MAX_VALUE : limit;

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
    return unlimited ? out : out.stream().limit(cap).toList();
    }

    /** Returns synthesized recipes with step-by-step directions and image URL. */
    public List<Generated> generateDetailed(Map<String, PantryItem> pantry, int servings, int limit){
        if (pantry == null) pantry = Map.of();
        int sv = Math.max(1, servings);
        boolean unlimited = limit <= 0;
        int cap = unlimited ? Integer.MAX_VALUE : limit;

        Map<String, PantryItem> p = new LinkedHashMap<>();
        for (var e: pantry.entrySet()) p.put(e.getKey().toLowerCase(), e.getValue());
        List<PantryItem> proteins = filterBy(p, PROTEINS);
        List<PantryItem> vegs = filterBy(p, VEG);
        List<PantryItem> arom = filterBy(p, AROMATICS);
        List<PantryItem> fats = filterBy(p, FATS);
        List<PantryItem> cheeses = filterBy(p, CHEESE);

        List<Generated> out = new ArrayList<>();
        // Cross-product variant generation
        // 1) Stir-fry: protein × veg × starch
        List<String> stirStarches = presentNames(p, List.of("rice","noodles","spaghetti","pasta"));
        if (!proteins.isEmpty() && (!vegs.isEmpty() || !arom.isEmpty()) && !stirStarches.isEmpty()){
            for (PantryItem prot : proteins){
                for (PantryItem veg : (vegs.isEmpty()? List.<PantryItem>of((PantryItem)null) : vegs)){
                    for (String carbName : stirStarches){
                        List<Ingredient> ings = new ArrayList<>();
                        addProtein(ings, prot, sv, 120);
                        addVeg(ings, veg, sv, 80);
                        if (!arom.isEmpty() && arom.stream().anyMatch(x -> x.name.equalsIgnoreCase("garlic")))
                            ings.add(new Ingredient("garlic", Math.max(1, Math.round(0.5*sv)), "clove"));
                        ings.add(new Ingredient(carbName, 75 * sv, "g"));
                        if (!fats.isEmpty()) ings.add(new Ingredient(fats.get(0).name, 1, "tbsp"));
                        Recipe r = new Recipe(cap("%s Stir-fry with %s".formatted(prot.name, veg!=null?veg.name:"veg")), ings, 25, Set.of("skillet","stir-fry","weeknight"));
                        List<String> steps = List.of(
                                "Prep and slice vegetables; mince aromatics.",
                                "Cook "+carbName+" if needed.",
                                "Heat oil in a large skillet; sear "+prot.name+".",
                                "Add vegetables and aromatics; stir-fry until crisp-tender.",
                                "Toss with sauce (soy, etc.) and serve over "+carbName+".");
                        out.add(new Generated(r, steps, imageFor(r)));
                    }
                }
            }
        }

        // 2) Tacos/Wraps: protein × tortilla
        if (p.containsKey("tortilla") && !proteins.isEmpty()){
            for (PantryItem prot : proteins){
                PantryItem veg = vegs.isEmpty()? null : vegs.get(0);
                List<Ingredient> ings = new ArrayList<>();
                addProtein(ings, prot, sv, 100);
                addVeg(ings, veg, sv, 60);
                ings.add(new Ingredient("tortilla", 2 * sv, "pc"));
                if (!cheeses.isEmpty()) ings.add(new Ingredient(cheeses.get(0).name, 20 * sv, "g"));
                Recipe r = new Recipe(cap("%s Tacos".formatted(prot.name)), ings, 20, Set.of("taco","mexican","weeknight"));
                List<String> steps = List.of(
                        "Warm tortillas in a dry skillet.",
                        "Cook "+prot.name+" until browned.",
                        "Add veg and season; cook until tender.",
                        "Assemble tacos and top with cheese.");
                out.add(new Generated(r, steps, imageFor(r)));
            }
        }

    // 3) Pasta: base × (protein or none)
        List<String> pastaBases = presentNames(p, List.of("pasta","spaghetti"));
        if (!pastaBases.isEmpty()){
        String tomato = firstPresent(p, List.of("tomato","tomatoes","tomato sauce","canned tomato"));
            List<PantryItem> protOrNone = new ArrayList<>();
            protOrNone.add(null);
            protOrNone.addAll(proteins);
        for (String pastaName : pastaBases){
                for (PantryItem prot : protOrNone){
                    List<Ingredient> ings = new ArrayList<>();
                    if (prot != null) addProtein(ings, prot, sv, 100);
                    if (tomato != null) ings.add(new Ingredient(tomato, 150 * sv, "g"));
            ings.add(new Ingredient(pastaName, 80 * sv, "g"));
                    if (!cheeses.isEmpty()) ings.add(new Ingredient(cheeses.get(0).name, 15 * sv, "g"));
            // Title includes sauce base + pasta + protein/veg
            String sauceBase = tomato != null ? "tomato" : (p.containsKey("coconut milk") ? "coconut milk" : (p.containsKey("olive oil") ? "olive oil" : null));
            String baseLabel = (sauceBase == null) ? "Olive Oil" : (sauceBase.contains("coconut") ? "Creamy Coconut" : (sauceBase.contains("tomato") ? "Tomato" : "Olive Oil"));
            String title = baseLabel + " " + cap(pastaName) + " with " + (prot != null ? displayName(prot) : "Veggies");
            Recipe r = new Recipe(title, ings, 22, Set.of("pasta","skillet","weeknight"));
                    List<String> steps = List.of(
                "Boil "+pastaName+" in salted water.",
                            (prot==null?"Skip protein step":"Brown "+prot.name+" in skillet."),
                            (tomato==null?"Add sauce of choice":"Add tomatoes and simmer."),
                "Toss with cooked "+pastaName+", finish with cheese.");
                    out.add(new Generated(r, steps, imageFor(r)));
                }
            }
        }

        // 4) Fried Rice: (protein or egg-only) × veg, requires rice
        if (p.containsKey("rice")){
            boolean hasEgg = p.containsKey("egg") || p.containsKey("eggs");
            List<PantryItem> protOrEggOnly = new ArrayList<>();
            protOrEggOnly.addAll(proteins);
            if (hasEgg || proteins.isEmpty()) protOrEggOnly.add(null); // null => egg-only
            for (PantryItem prot : protOrEggOnly){
                for (PantryItem veg : (vegs.isEmpty()? List.<PantryItem>of((PantryItem)null) : vegs)){
                    List<Ingredient> ings = new ArrayList<>();
                    if (prot != null) addProtein(ings, prot, sv, 80);
                    if (hasEgg) ings.add(new Ingredient("egg", Math.max(1, (int)Math.round(0.5*sv)), "pc"));
                    addVeg(ings, veg, sv, 60);
                    ings.add(new Ingredient("rice", 75 * sv, "g"));
                    if (!fats.isEmpty()) ings.add(new Ingredient(fats.get(0).name, 1, "tbsp"));
                    String vegName = veg != null ? cap(veg.name) : "Vegetable";
                    String title = vegName + " Fried Rice" + (hasEgg ? " with Egg" : "");
                    Recipe r = new Recipe(title, ings, 18, Set.of("stir-fry","rice","weeknight"));
                    List<String> steps = List.of(
                            "Scramble eggs and set aside.",
                            (prot==null?"Skip protein step":"Stir-fry "+prot.name+"."),
                            "Add veg and cook until tender.",
                            "Add rice, soy sauce, and eggs; stir-fry to combine.");
                    out.add(new Generated(r, steps, imageFor(r)));
                }
            }
        }

        // 5) Hearty Soup: veg × (starch or none) × (protein or none)
        if (!vegs.isEmpty()){
            List<String> soupStarches = presentNames(p, List.of("potato","potatoes","rice","pasta"));
            List<String> starchOrNone = new ArrayList<>();
            starchOrNone.add(null);
            starchOrNone.addAll(soupStarches);
            List<PantryItem> protOrNone = new ArrayList<>();
            protOrNone.add(null);
            protOrNone.addAll(proteins);
            for (PantryItem veg : vegs){
                for (String starch : starchOrNone){
                    for (PantryItem prot : protOrNone){
                        List<Ingredient> ings = new ArrayList<>();
                        addVeg(ings, veg, sv, 90);
                        if (starch != null) ings.add(new Ingredient(starch, 60 * sv, "g"));
                        if (prot != null) addProtein(ings, prot, sv, 80);
                        String bulkName = starch == null ? "" : (starch.equals("potato") ? " & Potato" : (" & " + cap(starch)));
                        String protPart = prot == null ? "" : (" with " + displayName(prot));
                        String title = "Hearty " + (veg!=null?cap(veg.name):"Vegetable") + bulkName + " Soup" + protPart;
                        Recipe r = new Recipe(title, ings, 30, Set.of("soup","comfort"));
                        List<String> steps = List.of(
                                "Sauté aromatics and "+veg.name+".",
                                "Add broth and bring to a simmer.",
                                (starch==null?"Simmer until veg is tender":"Add "+starch+" and cook until tender."),
                                (prot==null?"Optionally add protein":"Add "+prot.name+" in final 10 minutes to cook through."),
                                "Season to taste.");
                        out.add(new Generated(r, steps, imageFor(r)));
                    }
                }
            }
        }

        out.sort(Comparator.comparingInt((Generated g) -> -soonHits(g.recipe, p, 3))
                .thenComparingInt(g -> g.recipe.cookMinutes));
        return unlimited ? out : out.stream().limit(cap).toList();
    }

    /** Overload: accept a List of PantryItem (convenience). */
    public List<Generated> generateDetailed(List<PantryItem> pantryList, int servings, int limit){
        Map<String, PantryItem> map = new LinkedHashMap<>();
        if (pantryList != null){
            for (PantryItem pi : pantryList){
                if (pi == null || pi.name == null) continue;
                map.put(pi.name.toLowerCase(), pi);
            }
        }
        return generateDetailed(map, servings, limit);
    }

    private static String placeholderImage(String title){ return "https://placehold.co/800x500?text=" + (title==null?"Recipe":title.replace(" ", "+")); }

    /** Choose an image for a generated recipe using tags/title, preferring bundled resources. */
    private static String imageFor(Recipe r){
        if (r == null) return placeholderImage("Recipe");
        // Prefer tag-based mapping
        Set<String> tags = r.tags == null ? Set.of() : r.tags;
        String title = r.title == null ? "" : r.title.toLowerCase(Locale.ROOT);

        // tacos / wraps
        if (tags.contains("taco") || title.contains("taco") || title.contains("wrap"))
            return "res:/images/tacos.jpg";
        // fried rice / rice bowls (more specific than generic stir-fry)
        if (tags.contains("rice") || title.contains("fried rice") || title.contains(" rice"))
            return "res:/images/fried_rice.jpg";
        // pasta
        if (tags.contains("pasta") || title.contains("spaghetti") || title.contains("pasta"))
            return "res:/images/pasta.jpg";
        // soup
        if (tags.contains("soup") || title.contains("soup"))
            return "res:/images/soup.jpg";
        // generic stir-fry as a last resort category-specific image
        if (tags.contains("stir-fry") || title.contains("stir-fry") || title.contains("stir fry"))
            return "res:/images/stir_fry.jpg";
        // fallback to local text-based placeholder via UI loader including a brief ingredient list
        return "text:" + textCardLabel(r);
    }

    private static String textCardLabel(Recipe r){
        String title = r.title == null ? "Recipe" : r.title;
        // Choose up to 4 salient ingredients, skipping common pantry items and tiny amounts
        Set<String> skip = Set.of("oil","olive oil","butter","ghee","salt","pepper","water","broth");
        List<String> ingNames = new ArrayList<>();
        if (r.ingredients != null){
            r.ingredients.stream()
                .filter(i -> i != null && i.name != null)
                .filter(i -> !skip.contains(i.name.toLowerCase(Locale.ROOT)))
                .sorted((a,b) -> Double.compare(b.amount, a.amount))
                .limit(4)
                .forEach(i -> ingNames.add(cap(i.name)));
        }
        String bottom = ingNames.isEmpty()? "" : ("\n" + String.join(" · ", ingNames));
        return title + bottom;
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
    private static List<String> presentNames(Map<String, PantryItem> p, List<String> candidates){
        List<String> out = new ArrayList<>();
        for (String n : candidates){ if (p.containsKey(n)) out.add(n); }
        return out;
    }
    private static String firstPresent(Map<String, PantryItem> p, List<String> names){
        for (String n: names) if (p.containsKey(n)) return n; return null;
    }
    private static PantryItem soonest(List<PantryItem> list){ return (list==null||list.isEmpty())? null : list.get(0); }
    private static String cap(String s){ if (s==null||s.isBlank()) return s; return Character.toUpperCase(s.charAt(0))+s.substring(1); }
    private static String displayName(PantryItem p){ return p==null||p.name==null? "" : cap(p.name); }

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
