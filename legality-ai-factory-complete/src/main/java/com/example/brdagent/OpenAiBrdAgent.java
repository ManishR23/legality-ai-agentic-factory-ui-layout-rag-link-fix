package com.example.brdagent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

public class OpenAiBrdAgent {
    private final String apiKey;
    private final String model;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();

    public OpenAiBrdAgent(String apiKey, String model) {
        this.apiKey = apiKey; this.model = model;
    }

    public String generateRequirementsJson(List<SourceDocument> docs, String guidance) throws Exception {
        StringBuilder src = new StringBuilder();
        for (SourceDocument d : docs) src.append("\n--- FILE: ").append(d.fileName()).append(" ---\n").append(d.content()).append('\n');
        return call("You are a senior requirements analyst for transportation legality systems. Return only valid JSON.",
                "Extract atomic, testable requirements and business rules. Do not invent rules. Mark uncertainty.\nUser guidance:\n" + safe(guidance) +
                "\nReturn JSON with: agent, summary, businessProblem, goals, stakeholders, functionalRequirements, nonFunctionalRequirements, businessRules, dataRequirements, assumptions, risks, openQuestions. Business rules need id, ruleText, parameters, applicability, sourceRefs, extractionConfidence.\nSOURCE FILES:\n" + src, 0.1);
    }

    public String validateRulesAndConfidence(String req, String guidance) throws Exception {
        return call("You are a Rule Validation and Confidence Agent. Return only valid JSON.",
                "Validate numeric parameters, applicability, traceability, missing parameters, conflicts, and confidence. Do not invent source facts.\nGuidance:\n"+safe(guidance)+
                "\nReturn JSON with agent, overallStatus, overallConfidence, validatedRules, validatedRequirements, conflicts, gaps, recommendationsForNextAgent.\nINPUT:\n"+req,0.05);
    }

    public String generateBrdFromArtifacts(String req, String val, String guidance) throws Exception {
        return call("You are a senior Business Analyst.", "Create an implementation-ready BRD in Markdown from the supplied artifacts. Include scope, stakeholders, functional/NFR/data requirements, business rules with validation/confidence, assumptions, risks, TBDs, traceability.\nGuidance:\n"+safe(guidance)+"\nREQUIREMENTS:\n"+req+"\nVALIDATION:\n"+val,0.15);
    }

    public String normalizeRulesFromBrd(String brd, String req, String val, String guidance) throws Exception {
        return call("You are a Rule Normalization Agent. Return only valid JSON.",
                "Convert the BRD into deterministic machine-readable atomic rules. Do not invent thresholds. Return JSON with agent, catalogVersion, domain, rules, dataElements, normalizationIssues, recommendationsForRuleCodingAgent. Each rule: ruleId, ruleName, ruleType, sourceRefs, applicability, parameters, logic, severity, confidence, validationStatus, humanReviewRequired, testIdeas.\nBRD:\n"+brd+"\nREQ:\n"+req+"\nVALIDATION:\n"+val+"\nGUIDANCE:\n"+safe(guidance),0.05);
    }

    public String generateRuleCodeFromCatalog(String brd, String normalized, String codingPrompt) throws Exception {
        return call("You are a senior Java Rule Coding Agent. Return only valid JSON.",
                "Generate Java 17 rule files from normalized rule JSON. STRICT CONTRACT:\n"+
                "- package com.example.legality.generated\n- import com.example.legality.runtime.* and JsonNode only when needed\n"+
                "- Every rule implements LegalityRule and evaluate(ScheduleContext ctx, RuleDefinition rule)\n"+
                "- MANDATORY: every rule class must override public String ruleId() to return the exact ruleId string from its normalized rule catalog entry. The rule execution engine matches compiled rules to rule definitions by this id; a rule that does not override ruleId() is never matched to any definition and is skipped rather than evaluated.\n"+
                "- Do not invent domain classes. Allowed runtime: LegalityRule, RuleDefinition, RuleResult, ScheduleContext, JsonNode, java.time/java.util.\n"+
                "- EVERY numeric or time threshold used in a comparison (hours, counts, dates, limits) MUST be read from RuleDefinition at evaluation time via rule.param(name, fallback) or rule.param(name, Type.class) -- e.g. double minRest = rule.param(\"minRestHours\", 10.0); NEVER write the legality threshold itself as a Java literal (e.g. `if (hours < 10.0)` is forbidden; `if (hours < rule.param(\"minRestHours\", 10.0))` is required). The literal after the comma is only a fallback for when the catalog omits the parameter, not the enforced value.\n"+
                "- RuleDefinition.param(String name, Class<T> type) supports T = String, Boolean, Integer, Long, Double, java.time.Instant, java.time.LocalDate, java.time.LocalTime, java.time.LocalDateTime, and returns null if the parameter is absent or unparseable -- always null-check before use and return RuleResult.needsReview(rule.ruleId(), \"...\") when null. No other type argument is supported.\n"+
                "- RuleResult factory methods take the rule id FIRST and the human-readable message SECOND: RuleResult.pass(rule.ruleId(), \"message\"), RuleResult.violation(rule.ruleId(), \"message\"), RuleResult.needsReview(rule.ruleId(), \"message\"). Never pass the message as the first argument or an empty string as the second.\n"+
                "- CRITICAL DATA SOURCE RULE: rule (RuleDefinition) holds ONLY this rule's static catalog configuration -- thresholds, limits, flags such as minRestHours or maxDutyHours. It NEVER contains per-schedule instance data. ctx (ScheduleContext) holds the schedule being evaluated -- every actual report time, release time, flight leg, activity, and date comes from ctx, never from rule.param(...). A rule that calls rule.param(\"reportTime\", ...) or rule.param(\"releaseTime\", ...) etc. to get schedule instance data is wrong; that data does not exist on the rule definition and the call will always return null.\n"+
                "- Schedule instance timestamps (reportTime, releaseTime, startDateTime, endDateTime, departureTime, arrivalTime) are ISO-8601 date-times, either bare (\"2024-07-01T06:00:00\") or with an offset (\"2024-07-01T06:00:00-05:00\"). Extract them as raw strings via JsonNode.path(fieldName).asText() and pass those strings directly to ctx.hoursBetween(startIso, endIso), which handles both forms correctly -- do not parse them yourself with Instant.parse or LocalDateTime.parse.\n"+
                "- To count or filter activities of a given type within a pairing (flight legs, layovers, training, etc.), iterate pairing.path(\"duties\") then duty.path(\"activities\") and match on activity.path(\"type\").asText() case-insensitively. There is no shortcut top-level field like pairing.flightLegs or pairing.legs -- it does not exist in the schedule schema; inventing one produces a rule that always reads zero.\n"+
                "- Worked example for a rule comparing adjacent duty periods (e.g. minimum rest between duties), showing the correct division between rule (threshold only) and ctx (schedule data):\n"+
                "```java\n"+
                "public RuleResult evaluate(ScheduleContext ctx, RuleDefinition rule) {\n"+
                "    double minRestHours = rule.param(\"minRestHours\", 10.0); // threshold: from rule\n"+
                "    java.util.List<JsonNode> duties = new java.util.ArrayList<>();\n"+
                "    for (JsonNode pairing : ctx.pairings())\n"+
                "        for (JsonNode duty : pairing.path(\"duties\")) duties.add(duty); // instance data: from ctx\n"+
                "    for (int i = 1; i < duties.size(); i++) {\n"+
                "        String previousRelease = duties.get(i - 1).path(\"releaseTime\").asText();\n"+
                "        String nextReport = duties.get(i).path(\"reportTime\").asText();\n"+
                "        double restHours = ctx.hoursBetween(previousRelease, nextReport);\n"+
                "        if (restHours < minRestHours) return RuleResult.violation(rule.ruleId(), \"Rest \"+restHours+\"h < required \"+minRestHours+\"h\");\n"+
                "    }\n"+
                "    return RuleResult.pass(rule.ruleId(), \"All duty-to-duty rest periods satisfy minimum \"+minRestHours+\"h\");\n"+
                "}\n"+
                "```\n"+
                "Worked example for a rule that counts/filters activities by type within each pairing (e.g. minimum flight legs per pairing):\n"+
                "```java\n"+
                "public RuleResult evaluate(ScheduleContext ctx, RuleDefinition rule) {\n"+
                "    int minFlightLegs = rule.param(\"minimumFlightLegs\", 2); // threshold: from rule\n"+
                "    for (JsonNode pairing : ctx.pairings()) {\n"+
                "        int flightLegCount = 0;\n"+
                "        for (JsonNode duty : pairing.path(\"duties\"))\n"+
                "            for (JsonNode activity : duty.path(\"activities\"))\n"+
                "                if (\"FLIGHT\".equalsIgnoreCase(activity.path(\"type\").asText())) flightLegCount++; // instance data: from ctx\n"+
                "        if (flightLegCount < minFlightLegs) return RuleResult.violation(rule.ruleId(), \"Pairing \"+pairing.path(\"pairingId\").asText()+\" has \"+flightLegCount+\" flight legs, fewer than required \"+minFlightLegs);\n"+
                "    }\n"+
                "    return RuleResult.pass(rule.ruleId(), \"All pairings meet minimum \"+minFlightLegs+\" flight legs\");\n"+
                "}\n"+
                "```\n"+
                "Adapt these patterns (walk ctx.pairings()/duties()/activities() for instance data, rule.param(...) only for thresholds) to each rule's actual logic; do not copy either verbatim where it does not fit.\n"+
                "- do not reinterpret rules. Incomplete rules return needsReview.\n"+
                "Return JSON: {agent,packageName,generatedFiles:[{fileName,fileType,ruleIds,sourceCode,notes}],compileRisks}.\nCODING PROMPT:\n"+safe(codingPrompt)+"\nBRD CONTEXT:\n"+brd+"\nNORMALIZED RULES:\n"+normalized,0.02);
    }

    public String generateTestSchedules(String prompt, String normalized, String brd) throws Exception {
        return call("You are a Test Scenario Agent. Return only valid JSON.",
                "Generate schedule test data, not legality decisions. Follow user constraints. Defaults: United bases ORD,DEN,IAH,EWR,SFO,LAX,IAD; start/end at base; flying and non-flying; pairings >=2 days; each pairing >=2 flight legs; schedule >=14 days; include legal and intentionally illegal cases. Return {agent,schedules:[{scheduleId,crewId,crewType,base,scheduleStart,scheduleEnd,pairings:[{pairingId,startBase,endBase,startDateTime,endDateTime,duties:[{dutyId,reportTime,releaseTime,activities:[...]}]}],nonFlyingActivities:[...],expectedViolations:[...]}]}.\nPROMPT:\n"+safe(prompt)+"\nRULE CATALOG CONTEXT:\n"+normalized+"\nBRD:\n"+brd,0.2);
    }

    public String fixCompileErrors(String diagnostics, String generatedSourceBundle, String normalizedRules) throws Exception {
        return call("You are a Java Compile Fix Agent. Return only valid JSON. You are forbidden from changing legality semantics.",
                "Fix ONLY Java compilation problems. Immutable legality semantics: do not change thresholds, rule conditions, applicability, PASS/VIOLATION intent, or create/remove rules. You MAY fix syntax, imports, types, constructors, method signatures, and adapt calls to the approved runtime API. If a compile fix would require semantic change, return HUMAN_REVIEW_REQUIRED.\n"+
                "APPROVED API:\nLegalityRule: default String ruleId(); RuleResult evaluate(ScheduleContext,RuleDefinition)\n"+
                "RuleResult: pass(String), pass(String,String), violation(String,String), needsReview(String,String), error(String,String), with(String,Object)\n"+
                "RuleDefinition: ruleId(),ruleName(),validationStatus(),humanReviewRequired(),raw(),param(String), param(String,String|boolean|int|long|double fallback), param(String name, Class<T> type) for T in {String,Boolean,Integer,Long,Double,java.time.Instant,java.time.LocalDate,java.time.LocalTime,java.time.LocalDateTime} returning null if absent/unparseable, getText/getBoolean/getInt/getLong/getDouble. No other param(...) signature exists -- if generated code calls param with any other type argument, replace it with one of these.\n"+
                "ScheduleContext: raw(),at(path),getText/getBoolean/getInt/getDouble,pairings(),activities(),flightActivities(),nonFlyingActivities(),hoursBetween(start,end)\n"+
                "Return JSON: {status:'FIXED|HUMAN_REVIEW_REQUIRED', fixes:[{fileName,sourceCode,reason,legalityImpact:'NONE'}], humanReview:[...]}. Provide complete replacement source for changed files only.\n"+
                "COMPILER DIAGNOSTICS:\n"+diagnostics+"\nGENERATED SOURCES:\n"+generatedSourceBundle+"\nNORMALIZED RULES (source of semantics):\n"+normalizedRules,0.0);
    }

    public String explainWithGroundedData(String type, String selectedJson, String formatted, String normalized, String execution) throws Exception {
        return call("You are a grounded legality explanation agent.",
                "Explain exhaustively using ONLY supplied data. Deterministic legality engine is source of truth. Do not invent, add, reinterpret or override rules/status. If data is missing, say so. Separate facts from observations.\nTYPE:"+type+"\nSELECTED:\n"+selectedJson+"\nFORMATTED:\n"+formatted+"\nRULE CATALOG:\n"+normalized+"\nENGINE OUTPUT:\n"+execution,0.05);
    }

    private String call(String system, String user, double temperature) throws Exception {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException("OPENAI_API_KEY missing in .env/environment");
        ObjectNode root=mapper.createObjectNode(); root.put("model",model); root.put("temperature",temperature);
        ArrayNode msgs=root.putArray("messages"); msgs.addObject().put("role","system").put("content",system); msgs.addObject().put("role","user").put("content",user);
        HttpRequest req=HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/chat/completions")).timeout(Duration.ofMinutes(5)).header("Authorization","Bearer "+apiKey).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(root), StandardCharsets.UTF_8)).build();
        HttpResponse<String> resp=http.send(req,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if(resp.statusCode()<200||resp.statusCode()>=300) throw new RuntimeException("OpenAI API error "+resp.statusCode()+": "+resp.body());
        JsonNode c=mapper.readTree(resp.body()).path("choices").path(0).path("message").path("content");
        String text=c.isMissingNode()?"":c.asText(); if(text.isBlank()) throw new RuntimeException("Empty model response");
        text=stripFence(text.trim());
        try { return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readTree(text)); } catch(Exception ignored){ return text; }
    }
    private String stripFence(String s){ if(s.startsWith("```")){ s=s.replaceFirst("^```[a-zA-Z0-9_-]*\\s*",""); s=s.replaceFirst("\\s*```$",""); } return s.trim(); }
    private String safe(String s){ return s==null||s.isBlank()?"None":s; }
}
