package com.example.aipantry.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.example.aipantry.model.*;
import java.io.*;
import java.util.*;

public class JsonStorage {
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public List<Recipe> loadRecipes(InputStream in) throws IOException {
        try {
            return mapper.readValue(in, new TypeReference<List<Recipe>>() {});
        } catch (IOException ex) {
            throw new IOException("Failed to parse recipes JSON. Ensure it is an array of Recipe objects.", ex);
        }
    }
    public Map<String, PantryItem> loadPantry(InputStream in) throws IOException {
        List<PantryItem> list;
        try {
            list = mapper.readValue(in, new TypeReference<List<PantryItem>>(){});
        } catch (IOException ex) {
            throw new IOException("Failed to parse pantry JSON. Ensure it is an array of PantryItem objects.", ex);
        }
        Map<String, PantryItem> map = new LinkedHashMap<>();
        for (PantryItem p : list) map.put(p.name.toLowerCase(), p);
        return map;
    }
    public Map<String, List<String>> loadAliases(InputStream in) throws IOException {
        try {
            return mapper.readValue(in, new TypeReference<Map<String, List<String>>>(){});
        } catch (IOException ex) {
            throw new IOException("Failed to parse aliases JSON. Expect a map: canonical -> [aliases]", ex);
        }
    }
    public Map<String, Map<String, Double>> loadUnits(InputStream in) throws IOException {
        try {
            return mapper.readValue(in, new TypeReference<Map<String, Map<String, Double>>>(){});
        } catch (IOException ex) {
            throw new IOException("Failed to parse units JSON. Expect a nested map with conversion anchors.", ex);
        }
    }
    public static class Aisles {
        public List<String> order = List.of("Produce","Dairy","Pantry","Frozen","Bakery","Meat","Other");
        public Map<String,String> map = new LinkedHashMap<>();
    }
    public Aisles loadAisles(InputStream in) throws IOException {
        try {
            return mapper.readValue(in, Aisles.class);
        } catch (IOException ex) {
            throw new IOException("Failed to parse aisles JSON. Expect { order: [..], map: { item: category } }", ex);
        }
    }
    public void savePantry(Map<String, PantryItem> pantry, File f) throws IOException {
        mapper.writeValue(f, new ArrayList<>(pantry.values()));
    }
}
