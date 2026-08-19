package com.example.brdagent;

import com.example.legality.runtime.LegalityRule;
import com.example.legality.runtime.RuleDefinition;
import com.example.legality.runtime.RuleResult;
import com.example.legality.runtime.ScheduleContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the "core five" execution-engine fixes: rule/definition matching no longer fans
 * an unmatched rule across every catalog entry, human-review-flagged definitions are not
 * executed as authoritative, and a golden legal/illegal schedule pair produces the correct
 * PASS/VIOLATION verdict end to end through RuleExecutionService.
 */
class RuleExecutionServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final RuleExecutionService service = new RuleExecutionService();

    /** Rest-period rule keyed to catalog id MIN_REST. Reads dutyEnd/nextDutyStart off the raw schedule node. */
    static class MinRestRule implements LegalityRule {
        public String ruleId() { return "MIN_REST"; }
        public RuleResult evaluate(ScheduleContext ctx, RuleDefinition def) {
            double minRest = def.getDouble("minRestHours", 10.0);
            String dutyEnd = ctx.getText("dutyEnd", null);
            String nextStart = ctx.getText("nextDutyStart", null);
            double actual = ctx.hoursBetween(dutyEnd, nextStart);
            if (actual < minRest) return RuleResult.violation(def.ruleId(), "Rest " + actual + "h < required " + minRest + "h");
            return RuleResult.pass(def.ruleId());
        }
    }

    /** Deliberately does not override ruleId() — simulates LLM-generated code that forgot to. */
    static class UnidentifiedRule implements LegalityRule {
        public RuleResult evaluate(ScheduleContext ctx, RuleDefinition def) { return RuleResult.pass(def.ruleId()); }
    }

    static class ThrowingRule implements LegalityRule {
        public String ruleId() { return "CRASHY"; }
        public RuleResult evaluate(ScheduleContext ctx, RuleDefinition def) { throw new NullPointerException("boom"); }
    }

    private String catalog(String ruleJson) {
        return "{\"rules\":[" + ruleJson + "]}";
    }

    private String schedule(String dutyEnd, String nextDutyStart) {
        return "{\"schedules\":[{\"scheduleId\":\"S1\",\"crewId\":\"C1\",\"base\":\"ORD\",\"dutyEnd\":\"" + dutyEnd + "\",\"nextDutyStart\":\"" + nextDutyStart + "\"}]}";
    }

    @Test
    void illegalScheduleReportsViolation() throws Exception {
        String rules = catalog("{\"ruleId\":\"MIN_REST\",\"parameters\":{\"minRestHours\":10}}");
        String schedules = schedule("2026-03-14T08:00:00", "2026-03-14T13:00:00"); // 5h rest
        String out = service.execute(rules, schedules, List.of(new MinRestRule()));
        JsonNode root = mapper.readTree(out);
        assertEquals(1, root.path("violationCount").asInt());
        assertEquals(0, root.path("errorCount").asInt());
        assertEquals("VIOLATION", root.at("/scheduleResults/0/results/0/status").asText());
    }

    @Test
    void legalScheduleReportsPass() throws Exception {
        String rules = catalog("{\"ruleId\":\"MIN_REST\",\"parameters\":{\"minRestHours\":10}}");
        String schedules = schedule("2026-03-14T08:00:00", "2026-03-15T08:00:00"); // 24h rest
        String out = service.execute(rules, schedules, List.of(new MinRestRule()));
        JsonNode root = mapper.readTree(out);
        assertEquals(0, root.path("violationCount").asInt());
        assertEquals("PASS", root.at("/scheduleResults/0/results/0/status").asText());
    }

    @Test
    void quotedThresholdIsHonoredNotSilentlyDefaulted() throws Exception {
        // minRestHours is a quoted string in the catalog, as LLM output routinely emits.
        String rules = catalog("{\"ruleId\":\"MIN_REST\",\"parameters\":{\"minRestHours\":\"20\"}}");
        // 12h rest is legal under the 10h hardcoded fallback but illegal under the real 20h parameter.
        String schedules = schedule("2026-03-14T08:00:00", "2026-03-14T20:00:00");
        String out = service.execute(rules, schedules, List.of(new MinRestRule()));
        JsonNode root = mapper.readTree(out);
        assertEquals(1, root.path("violationCount").asInt());
    }

    @Test
    void unmatchedRuleIsNotFannedOutAcrossEveryDefinition() throws Exception {
        String rules = catalog(
                "{\"ruleId\":\"MIN_REST\",\"parameters\":{\"minRestHours\":10}},"
                        + "{\"ruleId\":\"MAX_DUTY\",\"parameters\":{\"maxDutyHours\":14}},"
                        + "{\"ruleId\":\"RESERVE\",\"parameters\":{}}");
        String schedules = schedule("2026-03-14T08:00:00", "2026-03-15T08:00:00");
        String out = service.execute(rules, schedules, List.of(new UnidentifiedRule()));
        JsonNode root = mapper.readTree(out);
        JsonNode results = root.at("/scheduleResults/0/results");
        assertEquals(1, results.size(), "an unmatched rule must produce exactly one NEEDS_REVIEW row, not one per catalog definition");
        assertEquals("NEEDS_REVIEW", results.get(0).path("status").asText());
        assertEquals(0, root.path("violationCount").asInt());
        assertEquals(1, root.path("needsReviewCount").asInt());
    }

    @Test
    void humanReviewRequiredDefinitionIsNotExecutedAsAuthoritative() throws Exception {
        String rules = catalog("{\"ruleId\":\"MIN_REST\",\"humanReviewRequired\":true,\"parameters\":{\"minRestHours\":10}}");
        String schedules = schedule("2026-03-14T08:00:00", "2026-03-14T09:00:00"); // 1h rest -- would be a clear violation
        String out = service.execute(rules, schedules, List.of(new MinRestRule()));
        JsonNode root = mapper.readTree(out);
        assertEquals(0, root.path("violationCount").asInt(), "a flagged-for-review definition must not be executed as authoritative");
        assertEquals(1, root.path("needsReviewCount").asInt());
        assertEquals("NEEDS_REVIEW", root.at("/scheduleResults/0/results/0/status").asText());
    }

    @Test
    void crashedRuleReportsErrorNotViolation() throws Exception {
        String rules = catalog("{\"ruleId\":\"CRASHY\",\"parameters\":{}}");
        String schedules = schedule("2026-03-14T08:00:00", "2026-03-15T08:00:00");
        String out = service.execute(rules, schedules, List.of(new ThrowingRule()));
        JsonNode root = mapper.readTree(out);
        assertEquals(0, root.path("violationCount").asInt(), "an execution crash must not be counted as a legality violation");
        assertEquals(1, root.path("errorCount").asInt());
        assertEquals("ERROR", root.at("/scheduleResults/0/results/0/status").asText());
    }
}
