package com.example.legality.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

public class RuleResult {
    private final String ruleId;
    private final String status;
    private final boolean violation;
    private final String message;
    private final Map<String,Object> metadata = new LinkedHashMap<>();

    public RuleResult(String ruleId, String status, boolean violation, String message) {
        this.ruleId = blank(ruleId) ? "UNKNOWN_RULE" : ruleId;
        this.status = blank(status) ? "UNKNOWN" : status;
        this.violation = violation;
        this.message = message == null ? "" : message;
    }

    public static RuleResult pass(String ruleId) { return new RuleResult(ruleId, "PASS", false, "Pass"); }
    public static RuleResult pass(String ruleId, String message) { return new RuleResult(ruleId, "PASS", false, message); }
    public static RuleResult ok() { return pass("UNKNOWN_RULE"); }
    public static RuleResult ok(String ruleId) { return pass(ruleId); }
    public static RuleResult ok(String ruleId, String message) { return pass(ruleId, message); }
    public static RuleResult violation(String ruleId, String message) { return new RuleResult(ruleId, "VIOLATION", true, message); }
    public static RuleResult violation(String ruleId, String code, String message) { return violation(ruleId, message).with("code", code); }
    public static RuleResult needsReview(String ruleId, String message) { return new RuleResult(ruleId, "NEEDS_REVIEW", false, message); }
    public static RuleResult error(String ruleId, String message) { return new RuleResult(ruleId, "ERROR", false, message); }

    public RuleResult with(String key, Object value) { metadata.put(key, value); return this; }
    public String getRuleId() { return ruleId; }
    public String getStatus() { return status; }
    public boolean isViolation() { return violation; }
    public String getMessage() { return message; }
    public Map<String,Object> getMetadata() { return metadata; }

    private static boolean blank(String s) { return s == null || s.trim().isEmpty(); }
}
