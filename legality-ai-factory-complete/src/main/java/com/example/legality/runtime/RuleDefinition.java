package com.example.legality.runtime;

import com.fasterxml.jackson.databind.JsonNode;

public class RuleDefinition {
    private final JsonNode node;

    public RuleDefinition(JsonNode node) { this.node = node; }
    public JsonNode raw() { return node; }
    public String ruleId() { return fieldText("ruleId", "UNKNOWN_RULE"); }
    public String ruleName() { return fieldText("ruleName", ruleId()); }
    public String validationStatus() { return fieldText("validationStatus", "UNKNOWN"); }
    public boolean humanReviewRequired() { return fieldBoolean("humanReviewRequired", false); }
    public JsonNode parameters() { return node == null ? null : node.path("parameters"); }
    public JsonNode param(String name) { JsonNode p = parameters(); return p == null ? null : p.path(name); }

    public String param(String name, String fallback) { return getText(name, fallback); }
    public boolean param(String name, boolean fallback) { return getBoolean(name, fallback); }
    public int param(String name, int fallback) { return getInt(name, fallback); }
    public long param(String name, long fallback) { return getLong(name, fallback); }
    public double param(String name, double fallback) { return getDouble(name, fallback); }

    public String getText(String name, String fallback) {
        JsonNode n = param(name);
        return n == null || n.isMissingNode() || n.isNull() ? fallback : n.asText();
    }
    public String getText(String name, boolean fallback) { return getText(name, Boolean.toString(fallback)); }
    public boolean getBoolean(String name, boolean fallback) {
        JsonNode n = param(name);
        if (n == null || n.isMissingNode() || n.isNull()) return fallback;
        if (n.isBoolean()) return n.asBoolean();
        String s = n.asText();
        return "true".equalsIgnoreCase(s) ? true : "false".equalsIgnoreCase(s) ? false : fallback;
    }
    public int getInt(String name, int fallback) {
        JsonNode n = param(name);
        if (n != null && n.isNumber()) return n.asInt();
        if (n != null && n.isTextual()) { try { return Integer.parseInt(n.asText().trim()); } catch (NumberFormatException ignored) {} }
        return fallback;
    }
    public long getLong(String name, long fallback) {
        JsonNode n = param(name);
        if (n != null && n.isNumber()) return n.asLong();
        if (n != null && n.isTextual()) { try { return Long.parseLong(n.asText().trim()); } catch (NumberFormatException ignored) {} }
        return fallback;
    }
    public double getDouble(String name, double fallback) {
        JsonNode n = param(name);
        if (n != null && n.isNumber()) return n.asDouble();
        if (n != null && n.isTextual()) { try { return Double.parseDouble(n.asText().trim()); } catch (NumberFormatException ignored) {} }
        return fallback;
    }

    private String fieldText(String name, String fallback) {
        JsonNode n = node == null ? null : node.path(name);
        return n == null || n.isMissingNode() || n.isNull() ? fallback : n.asText();
    }
    private boolean fieldBoolean(String name, boolean fallback) {
        JsonNode n = node == null ? null : node.path(name);
        return n == null || n.isMissingNode() || n.isNull() ? fallback : n.asBoolean();
    }
}
