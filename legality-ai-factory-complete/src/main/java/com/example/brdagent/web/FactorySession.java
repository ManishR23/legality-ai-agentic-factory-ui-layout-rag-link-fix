package com.example.brdagent.web;

import com.example.brdagent.RagChunk;
import com.example.brdagent.RagKnowledgeBase;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Mutable state for one local factory session, mirroring the fields BrdAgentApp keeps in
 * its Swing text areas. Single-process, single-user, best-effort thread safety (volatile
 * fields) -- this is a local dev server, not a multi-tenant service.
 */
public class FactorySession {
    public volatile String workspace = "factory-workspace";
    public final List<String> sourceFilePaths = new ArrayList<>();
    public final List<String> ragFilePaths = new ArrayList<>();
    public final RagKnowledgeBase ragKb = new RagKnowledgeBase();
    public volatile List<RagChunk> lastRagResults = new ArrayList<>();
    public volatile String ragQuestion = "What United FA rest, duty, reserve, pairing, base, flying and non-flying rules are relevant?";
    public volatile String ragEvidence = "";
    public volatile boolean ragEvidenceLinked = false;

    public volatile String factoryPrompt = "Create a United crew legality AI factory. Use source evidence only; extract testable requirements and do not invent legality rules.";
    public volatile String codingPrompt = "Generate Java 17. One atomic rule per class. Use only com.example.legality.runtime APIs. All legality thresholds come from RuleDefinition parameters.";
    public volatile String schedulePrompt = "Generate 25 United schedules. Bases ORD, DEN, IAH, EWR, SFO, LAX, IAD. Start/end at base, flying and non-flying, pairings at least 2 days with at least 2 flight legs, schedule at least 14 days, include 20% intentionally illegal examples.";

    public volatile String requirements = "";
    public volatile String validation = "";
    public volatile String brd = "";
    public volatile String normalized = "";
    public volatile String codeJson = "";
    public volatile int materializedFileCount = -1;
    public volatile String schedulesJson = "";
    public volatile String executionJson = "";
    public volatile String explanation = "";

    public volatile int maxFixAttempts = 3;
    public final List<CompileAttempt> compileHistory = new ArrayList<>();

    public Path workspacePath() { return Path.of(workspace); }

    public record CompileAttempt(int attempt, int errors, int filesChanged, String result, String diagnostics) {}
}
