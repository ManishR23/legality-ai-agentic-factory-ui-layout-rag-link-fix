package com.example.legality.runtime;

public interface LegalityRule {
    default String ruleId() { return ""; }
    RuleResult evaluate(ScheduleContext ctx, RuleDefinition rule);
}
