package com.example.aipantry.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.example.aipantry.model.*;
import java.io.*;
import java.util.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class JsonStorage {
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .registerModule(new JavaTimeModule());

    public List<Recipe> loadRecipes(InputStream in) throws IOException {
        try {
            return mapper.readValue(in, new TypeReference<List<Recipe>>() {});
        } catch (IOException ex) {
            throw new IOException("Failed to parse recipes JSON. Ensure it is an array of Recipe objects.", ex);
        }
    }
    public Map<String, PantryItem> loadPantry(InputStream in) throws IOException {
        byte[] data = readAll(in);
        List<PantryItem> list = null;
        IOException last = null;
        try {
            list = mapper.readValue(new ByteArrayInputStream(data), new TypeReference<List<PantryItem>>(){});
        } catch (IOException ex) { last = ex; }
        if (list == null) {
            try {
                PantryWrapper wrap = mapper.readValue(new ByteArrayInputStream(data), PantryWrapper.class);
                if (wrap != null) list = wrap.pantry;
            } catch (IOException ex) { last = ex; }
        }
        if (list == null) {
            throw new IOException("Failed to parse pantry JSON. Provide an array of PantryItems or {\"pantry\":[...] }.", last);
        }
        Map<String, PantryItem> map = new LinkedHashMap<>();
        for (PantryItem p : list) if (p!=null && p.name!=null) map.put(p.name.toLowerCase(), p);
        return map;
    }

    public static class PantryWrapper { public List<PantryItem> pantry; }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192]; int r;
        while ((r = in.read(buf)) != -1) bos.write(buf, 0, r);
        return bos.toByteArray();
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
