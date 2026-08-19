package com.example.brdagent;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.json.JsonReadContext;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JacksonRuntimeCheck {
    private JacksonRuntimeCheck() {}

    public static void assertCompatible() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.readTree("{\"ok\":true,\"items\":[1,2,3]}");
            try (JsonParser parser = new JsonFactory().createParser("{\"ok\":true}")) {
                while (parser.nextToken() != null) { }
            }
        } catch (Throwable ex) {
            throw new IllegalStateException("Jackson runtime mismatch. " + versionSummary()
                    + ". In Eclipse: Maven > Update Project > Force Update, then Project > Clean. Remove manually-added Jackson jars.", ex);
        }
    }

    public static String versionSummary() {
        return "jackson-core=" + version(JsonFactory.class)
                + ", jackson-databind=" + version(ObjectMapper.class)
                + ", coreSource=" + source(JsonReadContext.class)
                + ", databindSource=" + source(ObjectMapper.class);
    }

    private static String version(Class<?> c) {
        Package p = c.getPackage(); String v = p == null ? null : p.getImplementationVersion(); return v == null ? "unknown" : v;
    }
    private static String source(Class<?> c) {
        try { return String.valueOf(c.getProtectionDomain().getCodeSource().getLocation()); }
        catch (Exception e) { return "unknown"; }
    }
}
