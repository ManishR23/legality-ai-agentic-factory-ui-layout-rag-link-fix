package com.example.legality.runtime;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class ScheduleContext {
    private final JsonNode schedule;

    public ScheduleContext(JsonNode schedule) { this.schedule = schedule; }
    public JsonNode raw() { return schedule; }

    public JsonNode at(String path) {
        if (schedule == null || path == null || path.trim().isEmpty()) return schedule;
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        normalized = normalized.replace('.', '/');
        JsonNode current = schedule;
        for (String part : normalized.split("/")) {
            if (part.isEmpty()) continue;
            if (current == null || current.isMissingNode() || current.isNull()) return null;
            if (current.isArray()) {
                try { current = current.path(Integer.parseInt(part)); }
                catch (NumberFormatException ex) { return null; }
            } else current = current.path(part);
        }
        return current;
    }

    public String getText(String path, String fallback) {
        JsonNode n = at(path); return n == null || n.isMissingNode() || n.isNull() ? fallback : n.asText();
    }
    public boolean getBoolean(String path, boolean fallback) {
        JsonNode n = at(path); return n == null || n.isMissingNode() || n.isNull() ? fallback : n.asBoolean();
    }
    public int getInt(String path, int fallback) {
        JsonNode n = at(path); return n != null && n.isNumber() ? n.asInt() : fallback;
    }
    public double getDouble(String path, double fallback) {
        JsonNode n = at(path); return n != null && n.isNumber() ? n.asDouble() : fallback;
    }

    public List<JsonNode> pairings() { return array(schedule == null ? null : schedule.path("pairings")); }
    public List<JsonNode> activities() {
        List<JsonNode> out = new ArrayList<>();
        for (JsonNode p : pairings()) {
            for (JsonNode d : array(p.path("duties"))) out.addAll(array(d.path("activities")));
        }
        out.addAll(array(schedule == null ? null : schedule.path("nonFlyingActivities")));
        return out;
    }
    public List<JsonNode> flightActivities() {
        List<JsonNode> out = new ArrayList<>();
        for (JsonNode a : activities()) if ("FLIGHT".equalsIgnoreCase(a.path("type").asText())) out.add(a);
        return out;
    }
    public List<JsonNode> nonFlyingActivities() {
        List<JsonNode> out = new ArrayList<>();
        for (JsonNode a : activities()) if (!"FLIGHT".equalsIgnoreCase(a.path("type").asText())) out.add(a);
        return out;
    }
    public double hoursBetween(String start, String end) {
        try { return Duration.between(parseToInstant(start), parseToInstant(end)).toMinutes() / 60.0; }
        catch (Exception e) {
            throw new IllegalArgumentException("Cannot compute hoursBetween(\"" + start + "\", \"" + end + "\"): " + e.getMessage(), e);
        }
    }

    /**
     * Accepts an offset/zoned ISO-8601 timestamp (resolved to a true instant, so two
     * timestamps with different offsets are compared correctly) or a bare LocalDateTime
     * (treated as UTC wall-clock time, matching this method's original behavior).
     */
    private Instant parseToInstant(String s) {
        try { return OffsetDateTime.parse(s).toInstant(); } catch (DateTimeParseException ignored) {}
        try { return ZonedDateTime.parse(s).toInstant(); } catch (DateTimeParseException ignored) {}
        return LocalDateTime.parse(s).toInstant(ZoneOffset.UTC);
    }
    private List<JsonNode> array(JsonNode n) {
        List<JsonNode> out = new ArrayList<>(); if (n != null && n.isArray()) n.forEach(out::add); return out;
    }
}
