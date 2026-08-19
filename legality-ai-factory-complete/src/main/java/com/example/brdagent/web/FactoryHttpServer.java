package com.example.brdagent.web;

import com.example.brdagent.DocumentExtractor;
import com.example.brdagent.EnvConfig;
import com.example.brdagent.GeneratedJavaManager;
import com.example.brdagent.OpenAiBrdAgent;
import com.example.brdagent.RagChunk;
import com.example.brdagent.RuleExecutionService;
import com.example.brdagent.SourceDocument;
import com.example.legality.runtime.LegalityRule;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

/**
 * Local HTTP server exposing the same pipeline classes BrdAgentApp drives from Swing, as a
 * JSON API, plus static file serving for frontend/. Runs alongside the Swing app -- neither
 * touches the other. Single local user, no auth: not for exposure beyond localhost.
 */
public class FactoryHttpServer {
    private final ObjectMapper mapper = new ObjectMapper();
    private final FactorySession session = new FactorySession();
    private final GeneratedJavaManager javaManager = new GeneratedJavaManager();
    private final RuleExecutionService executionService = new RuleExecutionService();
    private final Path frontendRoot;

    public FactoryHttpServer(Path frontendRoot) {
        this.frontendRoot = frontendRoot;
    }

    public HttpServer start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.createContext("/api/", this::handleApi);
        server.createContext("/", this::handleStatic);
        server.start();
        return server;
    }

    // ===================== static file serving =====================

    private void handleStatic(HttpExchange ex) throws IOException {
        try {
            String reqPath = ex.getRequestURI().getPath();
            if (reqPath.equals("/")) reqPath = "/index.html";
            Path file = frontendRoot.resolve(reqPath.substring(1)).normalize();
            if (!file.startsWith(frontendRoot) || !Files.isRegularFile(file)) {
                sendBytes(ex, 404, "text/plain; charset=utf-8", "Not found".getBytes(StandardCharsets.UTF_8));
                return;
            }
            String ct = contentType(file.getFileName().toString());
            sendBytes(ex, 200, ct, Files.readAllBytes(file));
        } catch (Exception e) {
            sendBytes(ex, 500, "text/plain; charset=utf-8", ("Static file error: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }

    private String contentType(String fileName) {
        String n = fileName.toLowerCase(Locale.ROOT);
        if (n.endsWith(".html")) return "text/html; charset=utf-8";
        if (n.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (n.endsWith(".css")) return "text/css; charset=utf-8";
        if (n.endsWith(".json")) return "application/json; charset=utf-8";
        if (n.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }

    // ===================== API routing =====================

    private void handleApi(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        try {
            JsonNode body = readBody(ex);
            ObjectNode result;
            switch (path) {
                case "/api/state" -> { sendJson(ex, 200, stateJson()); return; }
                case "/api/workspace" -> { session.workspace = text(body, "path", session.workspace).trim(); result = stateJson(); }
                case "/api/sources" -> result = addSource(body);
                case "/api/sources/remove" -> result = removeSource(body);
                case "/api/rag/docs" -> result = addRagDoc(body);
                case "/api/rag/docs/remove" -> result = removeRagDoc(body);
                case "/api/rag/build" -> result = buildKb();
                case "/api/rag/search" -> result = searchKb(body);
                case "/api/rag/evidence" -> result = createEvidence();
                case "/api/rag/use-evidence" -> result = useEvidence();
                case "/api/prompts" -> result = updatePrompts(body);
                case "/api/requirements" -> result = runRequirements();
                case "/api/validation" -> result = runValidation();
                case "/api/brd" -> result = runBrd();
                case "/api/normalize" -> result = runNormalize();
                case "/api/code" -> result = runCode();
                case "/api/run-all" -> result = runAll();
                case "/api/materialize" -> result = materialize();
                case "/api/compile" -> result = compileOnly();
                case "/api/compile-autofix" -> result = compileAutoFix(body);
                case "/api/schedules" -> result = generateSchedules();
                case "/api/execute" -> result = executeRules();
                case "/api/explain" -> result = explain(body);
                case "/api/save-artifacts" -> result = saveArtifacts();
                default -> { sendError(ex, 404, "No such endpoint: " + method + " " + path); return; }
            }
            sendJson(ex, 200, result);
        } catch (IllegalStateException | IllegalArgumentException e) {
            sendError(ex, 400, e.getMessage());
        } catch (Exception e) {
            Throwable t = e;
            while (t.getCause() != null) t = t.getCause();
            sendError(ex, 500, t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    // ===================== RAG =====================

    private ObjectNode addSource(JsonNode body) throws Exception {
        String p = text(body, "path", "");
        if (p.isBlank()) throw new IllegalArgumentException("path is required");
        if (!Files.exists(Path.of(p))) throw new IllegalArgumentException("File does not exist: " + p);
        session.sourceFilePaths.add(p);
        return stateJson();
    }
    private ObjectNode removeSource(JsonNode body) {
        int i = body.path("index").asInt(-1);
        if (i >= 0 && i < session.sourceFilePaths.size()) session.sourceFilePaths.remove(i);
        return stateJson();
    }
    private ObjectNode addRagDoc(JsonNode body) throws Exception {
        String p = text(body, "path", "");
        if (p.isBlank()) throw new IllegalArgumentException("path is required");
        if (!Files.exists(Path.of(p))) throw new IllegalArgumentException("File does not exist: " + p);
        session.ragFilePaths.add(p);
        return stateJson();
    }
    private ObjectNode removeRagDoc(JsonNode body) {
        int i = body.path("index").asInt(-1);
        if (i >= 0 && i < session.ragFilePaths.size()) session.ragFilePaths.remove(i);
        return stateJson();
    }

    private ObjectNode buildKb() throws Exception {
        if (session.ragFilePaths.isEmpty()) throw new IllegalStateException("Add FA source docs first");
        session.ragKb.clear();
        DocumentExtractor ex = new DocumentExtractor();
        for (String p : session.ragFilePaths) {
            SourceDocument d = ex.extract(new File(p));
            session.ragKb.addDocument(d.fileName(), d.content());
        }
        Path saveTo = session.workspacePath().resolve("fa-rag/fa-knowledge-base.json");
        session.ragKb.save(saveTo);
        return stateJson();
    }

    private ObjectNode searchKb(JsonNode body) {
        session.ragQuestion = text(body, "question", session.ragQuestion);
        session.lastRagResults = session.ragKb.search(session.ragQuestion, 12);
        ObjectNode out = stateJson();
        return out;
    }

    private ObjectNode createEvidence() {
        if (session.lastRagResults.isEmpty()) session.lastRagResults = session.ragKb.search(session.ragQuestion, 12);
        session.ragEvidence = session.ragKb.evidencePackage(session.ragQuestion, session.lastRagResults);
        return stateJson();
    }

    private ObjectNode useEvidence() {
        if (session.ragEvidence.isBlank()) {
            if (session.lastRagResults.isEmpty()) session.lastRagResults = session.ragKb.search(session.ragQuestion, 12);
            session.ragEvidence = session.ragKb.evidencePackage(session.ragQuestion, session.lastRagResults);
        }
        if (session.ragEvidence.isBlank() || session.lastRagResults.isEmpty())
            throw new IllegalStateException("No RAG evidence available -- search the knowledge base first");
        session.ragEvidenceLinked = true;
        return stateJson();
    }

    // ===================== prompts / pipeline =====================

    private ObjectNode updatePrompts(JsonNode body) {
        if (body.has("factoryPrompt")) session.factoryPrompt = body.path("factoryPrompt").asText();
        if (body.has("codingPrompt")) session.codingPrompt = body.path("codingPrompt").asText();
        if (body.has("schedulePrompt")) session.schedulePrompt = body.path("schedulePrompt").asText();
        return stateJson();
    }

    private String combinedFactoryPrompt() {
        String base = session.factoryPrompt == null ? "" : session.factoryPrompt;
        if (!session.ragEvidenceLinked || session.ragEvidence == null || session.ragEvidence.isBlank()) return base;
        return base + "\n\n===== RETRIEVED UNITED FA EVIDENCE - USE AS SOURCE EVIDENCE ONLY =====\n" + session.ragEvidence +
                "\n===== END RETRIEVED EVIDENCE =====\n" +
                "The RAG evidence is retrieval evidence only. Do not invent rules beyond the supplied evidence. Final legality execution is determined only by the deterministic legality engine.";
    }

    private List<SourceDocument> docs() throws Exception {
        List<SourceDocument> out = new ArrayList<>();
        DocumentExtractor ex = new DocumentExtractor();
        for (String p : session.sourceFilePaths) out.add(ex.extract(new File(p)));
        if (out.isEmpty() && session.ragEvidenceLinked && !session.ragEvidence.isBlank())
            out.add(new SourceDocument("United_FA_RAG_Evidence.md", session.ragEvidence));
        if (out.isEmpty()) throw new IllegalStateException("Add source files, or link FA RAG evidence, before running Requirements");
        return out;
    }

    private OpenAiBrdAgent agent() {
        EnvConfig e = new EnvConfig();
        if (e.openAiKey().isBlank()) throw new IllegalStateException("OPENAI_API_KEY missing -- add it to .env next to this server's working directory");
        return new OpenAiBrdAgent(e.openAiKey(), e.model());
    }

    private void require(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalStateException(message);
    }

    private ObjectNode runRequirements() throws Exception {
        session.requirements = agent().generateRequirementsJson(docs(), combinedFactoryPrompt());
        return stateJson();
    }
    private ObjectNode runValidation() throws Exception {
        require(session.requirements, "Run Requirements first");
        session.validation = agent().validateRulesAndConfidence(session.requirements, combinedFactoryPrompt());
        return stateJson();
    }
    private ObjectNode runBrd() throws Exception {
        require(session.requirements, "Need requirements"); require(session.validation, "Need validation");
        session.brd = agent().generateBrdFromArtifacts(session.requirements, session.validation, combinedFactoryPrompt());
        return stateJson();
    }
    private ObjectNode runNormalize() throws Exception {
        require(session.brd, "Need BRD");
        session.normalized = agent().normalizeRulesFromBrd(session.brd, session.requirements, session.validation, combinedFactoryPrompt());
        return stateJson();
    }
    private ObjectNode runCode() throws Exception {
        require(session.normalized, "Need normalized rules");
        session.codeJson = agent().generateRuleCodeFromCatalog(session.brd, session.normalized, session.codingPrompt);
        return stateJson();
    }
    private ObjectNode runAll() throws Exception {
        OpenAiBrdAgent a = agent();
        session.requirements = a.generateRequirementsJson(docs(), combinedFactoryPrompt());
        session.validation = a.validateRulesAndConfidence(session.requirements, combinedFactoryPrompt());
        session.brd = a.generateBrdFromArtifacts(session.requirements, session.validation, combinedFactoryPrompt());
        session.normalized = a.normalizeRulesFromBrd(session.brd, session.requirements, session.validation, combinedFactoryPrompt());
        session.codeJson = a.generateRuleCodeFromCatalog(session.brd, session.normalized, session.codingPrompt);
        return stateJson();
    }

    private ObjectNode materialize() throws Exception {
        require(session.codeJson, "Need Rule Code JSON");
        session.materializedFileCount = javaManager.materializeRuleCodingJson(session.workspacePath(), session.codeJson);
        return stateJson();
    }

    private int countErrors(GeneratedJavaManager.CompileResult r) {
        if (r.success()) return 0;
        String s = r.diagnostics();
        if (s == null || s.isBlank()) return 1;
        int n = 0;
        for (String l : s.split("\\R")) if (l.contains("[ERROR]")) n++;
        return n == 0 ? 1 : n;
    }

    private ObjectNode compileOnly() throws Exception {
        GeneratedJavaManager.CompileResult r = javaManager.compileGeneratedRules(session.workspacePath());
        int errors = countErrors(r);
        session.compileHistory.clear();
        session.compileHistory.add(new FactorySession.CompileAttempt(1, errors, 0, r.success() ? "PASSED" : "FAILED", r.diagnostics()));
        if (!r.success()) throw new IllegalStateException(r.diagnostics());
        return stateJson();
    }

    private ObjectNode compileAutoFix(JsonNode body) throws Exception {
        int max = body.has("maxAttempts") ? body.path("maxAttempts").asInt(session.maxFixAttempts) : session.maxFixAttempts;
        session.compileHistory.clear();
        OpenAiBrdAgent a = agent();
        for (int attempt = 1; attempt <= max; attempt++) {
            GeneratedJavaManager.CompileResult r = javaManager.compileGeneratedRules(session.workspacePath());
            int errors = countErrors(r);
            if (r.success()) {
                session.compileHistory.add(new FactorySession.CompileAttempt(attempt, 0, 0, "PASSED", r.diagnostics()));
                return stateJson();
            }
            String fix = a.fixCompileErrors(r.diagnostics(), javaManager.sourceBundle(session.workspacePath()), session.normalized);
            int changed = javaManager.applyCompileFixJson(session.workspacePath(), fix);
            session.compileHistory.add(new FactorySession.CompileAttempt(attempt, errors, changed,
                    changed > 0 ? "FIXED / RETRY" : "HUMAN REVIEW", r.diagnostics() + "\n\nFIX AGENT OUTPUT\n" + fix));
            if (changed == 0) throw new IllegalStateException("Compile Fix Agent requires human review -- see attempt history");
        }
        GeneratedJavaManager.CompileResult finalR = javaManager.compileGeneratedRules(session.workspacePath());
        if (finalR.success()) {
            session.compileHistory.add(new FactorySession.CompileAttempt(max + 1, 0, 0, "PASSED", finalR.diagnostics()));
            return stateJson();
        }
        throw new IllegalStateException("Still failing after " + max + " fix attempts. See compile history.");
    }

    private ObjectNode generateSchedules() throws Exception {
        require(session.normalized, "Need normalized rules");
        session.schedulesJson = agent().generateTestSchedules(session.schedulePrompt, session.normalized, session.brd);
        Files.writeString(session.workspacePath().resolve("test-schedules.json"), session.schedulesJson);
        return stateJson();
    }

    private ObjectNode executeRules() throws Exception {
        require(session.normalized, "Need normalized rules"); require(session.schedulesJson, "Need schedules");
        List<LegalityRule> rules = javaManager.loadCompiledRules(session.workspacePath());
        session.executionJson = executionService.execute(session.normalized, session.schedulesJson, rules);
        Files.writeString(session.workspacePath().resolve("execution-results.json"), session.executionJson);
        return stateJson();
    }

    private ObjectNode explain(JsonNode body) throws Exception {
        String type = text(body, "type", "selection");
        String selectedJson = text(body, "selectedJson", "");
        String formatted = text(body, "formatted", "");
        session.explanation = agent().explainWithGroundedData(type, selectedJson, formatted, session.normalized, session.executionJson);
        return stateJson();
    }

    private ObjectNode saveArtifacts() throws Exception {
        Path w = session.workspacePath();
        Files.createDirectories(w);
        String t = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        saveIfPresent(w, "01-requirements-" + t + ".json", session.requirements);
        saveIfPresent(w, "02-validation-" + t + ".json", session.validation);
        saveIfPresent(w, "03-brd-" + t + ".md", session.brd);
        saveIfPresent(w, "04-normalized-" + t + ".json", session.normalized);
        saveIfPresent(w, "05-rule-code-" + t + ".json", session.codeJson);
        saveIfPresent(w, "07-schedules-" + t + ".json", session.schedulesJson);
        saveIfPresent(w, "08-execution-" + t + ".json", session.executionJson);
        saveIfPresent(w, "09-explanation-" + t + ".txt", session.explanation);
        ObjectNode out = stateJson();
        out.put("savedTo", w.toAbsolutePath().toString());
        return out;
    }
    private void saveIfPresent(Path w, String name, String content) throws Exception {
        if (content != null && !content.isBlank()) Files.writeString(w.resolve(name), content);
    }

    // ===================== state serialization =====================

    private ObjectNode stateJson() {
        ObjectNode n = mapper.createObjectNode();
        n.put("workspace", session.workspace);
        ArrayNode sources = n.putArray("sourceFiles");
        session.sourceFilePaths.forEach(sources::add);
        ArrayNode ragDocs = n.putArray("ragFiles");
        session.ragFilePaths.forEach(ragDocs::add);
        n.put("ragKbSize", session.ragKb.size());
        n.put("ragQuestion", session.ragQuestion);
        ArrayNode ragResults = n.putArray("ragResults");
        for (RagChunk c : session.lastRagResults) {
            ObjectNode cn = ragResults.addObject();
            cn.put("chunkId", c.chunkId); cn.put("source", c.source); cn.put("section", c.section);
            cn.put("text", c.text); cn.put("score", c.score);
            ArrayNode tags = cn.putArray("tags"); c.tags.forEach(tags::add);
        }
        n.put("ragEvidence", session.ragEvidence);
        n.put("ragEvidenceLinked", session.ragEvidenceLinked);
        n.put("factoryPrompt", session.factoryPrompt);
        n.put("codingPrompt", session.codingPrompt);
        n.put("schedulePrompt", session.schedulePrompt);
        putJsonOrRaw(n, "requirements", session.requirements);
        putJsonOrRaw(n, "validation", session.validation);
        n.put("brd", session.brd);
        putJsonOrRaw(n, "normalized", session.normalized);
        putJsonOrRaw(n, "code", session.codeJson);
        n.put("materializedFileCount", session.materializedFileCount);
        putJsonOrRaw(n, "schedules", session.schedulesJson);
        putJsonOrRaw(n, "execution", session.executionJson);
        n.put("explanation", session.explanation);
        n.put("maxFixAttempts", session.maxFixAttempts);
        ArrayNode history = n.putArray("compileHistory");
        for (FactorySession.CompileAttempt a : session.compileHistory) {
            ObjectNode an = history.addObject();
            an.put("attempt", a.attempt()); an.put("errors", a.errors()); an.put("filesChanged", a.filesChanged());
            an.put("result", a.result()); an.put("diagnostics", a.diagnostics());
        }
        return n;
    }

    /** Embeds already-JSON stage output as real JSON (not a string) so the frontend never re-parses it; falls back to a plain string if it isn't valid JSON (e.g. mid-failure). */
    private void putJsonOrRaw(ObjectNode target, String field, String value) {
        if (value == null || value.isBlank()) { target.putNull(field); return; }
        try { target.set(field, mapper.readTree(value)); }
        catch (Exception e) { target.put(field, value); }
    }

    // ===================== HTTP plumbing =====================

    private JsonNode readBody(HttpExchange ex) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        ex.getRequestBody().transferTo(buf);
        String s = buf.toString(StandardCharsets.UTF_8);
        if (s.isBlank()) return mapper.createObjectNode();
        try { return mapper.readTree(s); } catch (Exception e) { return mapper.createObjectNode(); }
    }
    private void sendJson(HttpExchange ex, int status, ObjectNode body) throws IOException {
        sendBytes(ex, status, "application/json; charset=utf-8", mapper.writeValueAsBytes(body));
    }
    private void sendError(HttpExchange ex, int status, String message) throws IOException {
        ObjectNode n = mapper.createObjectNode(); n.put("error", message);
        sendJson(ex, status, n);
    }
    private void sendBytes(HttpExchange ex, int status, String contentType, byte[] bytes) throws IOException {
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(status, bytes.length);
        try (var os = ex.getResponseBody()) { os.write(bytes); }
    }
    private String text(JsonNode n, String field, String fallback) {
        JsonNode v = n == null ? null : n.path(field);
        return v == null || v.isMissingNode() || v.isNull() ? fallback : v.asText();
    }
}
