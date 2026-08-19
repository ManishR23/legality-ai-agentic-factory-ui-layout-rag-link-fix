package com.example.brdagent;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class RagKnowledgeBase {
    private final List<RagChunk> chunks = new ArrayList<>();
    private final ObjectMapper mapper = new ObjectMapper();

    public void clear() { chunks.clear(); }
    public int size() { return chunks.size(); }
    public List<RagChunk> all() { return List.copyOf(chunks); }

    public void addDocument(String source, String text) {
        if (text == null || text.isBlank()) return;
        String[] sections = text.replace("\r", "").split("(?m)(?=^#{1,4}\\s+|^Section\\s+\\d+|^Article\\s+\\d+|^\\d+\\.\\d+)");
        int index = 1;
        for (String sec : sections) {
            String clean = sec.trim();
            if (clean.length() < 80) continue;
            for (int start = 0; start < clean.length(); start += 1800) {
                int end = Math.min(clean.length(), start + 1800);
                String piece = clean.substring(start, end).trim();
                if (piece.length() < 80) continue;
                RagChunk c = new RagChunk();
                c.chunkId = source.replaceAll("[^A-Za-z0-9]", "_") + "_" + index++;
                c.source = source;
                c.section = firstLine(piece);
                c.text = piece;
                c.tags = inferTags(piece);
                chunks.add(c);
            }
        }
    }

    public List<RagChunk> search(String query, int max) {
        Set<String> q = terms(query);
        return chunks.stream().map(c -> {
            RagChunk copy = c.copy();
            Set<String> ct = terms(c.text + " " + c.section + " " + String.join(" ", c.tags));
            double score = 0;
            for (String t : q) if (ct.contains(t)) score += 1.0;
            String lowerQ = query == null ? "" : query.toLowerCase(Locale.ROOT);
            for (String tag : c.tags) if (lowerQ.contains(tag.toLowerCase(Locale.ROOT))) score += 2.0;
            copy.score = score;
            return copy;
        }).filter(c -> c.score > 0)
                .sorted((a,b) -> Double.compare(b.score, a.score))
                .limit(max)
                .collect(Collectors.toList());
    }

    public String evidencePackage(String question, List<RagChunk> results) {
        StringBuilder b = new StringBuilder();
        b.append("# United FA RAG Evidence Package\n\n");
        b.append("## Question\n").append(question == null ? "" : question).append("\n\n");
        b.append("## Guardrails\n- Use only these retrieved chunks as source evidence.\n- Do not invent FAR/CBA/company rules.\n- If evidence is insufficient, say what is missing.\n- The deterministic legality engine is the source of truth for pass/fail.\n\n");
        int i = 1;
        for (RagChunk c : results) {
            b.append("## Chunk ").append(i++).append("\nSource: ").append(c.source)
                    .append("\nSection: ").append(c.section)
                    .append("\nTags: ").append(String.join(", ", c.tags))
                    .append("\nScore: ").append(String.format(Locale.ROOT, "%.2f", c.score))
                    .append("\n\n").append(c.text).append("\n\n");
        }
        return b.toString();
    }

    public void save(Path path) throws Exception {
        Files.createDirectories(path.getParent()); mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), chunks);
    }
    public void load(Path path) throws Exception {
        RagChunk[] arr = mapper.readValue(path.toFile(), RagChunk[].class); chunks.clear(); chunks.addAll(Arrays.asList(arr));
    }

    private String firstLine(String text) {
        String first = Arrays.stream(text.split("\\R")).map(String::trim).filter(s -> !s.isBlank()).findFirst().orElse("Chunk");
        return first.length() > 90 ? first.substring(0,90) : first;
    }
    private List<String> inferTags(String text) {
        String l = text.toLowerCase(Locale.ROOT); List<String> tags = new ArrayList<>();
        if (l.contains("rest")) tags.add("rest"); if (l.contains("duty")) tags.add("duty"); if (l.contains("reserve")) tags.add("reserve");
        if (l.contains("flight attendant") || l.contains("inflight")) tags.add("FA"); if (l.contains("pairing")) tags.add("pairing");
        if (l.contains("training")) tags.add("training"); if (l.contains("deadhead")) tags.add("deadhead"); if (l.contains("base")) tags.add("base");
        if (tags.isEmpty()) tags.add("general"); return tags;
    }
    private Set<String> terms(String text) {
        if (text == null) return Set.of();
        return Arrays.stream(text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").split("\\s+"))
                .filter(t -> t.length() > 2).collect(Collectors.toSet());
    }
}
