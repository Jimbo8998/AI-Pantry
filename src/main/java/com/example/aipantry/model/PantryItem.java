package com.example.aipantry.model;
import java.time.LocalDate;

public class PantryItem {
    public String name;
    public double quantity;
    public String unit;
    public LocalDate expiresOn; // nullable

    public PantryItem() {}
    public PantryItem(String name, double quantity, String unit, LocalDate expiresOn) {
        this.name = name; this.quantity = quantity; this.unit = unit; this.expiresOn = expiresOn;
    }
}
