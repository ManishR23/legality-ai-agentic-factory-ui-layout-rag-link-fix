package com.example.brdagent;

import com.example.legality.runtime.LegalityRule;
import com.example.legality.runtime.RuleDefinition;
import com.example.legality.runtime.RuleResult;
import com.example.legality.runtime.ScheduleContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class RuleExecutionService {
    private static final Set<String> BLOCKING_VALIDATION_STATUSES = Set.of("INVALID","REJECTED","NEEDS_REVIEW","FAILED");
    private final ObjectMapper mapper=new ObjectMapper();

    public String execute(String normalizedRulesJson,String schedulesJson,List<LegalityRule> compiledRules)throws Exception{
        JsonNode rulesRoot=mapper.readTree(normalizedRulesJson); JsonNode schedRoot=mapper.readTree(schedulesJson);
        List<RuleDefinition> defs=new ArrayList<>(); JsonNode rules=rulesRoot.path("rules"); if(rules.isArray())rules.forEach(r->defs.add(new RuleDefinition(r)));
        JsonNode schedules=schedRoot.path("schedules"); if(!schedules.isArray()) throw new IllegalArgumentException("Schedules JSON must contain schedules array");
        ObjectNode out=mapper.createObjectNode(); out.put("agent","RuleExecutionAgent"); out.put("compiledRuleCount",compiledRules.size()); out.put("ruleDefinitionCount",defs.size());
        ArrayNode srOut=out.putArray("scheduleResults"); int violations=0,errors=0,needsReview=0;
        for(JsonNode s:schedules){
            ScheduleContext ctx=new ScheduleContext(s); ObjectNode sr=srOut.addObject(); sr.put("scheduleId",text(s,"scheduleId","UNKNOWN")); sr.put("crewId",text(s,"crewId","UNKNOWN")); sr.put("base",text(s,"base","UNKNOWN"));
            ArrayNode results=sr.putArray("results");
            for(LegalityRule rule:compiledRules){
                List<RuleDefinition> candidates=matchingDefinitions(rule,defs);
                if(candidates.isEmpty()){
                    RuleResult rr=RuleResult.needsReview(rule.ruleId(),"No matching rule definition found for "+rule.getClass().getSimpleName()+" (ruleId()='"+rule.ruleId()+"'). Not evaluated.");
                    appendResult(results,rule,rr); needsReview++;
                    continue;
                }
                for(RuleDefinition def:candidates){
                    if(blocked(def)){
                        RuleResult rr=RuleResult.needsReview(def.ruleId(),"Skipped: rule definition flagged humanReviewRequired="+def.humanReviewRequired()+", validationStatus="+def.validationStatus()+". Not executed as authoritative.");
                        appendResult(results,rule,rr); needsReview++;
                        continue;
                    }
                    RuleResult rr;
                    try{ rr=rule.evaluate(ctx,def); if(rr==null)rr=RuleResult.error(def.ruleId(),"Rule returned null result"); }
                    catch(Exception ex){ rr=RuleResult.error(def.ruleId(),"Execution error in "+rule.getClass().getSimpleName()+": "+ex.getMessage()); }
                    appendResult(results,rule,rr);
                    if(rr.isViolation())violations++;
                    else if("ERROR".equalsIgnoreCase(rr.getStatus()))errors++;
                    else if("NEEDS_REVIEW".equalsIgnoreCase(rr.getStatus()))needsReview++;
                }
            }
        }
        out.put("violationCount",violations); out.put("errorCount",errors); out.put("needsReviewCount",needsReview);
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(out);
    }

    private void appendResult(ArrayNode results,LegalityRule rule,RuleResult rr){
        ObjectNode r=results.addObject(); r.put("ruleClass",rule.getClass().getName()); r.put("ruleId",rr.getRuleId()); r.put("status",rr.getStatus()); r.put("violation",rr.isViolation()); r.put("message",rr.getMessage()); r.set("metadata",mapper.valueToTree(rr.getMetadata()));
    }

    private boolean blocked(RuleDefinition def){
        if(def.humanReviewRequired()) return true;
        String status=def.validationStatus();
        return status!=null && BLOCKING_VALIDATION_STATUSES.contains(status.toUpperCase(Locale.ROOT));
    }

    private List<RuleDefinition> matchingDefinitions(LegalityRule rule,List<RuleDefinition> defs){
        List<RuleDefinition> out=new ArrayList<>(); String id=rule.ruleId();
        if(id==null||id.isBlank())return out;
        for(RuleDefinition d:defs) if(id.equalsIgnoreCase(d.ruleId())) out.add(d); return out;
    }
    private String text(JsonNode n,String field,String fallback){ JsonNode v=n==null?null:n.path(field); return v==null||v.isMissingNode()||v.isNull()?fallback:v.asText(); }
}
