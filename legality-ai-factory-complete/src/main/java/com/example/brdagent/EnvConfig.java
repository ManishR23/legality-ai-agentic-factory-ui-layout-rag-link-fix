package com.example.brdagent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EnvConfig {
    private final Map<String,String> values = new HashMap<>();

    public EnvConfig() {
        values.putAll(System.getenv());
        Path env = Path.of(".env");
        if (Files.exists(env)) {
            try {
                List<String> lines = Files.readAllLines(env);
                for (String line : lines) {
                    String s = line.trim();
                    if (s.isEmpty() || s.startsWith("#") || !s.contains("=")) continue;
                    int idx = s.indexOf('=');
                    values.putIfAbsent(s.substring(0, idx).trim(), s.substring(idx + 1).trim());
                }
            } catch (Exception ignored) {}
        }
    }

    public String openAiKey() { return values.getOrDefault("OPENAI_API_KEY", "").trim(); }
    public String model() {
        String m = values.getOrDefault("OPENAI_MODEL", "gpt-4.1-mini").trim();
        return m.isEmpty() ? "gpt-4.1-mini" : m;
    }
}
