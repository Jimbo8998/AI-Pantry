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
                if (!this.units.containsKey("tbsp")) this.units.put("tbsp", Map.of("to_ml", 15.0));
            }
            // pieces exist by default in sample data
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
            case "tablespoon": case "tablespoons": case "tbsp": return "tbsp";
            
            case "piece": case "pieces": case "pc": case "pcs": return "piece";
            default:
                // leave as-is but trimmed/lowercased
                return u;
        }
    }
}
