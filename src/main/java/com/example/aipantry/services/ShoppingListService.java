package com.example.aipantry.services;

import com.example.aipantry.model.*;
import java.util.*;

public class ShoppingListService {
    public static class Line {
        public final String name; public final double amount; public final String unit;
        public Line(String name,double amount,String unit){ this.name=name; this.amount=amount; this.unit=unit; }
        @Override public String toString(){ return name + "," + amount + "," + unit; }
    }

    public List<Line> compute(List<Recipe> recipes, Map<String,PantryItem> pantry, Units units, AliasResolver aliases){
        Map<String, Line> need = new LinkedHashMap<>();

        for (Recipe r: recipes) for (Ingredient ing: r.ingredients){
            String key = aliases!=null ? aliases.canonical(ing.name) : ing.name.toLowerCase();
            String baseUnit = units!=null ? units.preferredDisplayUnit(ing.unit) : ing.unit;
            double amt = (units!=null && baseUnit!=null) ? units.convert(ing.amount, ing.unit, baseUnit) : ing.amount;
            Line line = need.get(key);
            if (line==null) need.put(key, new Line(key, amt, baseUnit));
            else need.put(key, new Line(key, line.amount + (units!=null? units.convert(amt, baseUnit, line.unit): amt), line.unit));
        }

        List<Line> out = new ArrayList<>();
        for (var e: need.entrySet()){
            String k = e.getKey(); Line l = e.getValue();
            PantryItem stock = pantry.get(k);
            double remaining = l.amount;
            if (stock != null){
                double stockInUnit = units!=null? units.convert(stock.quantity, stock.unit, l.unit) : stock.quantity;
                remaining = Math.max(0, l.amount - stockInUnit);
            }
            if (remaining > 0.0001) out.add(new Line(k, Math.round(remaining*100.0)/100.0, l.unit));
        }
        return out;
    }

    /**
     * Returns a new list with amounts multiplied by the given multiplier, rounded to 2 decimals.
     * If multiplier <= 1, returns the original list.
     */
    public List<Line> scale(List<Line> lines, int multiplier) {
        if (lines == null || multiplier <= 1) return lines;
        List<Line> out = new ArrayList<>();
        for (Line l : lines) {
            double amt = Math.round(l.amount * multiplier * 100.0) / 100.0;
            out.add(new Line(l.name, amt, l.unit));
        }
        return out;
    }
}
