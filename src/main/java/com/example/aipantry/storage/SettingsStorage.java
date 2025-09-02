package com.example.aipantry.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.*;
import java.nio.file.*;

public class SettingsStorage {
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final Path dir = Path.of(System.getProperty("user.home"), ".ai-pantry");
    private final Path file = dir.resolve("settings.json");

    public Settings load() {
        try {
            if (!Files.exists(file)) return new Settings();
            try (InputStream in = Files.newInputStream(file)) {
                return mapper.readValue(in, Settings.class);
            }
        } catch (Exception ex) { return new Settings(); }
    }

    public void save(Settings s) throws IOException {
        if (!Files.exists(dir)) Files.createDirectories(dir);
        try (OutputStream out = Files.newOutputStream(file)) {
            mapper.writeValue(out, s);
        }
    }
}
