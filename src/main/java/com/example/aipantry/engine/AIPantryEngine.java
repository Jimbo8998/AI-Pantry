package com.example.aipantry.engine;

import com.example.aipantry.model.PantryItem;
import com.example.aipantry.model.Recipe;
import com.example.aipantry.services.AutoRecipeGenerator;

import java.util.*;

/** Compatibility facade to match the canvas API. */
public class AIPantryEngine {
    public static class Generator {
        public static class Generated {
            public final Recipe recipe;
            public final List<String> steps;
            public final String imageUrl;
            public Generated(Recipe recipe, List<String> steps, String imageUrl){
                this.recipe = recipe; this.steps = steps; this.imageUrl = imageUrl;
            }
        }

        /**
         * Canvas-compatible signature. Applies the unlimited pattern and delegates
         * to the in-project AutoRecipeGenerator for the actual generation logic
         * (which already uses cross-product variant loops).
         */
        public static List<Generated> generateDetailed(List<PantryItem> pantry, int servings, int limit) {
            if (pantry == null) pantry = List.of();
            servings = Math.max(1, servings);
            boolean unlimited = limit <= 0;
            int cap = unlimited ? Integer.MAX_VALUE : limit;

            Map<String, PantryItem> map = new LinkedHashMap<>();
            for (PantryItem pi : pantry){
                if (pi == null || pi.name == null) continue;
                map.put(pi.name.toLowerCase(), pi);
            }
            List<AutoRecipeGenerator.Generated> tmp = new AutoRecipeGenerator().generateDetailed(map, servings, limit);
            List<Generated> out = new ArrayList<>(tmp.size());
            for (AutoRecipeGenerator.Generated g : tmp) out.add(new Generated(g.recipe, g.steps, g.imageUrl));
            return unlimited ? out : out.stream().limit(cap).toList();
        }
    }
}
