# Legality AI Agentic Factory - Complete Swing Source

Includes:

- United FA RAG / Knowledge Base
- Requirements Agent
- Rule Validation & Confidence Agent
- BRD Agent
- Rule Normalization Agent
- Rule Coding Agent
- Java Materializer
- Compile Agent
- Compile Fix Agent with automatic retry loop
- Test Schedule Agent
- Readable Schedule table/detail UI
- Rule Execution Agent
- Readable Violation table/detail UI
- Grounded LLM Explanation Agent

## Eclipse

1. File -> Import -> Existing Maven Project.
2. Select this project folder.
3. Right-click project -> Maven -> Update Project -> check **Force Update**.
4. Project -> Clean.
5. Use **JDK 17 or JDK 21**, not a JRE.
6. Copy `.env.example` to `.env` and add `OPENAI_API_KEY`.
7. Run `com.example.brdagent.BrdAgentApp` as Java Application.

## Compile Fix Agent

After Rule Coding:

```text
Materialize Java
  -> Compile
  -> compiler diagnostics
  -> Compile Fix Agent
  -> rewrite only technically broken Java
  -> recompile
  -> repeat up to Max Fix attempts
```

The Compile Fix Agent is explicitly prohibited from changing thresholds, rule applicability, conditions, PASS/VIOLATION intent, or inventing new rules. If a compile error cannot be fixed without changing legality semantics, it returns `HUMAN_REVIEW_REQUIRED`.

Use **Compile + Auto Fix** for the normal workflow. Default maximum retries is 3.

## Factory flow

```text
FA/FAR/CBA Sources
  -> FA RAG evidence (optional)
  -> Requirements
  -> Validation & Confidence
  -> BRD
  -> Normalized Rule Catalog
  -> Java Rule Coding
  -> Materialize Java
  -> Compile + Compile Fix Agent
  -> Test Schedule Generation
  -> Rule Execution
  -> Violation Display
  -> Grounded LLM Explanation
```

The deterministic legality engine remains the source of truth for execution. LLM explanation is grounded only in supplied tool data, normalized rules, and engine output.

## UI layout fix for RAG evidence

The RAG evidence package is no longer appended into the visible Factory Prompt text area.
Instead, `Use Evidence` links the evidence in memory and the Requirements/Validation/BRD/Normalization agents receive it through `combinedFactoryPrompt()`.

This prevents the Swing prompt panel from expanding and hiding the main workflow tabs.
The top prompt/source area is now contained in a fixed-height vertical split pane; the workflow tabs always remain visible.
