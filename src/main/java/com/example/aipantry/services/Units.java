package com.example.aipantry.services;
import java.util.Map;

public class Units {
    private final Map<String, Map<String, Double>> units;
    public Units(Map<String, Map<String, Double>> unitMap){
        // Always keep a mutable copy internally
        this.units = (unitMap == null) ? new java.util.HashMap<>() : new java.util.HashMap<>(unitMap);
        // Add a tiny default map so more recipes just work
        if (this.units != null) {
            // weight
            if (this.units.containsKey("g") && !this.units.containsKey("kg")) {
                this.units.put("kg", Map.of("to_g", 1000.0));
            }
            // volume
            if (this.units.containsKey("ml")) {
                if (!this.units.containsKey("l")) this.units.put("l", Map.of("to_ml", 1000.0));
                // common culinary units relative to ml
                if (!this.units.containsKey("tsp")) this.units.put("tsp", Map.of("to_ml", 4.92892));
                if (!this.units.containsKey("tbsp")) this.units.put("tbsp", Map.of("to_ml", 14.7868));
                if (!this.units.containsKey("floz")) this.units.put("floz", Map.of("to_ml", 29.5735));
                if (!this.units.containsKey("cup")) this.units.put("cup", Map.of("to_ml", 236.588));
                if (!this.units.containsKey("pint")) this.units.put("pint", Map.of("to_ml", 473.176));
                if (!this.units.containsKey("quart")) this.units.put("quart", Map.of("to_ml", 946.353));
                if (!this.units.containsKey("gallon")) this.units.put("gallon", Map.of("to_ml", 3785.41));
            }
            // pieces exist by default in sample data
            if (this.units.containsKey("piece")) {
                // Treat other count-style units as equivalent to a piece for arithmetic aggregation
                if (!this.units.containsKey("can")) this.units.put("can", Map.of("to_piece", 1.0));
                if (!this.units.containsKey("egg")) this.units.put("egg", Map.of("to_piece", 1.0));
                if (!this.units.containsKey("clove")) this.units.put("clove", Map.of("to_piece", 1.0));
                if (!this.units.containsKey("slice")) this.units.put("slice", Map.of("to_piece", 1.0));
                if (!this.units.containsKey("item")) this.units.put("item", Map.of("to_piece", 1.0));
            }
        }
    }
    public double convert(double amount, String from, String to) {
    // normalize unit names before conversion
    String nf = normalizeUnit(from);
    String nt = normalizeUnit(to);
    if (nf==null || nt==null || nf.equalsIgnoreCase(nt)) return amount;
    var f=units.get(nf);
    var t=units.get(nt);
        if (f==null || t==null) return amount;
        for (var k: f.keySet()) if (t.containsKey(k)) return amount * f.get(k) / t.get(k);
        return amount;
    }

    /** Returns true if the unit string is known in the conversion table. */
    public boolean isKnownUnit(String unit) {
    String u = normalizeUnit(unit);
    return u != null && units != null && units.containsKey(u);
    }

    /**
     * Prefer a base display unit for a given unit. For anchors like to_g or to_ml,
     * return the unit whose factor is 1.0 for that anchor (e.g., g, ml, piece).
     * If not determinable, returns the input unit.
     */
    public String preferredDisplayUnit(String unit) {
        if (unit == null) return null;
        var m = units.get(normalizeUnit(unit));
        if (m == null) return unit;
        for (String anchor : m.keySet()) {
            // find any unit that shares this anchor with factor == 1.0
            for (var e : units.entrySet()) {
                var mm = e.getValue();
                if (mm != null && mm.containsKey(anchor) && Math.abs(mm.get(anchor) - 1.0) < 1e-9) {
                    return e.getKey();
                }
            }
        }
        return unit;
    }

    /**
     * Map common synonyms/plurals to canonical short forms used in conversion table.
     * Examples: "grams"->"g", "gram"->"g", "kilogram(s)"->"kg", "litre"/"liter"->"l",
     * "millilitre"/"milliliter"->"ml", "tablespoon(s)"->"tbsp".
     */
    public String normalizeUnit(String unit) {
        if (unit == null) return null;
        String u = unit.trim().toLowerCase();
        if (u.isEmpty()) return u;
        // quick dictionary
        switch (u) {
            case "gram": case "grams": case "gms": case "gm": return "g";
            case "kilogram": case "kilograms": case "kgs": case "kg": return "kg";
            case "milliliter": case "millilitre": case "milliliters": case "millilitres": return "ml";
            case "liter": case "litre": case "liters": case "litres": return "l";
            case "teaspoon": case "teaspoons": case "tsp": return "tsp";
            case "tablespoon": case "tablespoons": case "tbsp": return "tbsp";
            case "fluid ounce": case "fluid ounces": case "fl oz": case "floz": return "floz";
            case "cup": case "cups": return "cup";
            case "pint": case "pints": return "pint";
            case "quart": case "quarts": return "quart";
            case "gallon": case "gallons": case "gal": case "gals": return "gallon";
            
            case "piece": case "pieces": case "pc": case "pcs": case "each": case "ea": case "ct": return "piece";
            case "cans": return "can";
            default:
                // leave as-is but trimmed/lowercased
                return u;
        }
    }
}
