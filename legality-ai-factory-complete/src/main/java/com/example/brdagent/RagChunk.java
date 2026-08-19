package com.example.brdagent;

import java.util.ArrayList;
import java.util.List;

public class RagChunk {
    public String chunkId;
    public String source;
    public String section;
    public String text;
    public List<String> tags = new ArrayList<>();
    public double score;

    public RagChunk copy() {
        RagChunk r = new RagChunk();
        r.chunkId = chunkId; r.source = source; r.section = section; r.text = text;
        r.tags = new ArrayList<>(tags); r.score = score; return r;
    }
}
