"use strict";

/* =====================================================================
   Crew Legality Console -- live client for FactoryHttpServer's JSON API.
   Every render function reads from the server's /api/state response;
   the client holds no pipeline state of its own except the current
   "explain" selection (which schedule/result the user picked to explain).
   ===================================================================== */

const PANELS = [
  { id: "rag", num: 0, label: "FA RAG" },
  { id: "req", num: 1, label: "Requirements" },
  { id: "val", num: 2, label: "Validation" },
  { id: "brd", num: 3, label: "BRD" },
  { id: "norm", num: 4, label: "Normalized Rules" },
  { id: "code", num: 5, label: "Rule Code" },
  { id: "compile", num: 6, label: "Compile / Fix" },
  { id: "sched", num: 7, label: "Schedules" },
  { id: "exec", num: 8, label: "Execution" },
  { id: "explain", num: 9, label: "Explanation" }
];

let STATE = null;
let selectedCodeFile = 0;
let selectedSchedule = 0;
let explainSelection = null; // {type, selectedJson, formatted, label}

/* ---------------------------- utilities ---------------------------- */

function esc(s) {
  if (s === null || s === undefined) return "";
  return String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}
function el(tag, attrs, children) {
  const node = document.createElement(tag);
  if (attrs) for (const k in attrs) {
    if (k === "class") node.className = attrs[k];
    else if (k === "text") node.textContent = attrs[k];
    else node.setAttribute(k, attrs[k]);
  }
  (children || []).forEach((c) => node.appendChild(c));
  return node;
}
function clear(node) { while (node.firstChild) node.removeChild(node.firstChild); }

async function api(path, body) {
  const res = await fetch(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body || {})
  });
  const json = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(json.error || ("Request failed (" + res.status + ")"));
  return json;
}
async function apiGet(path) {
  const res = await fetch(path);
  const json = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(json.error || ("Request failed (" + res.status + ")"));
  return json;
}

function setBusy(btn, busy) { btn.classList.toggle("busy", busy); btn.disabled = busy; }
function setStatus(id, message, kind) {
  const line = document.getElementById(id);
  line.textContent = message || "";
  line.className = "status-line" + (kind ? " " + kind : "");
}

/** Wraps a button click: sets busy/status, calls action(), applies returned state, reports success/error. */
function bindAction(btnId, statusId, successMessage, action) {
  const btn = document.getElementById(btnId);
  btn.addEventListener("click", async () => {
    setBusy(btn, true);
    setStatus(statusId, "Working…", "busy");
    try {
      const state = await action();
      applyState(state);
      setStatus(statusId, successMessage, "ok");
    } catch (e) {
      setStatus(statusId, e.message, "err");
    } finally {
      setBusy(btn, false);
    }
  });
}

const STATUS_ICONS = {
  pass: '<svg viewBox="0 0 16 16" fill="none" aria-hidden="true"><path d="M3.5 8.5l3 3 6-7" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  violation: '<svg viewBox="0 0 16 16" fill="none" aria-hidden="true"><path d="M4.5 4.5l7 7M11.5 4.5l-7 7" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>',
  review: '<svg viewBox="0 0 16 16" fill="none" aria-hidden="true"><path d="M8 4.5v4.2M8 11v.1" stroke="currentColor" stroke-width="2" stroke-linecap="round"/><circle cx="8" cy="8" r="6.3" stroke="currentColor" stroke-width="1.4"/></svg>',
  error: '<svg viewBox="0 0 16 16" fill="none" aria-hidden="true"><circle cx="8" cy="8" r="6.3" stroke="currentColor" stroke-width="1.4"/><path d="M6 6l4 4M10 6l-4 4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>'
};
function statusKind(status) {
  const s = (status || "").toUpperCase();
  if (s === "PASS") return "pass";
  if (s === "VIOLATION") return "violation";
  if (s === "NEEDS_REVIEW") return "review";
  return "error";
}
function statusPill(status) {
  const kind = statusKind(status);
  return '<span class="status-pill ' + kind + '">' + STATUS_ICONS[kind] + esc((status || "UNKNOWN").replace(/_/g, " ")) + "</span>";
}
function jsonViewCard(title, data, emptyMsg) {
  if (data === null || data === undefined) return el("div", { class: "empty-state", text: emptyMsg });
  const text = typeof data === "string" ? data : JSON.stringify(data, null, 2);
  const card = el("div", { class: "card" }, [
    el("h3", { text: title }),
    el("pre", {}, [el("code", { text })])
  ]);
  return card;
}

/* ---------------------------- rail / tabs ---------------------------- */

function buildRail() {
  const railEl = document.getElementById("rail");
  railEl.appendChild(el("div", { class: "rail-label", text: "Pipeline · 10 stages" }));
  PANELS.forEach((p, i) => {
    const btn = el("button", { class: "tab", id: "tab-" + p.id, type: "button", role: "tab", "aria-selected": i === 0 ? "true" : "false", "aria-controls": "panel-" + p.id });
    btn.tabIndex = i === 0 ? 0 : -1;
    btn.appendChild(el("span", { class: "tab-num", text: String(p.num), "aria-hidden": "true" }));
    btn.appendChild(el("span", { class: "tab-label", text: p.label }));
    btn.addEventListener("click", () => activate(i));
    railEl.appendChild(btn);
  });
  railEl.addEventListener("keydown", (e) => {
    const tabs = PANELS.map((p) => document.getElementById("tab-" + p.id));
    const idx = tabs.indexOf(document.activeElement);
    if (idx === -1) return;
    let next = null;
    if (e.key === "ArrowDown") next = (idx + 1) % tabs.length;
    else if (e.key === "ArrowUp") next = (idx - 1 + tabs.length) % tabs.length;
    else if (e.key === "Home") next = 0;
    else if (e.key === "End") next = tabs.length - 1;
    if (next !== null) { e.preventDefault(); activate(next); }
  });
}
function activate(i) {
  PANELS.forEach((p, j) => {
    const tab = document.getElementById("tab-" + p.id);
    const panel = document.getElementById("panel-" + p.id);
    const on = i === j;
    tab.setAttribute("aria-selected", on ? "true" : "false");
    tab.tabIndex = on ? 0 : -1;
    panel.classList.toggle("active", on);
    panel.hidden = !on;
  });
  document.getElementById("tab-" + PANELS[i].id).focus();
}
function activateById(id) { activate(PANELS.findIndex((p) => p.id === id)); }

/* ---------------------------- state application ---------------------------- */

function applyState(state) {
  STATE = state;
  renderTopbar();
  renderRagDocs();
  renderSourceDocs();
  renderRagResults();
  renderRagEvidence();
  renderRequirements();
  renderValidation();
  renderBrd();
  renderNormalized();
  renderCode();
  renderCompileHistory();
  renderSchedules();
  renderExecution();
  renderExplainSelection();
  renderExplainResult();
  syncPrompts();
}

function syncPrompts() {
  const wsInput = document.getElementById("workspace-input");
  if (document.activeElement !== wsInput) wsInput.value = STATE.workspace;
  const fp = document.getElementById("factory-prompt");
  if (document.activeElement !== fp) fp.value = STATE.factoryPrompt || "";
  const cp = document.getElementById("coding-prompt");
  if (document.activeElement !== cp) cp.value = STATE.codingPrompt || "";
  const sp = document.getElementById("schedule-prompt");
  if (document.activeElement !== sp) sp.value = STATE.schedulePrompt || "";
  const rq = document.getElementById("rag-question");
  if (document.activeElement !== rq) rq.value = STATE.ragQuestion || "";
  document.getElementById("max-fix").value = STATE.maxFixAttempts || 3;
}

function renderTopbar() {
  const chip = document.getElementById("rag-chip");
  const chipText = document.getElementById("rag-chip-text");
  if (STATE.ragEvidenceLinked) {
    chip.className = "chip chip-live";
    chipText.textContent = "RAG evidence linked · " + STATE.ragResults.length + " chunks";
  } else {
    chip.className = "chip chip-off";
    chipText.textContent = "RAG evidence not linked (KB: " + STATE.ragKbSize + " chunks)";
  }
}

function pathList(container, items, onRemove) {
  clear(container);
  items.forEach((p, i) => {
    const row = el("div", { class: "path-row" }, [
      el("span", { class: "path mono", text: p }),
      el("button", { type: "button", text: "Remove" })
    ]);
    row.querySelector("button").addEventListener("click", () => onRemove(i));
    container.appendChild(row);
  });
}
function renderRagDocs() {
  pathList(document.getElementById("rag-doc-list"), STATE.ragFiles, async (i) => {
    applyState(await api("/api/rag/docs/remove", { index: i }));
  });
}
function renderSourceDocs() {
  pathList(document.getElementById("source-doc-list"), STATE.sourceFiles, async (i) => {
    applyState(await api("/api/sources/remove", { index: i }));
  });
}

function renderRagResults() {
  const wrap = document.getElementById("rag-results-wrap");
  clear(wrap);
  if (!STATE.ragResults.length) { wrap.appendChild(el("div", { class: "empty-state", text: "No search results yet. Build the knowledge base, then search it." })); return; }
  const wrapDiv = el("div", { class: "table-wrap" });
  wrapDiv.innerHTML =
    '<table><caption>Retrieved chunks</caption><thead><tr>' +
    '<th scope="col" class="num">Score</th><th scope="col">Section</th><th scope="col">Tags</th><th scope="col">Preview</th>' +
    "</tr></thead><tbody>" +
    STATE.ragResults.map((c) =>
      "<tr><td class='num mono'>" + esc(c.score.toFixed(2)) + "</td><td>" + esc(c.section) + "</td><td class='chiprow'>" +
      c.tags.map((t) => "<span class='tagchip'>" + esc(t) + "</span>").join("") +
      "</td><td>" + esc(c.text.slice(0, 160)) + (c.text.length > 160 ? "…" : "") + "</td></tr>"
    ).join("") +
    "</tbody></table>";
  wrap.appendChild(wrapDiv);
}
function renderRagEvidence() {
  const wrap = document.getElementById("rag-evidence-wrap");
  clear(wrap);
  if (!STATE.ragEvidence) return;
  const card = el("div", { class: "card" }, [el("h3", { text: "Evidence package" })]);
  const pre = el("pre", {}, [el("code", { text: STATE.ragEvidence })]);
  card.appendChild(pre);
  wrap.appendChild(card);
}

function renderRequirements() {
  const c = document.getElementById("req-result"); clear(c);
  c.appendChild(jsonViewCard("Requirements output", STATE.requirements, "Not generated yet. Add source files or link RAG evidence, then run this stage."));
}
function renderValidation() {
  const c = document.getElementById("val-result"); clear(c);
  c.appendChild(jsonViewCard("Validation output", STATE.validation, "Not generated yet. Run Requirements first."));
}
function renderBrd() {
  const c = document.getElementById("brd-result"); clear(c);
  if (!STATE.brd) { c.appendChild(el("div", { class: "empty-state", text: "Not generated yet. Run Requirements and Validation first." })); return; }
  const card = el("div", { class: "card" }, [el("h3", { text: "BRD (Markdown)" })]);
  const pre = el("pre", {}, [el("code", { text: STATE.brd })]);
  card.appendChild(pre);
  c.appendChild(card);
}

function renderNormalized() {
  const c = document.getElementById("norm-result"); clear(c);
  const data = STATE.normalized;
  if (!data) { c.appendChild(el("div", { class: "empty-state", text: "Not generated yet. Run the BRD stage first." })); return; }
  const rules = Array.isArray(data.rules) ? data.rules : null;
  if (!rules) { c.appendChild(jsonViewCard("Normalized rules (raw)", data, "")); return; }
  const stack = el("div", { class: "stack" });
  rules.forEach((r) => {
    const params = r.parameters && typeof r.parameters === "object"
      ? Object.entries(r.parameters).map(([k, v]) => esc(k) + " = " + esc(JSON.stringify(v))).join(", ")
      : "none";
    const card = document.createElement("div"); card.className = "card";
    card.innerHTML =
      '<div style="display:flex;justify-content:space-between;align-items:baseline;gap:10px;flex-wrap:wrap">' +
      "<h3><span class='idchip'>" + esc(r.ruleId || "?") + "</span> " + esc(r.ruleName || "") + "</h3>" +
      "<span class='tagchip'>severity: " + esc(r.severity || "?") + "</span></div>" +
      "<p style='font-size:.85rem;margin-top:6px'>" + esc(r.logic || "") + "</p>" +
      "<div class='kv' style='margin-top:10px'><dt>Parameters</dt><dd class='mono'>" + params + "</dd>" +
      "<dt>Validation</dt><dd>" + esc(r.validationStatus || "?") + (r.humanReviewRequired ? " · human review required" : " · human review not required") + "</dd></div>";
    stack.appendChild(card);
  });
  c.appendChild(stack);
}

function renderCode() {
  const c = document.getElementById("code-result"); clear(c);
  const data = STATE.code;
  document.getElementById("materialize-btn").disabled = !data;
  if (!data) {
    c.appendChild(el("div", { class: "empty-state", text: "Not generated yet. Run the Normalized Rules stage first." }));
    return;
  }
  const files = Array.isArray(data.generatedFiles) ? data.generatedFiles : [];
  if (selectedCodeFile >= files.length) selectedCodeFile = 0;
  const chiprow = el("div", { class: "chiprow" });
  files.forEach((f, i) => {
    const chip = el("span", { class: "idchip clickable" + (i === selectedCodeFile ? " active" : ""), text: f.fileName || ("file-" + i) });
    chip.addEventListener("click", () => { selectedCodeFile = i; renderCode(); });
    chiprow.appendChild(chip);
  });
  c.appendChild(chiprow);
  if (files[selectedCodeFile]) {
    const card = el("div", { class: "card" }, [el("h3", { text: files[selectedCodeFile].fileName || "" })]);
    card.appendChild(el("pre", {}, [el("code", { text: files[selectedCodeFile].sourceCode || "" })]));
    c.appendChild(card);
  }
  if (STATE.materializedFileCount >= 0) {
    c.appendChild(el("footer", { class: "note", text: "Materialized " + STATE.materializedFileCount + " .java file(s) to " + STATE.workspace + "/generated-java" }));
  }
}

function renderCompileHistory() {
  const c = document.getElementById("compile-result"); clear(c);
  const history = STATE.compileHistory || [];
  if (!history.length) { c.appendChild(el("div", { class: "empty-state", text: "No compile attempts yet. Materialize Java, then compile." })); return; }
  const wrap = el("div", { class: "table-wrap" });
  wrap.innerHTML =
    "<table><caption>Compile attempt history</caption><thead><tr>" +
    "<th scope='col' class='num'>Attempt</th><th scope='col' class='num'>Errors</th><th scope='col' class='num'>Files changed</th><th scope='col'>Result</th>" +
    "</tr></thead><tbody>" +
    history.map((a, i) =>
      "<tr class='selectable' data-i='" + i + "'><td class='num mono'>" + a.attempt + "</td><td class='num mono'>" + a.errors +
      "</td><td class='num mono'>" + a.filesChanged + "</td><td>" +
      (a.result === "PASSED" ? statusPill("PASS") : "<span class='status-pill " + (a.result === "HUMAN REVIEW" ? "error" : "review") + "'>" + esc(a.result) + "</span>") +
      "</td></tr>"
    ).join("") + "</tbody></table>";
  c.appendChild(wrap);
  const detail = el("pre", {}, [el("code", { text: history[history.length - 1].diagnostics || "" })]);
  const detailCard = el("div", { class: "card" }, [el("h3", { text: "Diagnostics · attempt " + history[history.length - 1].attempt })]);
  detailCard.appendChild(detail);
  c.appendChild(detailCard);
  wrap.querySelectorAll("tr.selectable").forEach((row) => {
    row.addEventListener("click", () => {
      const a = history[Number(row.dataset.i)];
      detailCard.querySelector("h3").textContent = "Diagnostics · attempt " + a.attempt;
      detail.querySelector("code").textContent = a.diagnostics || "";
      wrap.querySelectorAll("tr.selectable").forEach((r) => r.classList.remove("selected"));
      row.classList.add("selected");
    });
  });
}

function flightCount(schedule) {
  let n = 0;
  (schedule.pairings || []).forEach((p) => (p.duties || []).forEach((d) => (d.activities || []).forEach((a) => { if ((a.type || "").toUpperCase() === "FLIGHT") n++; })));
  return n;
}
function scheduleDuties(schedule) {
  const duties = [];
  (schedule.pairings || []).forEach((p) => (p.duties || []).forEach((d) => duties.push(d)));
  return duties;
}

function renderSchedules() {
  const c = document.getElementById("sched-result"); clear(c);
  const data = STATE.schedules;
  if (!data || !Array.isArray(data.schedules) || !data.schedules.length) {
    c.appendChild(el("div", { class: "empty-state", text: "Not generated yet. Run the Normalized Rules stage first, then generate schedules." }));
    return;
  }
  const schedules = data.schedules;
  if (selectedSchedule >= schedules.length) selectedSchedule = 0;

  const chiprow = el("div", { class: "chiprow" });
  schedules.forEach((s, i) => {
    const chip = el("span", { class: "idchip clickable" + (i === selectedSchedule ? " active" : ""), text: s.scheduleId || ("schedule-" + i) });
    chip.addEventListener("click", () => { selectedSchedule = i; renderSchedules(); });
    chiprow.appendChild(chip);
  });
  c.appendChild(chiprow);

  const s = schedules[selectedSchedule];
  if (!s) return;
  const card = el("div", { class: "card" });
  card.innerHTML =
    "<h3><span class='idchip'>" + esc(s.scheduleId || "?") + "</span> · " + esc(s.crewId || "?") + " · base " + esc(s.base || "?") + "</h3>" +
    "<p class='panel-desc' style='margin:6px 0 14px'>" + esc(s.scheduleStart || "?") + " → " + esc(s.scheduleEnd || "?") + " · " +
    flightCount(s) + " flight legs</p>";
  const timeline = el("div", { class: "timeline" });
  const duties = scheduleDuties(s);
  duties.forEach((d, i) => {
    const dutyDiv = document.createElement("div"); dutyDiv.className = "duty";
    dutyDiv.innerHTML =
      "<div class='duty-head'><span class='id'>" + esc(d.dutyId || "?") + "</span><span class='duty-times'>" +
      esc(d.reportTime || "?") + " → " + esc(d.releaseTime || "?") + "</span></div>" +
      "<div class='leg-list'>" + (d.activities || []).map((a) =>
        "<div class='leg'><span class='flt'>" + esc(a.flightNumber || a.type || "") + "</span><span class='route'>" +
        esc(a.origin || "") + (a.origin ? " → " : "") + esc(a.destination || "") + "</span><span>" +
        esc(a.departureTime || a.startDateTime || "") + (a.departureTime || a.startDateTime ? " – " : "") + esc(a.arrivalTime || a.endDateTime || "") + "</span></div>"
      ).join("") + "</div>";
    timeline.appendChild(dutyDiv);
    if (i < duties.length - 1) {
      const next = duties[i + 1];
      const marker = document.createElement("div"); marker.className = "gap-marker";
      marker.innerHTML = "<span class='line'></span><span class='label'>rest gap: release " + esc(d.releaseTime || "?") + " → report " + esc(next.reportTime || "?") + "</span><span class='line'></span>";
      timeline.appendChild(marker);
    }
  });
  card.appendChild(timeline);
  c.appendChild(card);

  const explainBtn = el("button", { type: "button", class: "btn btn-secondary", text: "Explain this schedule" });
  explainBtn.addEventListener("click", () => {
    explainSelection = { type: "schedule", selectedJson: JSON.stringify(s, null, 2), formatted: "Schedule " + (s.scheduleId || "") + " · crew " + (s.crewId || "") + " · " + duties.length + " duties, " + flightCount(s) + " flight legs", label: "Schedule " + (s.scheduleId || "") };
    renderExplainSelection();
    activateById("explain");
  });
  c.appendChild(explainBtn);
}

function renderExecution() {
  const c = document.getElementById("exec-result"); clear(c);
  const data = STATE.execution;
  if (!data || !Array.isArray(data.scheduleResults) || !data.scheduleResults.length) {
    c.appendChild(el("div", { class: "empty-state", text: "Not executed yet. Compile the rules and generate schedules first." }));
    return;
  }
  const stats = el("div", { class: "stat-row" });
  stats.innerHTML =
    "<div class='stat'><div class='stat-num mono'>" + data.scheduleResults.reduce((n, sr) => n + sr.results.length, 0) + "</div><div class='stat-label'>Evaluations</div></div>" +
    "<div class='stat violation'><div class='stat-num mono'>" + data.violationCount + "</div><div class='stat-label'>Violations</div></div>" +
    "<div class='stat review'><div class='stat-num mono'>" + data.needsReviewCount + "</div><div class='stat-label'>Needs review</div></div>" +
    "<div class='stat error'><div class='stat-num mono'>" + data.errorCount + "</div><div class='stat-label'>Errors</div></div>";
  c.appendChild(stats);

  const wrap = el("div", { class: "table-wrap" });
  const table = document.createElement("table");
  table.innerHTML = "<caption>Execution results</caption><thead><tr><th scope='col'>Rule</th><th scope='col'>Status</th><th scope='col'>Message</th></tr></thead><tbody></tbody>";
  const tbody = table.querySelector("tbody");
  data.scheduleResults.forEach((sr) => {
    const groupRow = document.createElement("tr"); groupRow.className = "sched-group";
    const groupTh = document.createElement("th"); groupTh.colSpan = 3;
    groupTh.textContent = (sr.scheduleId || "?") + " · " + (sr.crewId || "?") + " · " + (sr.base || "?");
    groupRow.appendChild(groupTh);
    tbody.appendChild(groupRow);
    sr.results.forEach((r) => {
      const row = document.createElement("tr");
      row.innerHTML = "<td class='idchip'>" + esc(r.ruleId) + "</td><td>" + statusPill(r.status) + "</td><td>" + esc(r.message) + "</td>";
      tbody.appendChild(row);
    });
    const explainRow = document.createElement("tr");
    const explainTd = document.createElement("td"); explainTd.colSpan = 3; explainTd.style.borderBottom = "1px solid var(--line)"; explainTd.style.paddingTop = "4px"; explainTd.style.paddingBottom = "10px";
    const explainBtn = el("button", { type: "button", class: "btn btn-secondary", text: "Explain this schedule's results" });
    explainBtn.addEventListener("click", () => {
      const violations = sr.results.filter((r) => r.violation).length;
      explainSelection = { type: "executionResult", selectedJson: JSON.stringify(sr, null, 2), formatted: "Execution for " + (sr.scheduleId || "") + " · " + sr.results.length + " evaluations, " + violations + " violation(s)", label: "Execution result " + (sr.scheduleId || "") };
      renderExplainSelection();
      activateById("explain");
    });
    explainTd.appendChild(explainBtn);
    explainRow.appendChild(explainTd);
    tbody.appendChild(explainRow);
  });
  wrap.appendChild(table);
  c.appendChild(wrap);
  c.appendChild(el("footer", { class: "note", text: "compiledRuleCount=" + data.compiledRuleCount + ", ruleDefinitionCount=" + data.ruleDefinitionCount }));
}

function renderExplainSelection() {
  const c = document.getElementById("explain-selection"); clear(c);
  const btn = document.getElementById("explain-run-btn");
  if (!explainSelection) {
    c.appendChild(el("div", { class: "empty-state", text: "Nothing selected. Go to Schedules or Execution and click “Explain this…” on an item." }));
    btn.disabled = true;
    return;
  }
  btn.disabled = false;
  const card = el("div", { class: "card" }, [
    el("h3", { text: "Selected: " + explainSelection.label }),
    el("p", { class: "mono", text: explainSelection.formatted, style: "font-size:.82rem" })
  ]);
  c.appendChild(card);
}

/* ---------------------------- event wiring ---------------------------- */

function wireEvents() {
  document.getElementById("workspace-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    applyState(await api("/api/workspace", { path: document.getElementById("workspace-input").value.trim() }));
  });

  document.getElementById("rag-doc-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const input = document.getElementById("rag-doc-path");
    if (!input.value.trim()) return;
    try { applyState(await api("/api/rag/docs", { path: input.value.trim() })); input.value = ""; }
    catch (err) { setStatus("rag-build-status", err.message, "err"); }
  });
  document.getElementById("source-doc-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const input = document.getElementById("source-doc-path");
    if (!input.value.trim()) return;
    try { applyState(await api("/api/sources", { path: input.value.trim() })); input.value = ""; }
    catch (err) { setStatus("req-status", err.message, "err"); }
  });

  bindAction("rag-build-btn", "rag-build-status", "Knowledge base built.", () => api("/api/rag/build"));
  bindAction("rag-search-btn", "rag-search-status", "Search complete.", () => api("/api/rag/search", { question: document.getElementById("rag-question").value }));
  bindAction("rag-evidence-btn", "rag-search-status", "Evidence package created.", () => api("/api/rag/evidence"));
  bindAction("rag-use-btn", "rag-search-status", "Evidence linked to Factory Prompt.", () => api("/api/rag/use-evidence"));

  document.getElementById("factory-prompt").addEventListener("change", (e) => api("/api/prompts", { factoryPrompt: e.target.value }));
  document.getElementById("coding-prompt").addEventListener("change", (e) => api("/api/prompts", { codingPrompt: e.target.value }));
  document.getElementById("schedule-prompt").addEventListener("change", (e) => api("/api/prompts", { schedulePrompt: e.target.value }));

  bindAction("req-run-btn", "req-status", "Requirements generated.", () => api("/api/requirements"));
  bindAction("val-run-btn", "val-status", "Validation complete.", () => api("/api/validation"));
  bindAction("brd-run-btn", "brd-status", "BRD generated.", () => api("/api/brd"));
  bindAction("norm-run-btn", "norm-status", "Rules normalized.", () => api("/api/normalize"));
  bindAction("code-run-btn", "code-status", "Rule code generated.", () => api("/api/code"));
  bindAction("materialize-btn", "code-status", "Java materialized.", () => api("/api/materialize"));
  bindAction("compile-once-btn", "compile-status", "Compile passed.", () => api("/api/compile"));
  bindAction("compile-autofix-btn", "compile-status", "Compile passed after auto-fix.", () => api("/api/compile-autofix", { maxAttempts: Number(document.getElementById("max-fix").value) || 3 }));
  bindAction("sched-run-btn", "sched-status", "Schedules generated.", () => api("/api/schedules"));
  bindAction("exec-run-btn", "exec-status", "Execution complete.", () => api("/api/execute"));
  bindAction("explain-run-btn", "explain-status", "Explanation generated.", () => {
    if (!explainSelection) throw new Error("Nothing selected to explain");
    return api("/api/explain", explainSelection);
  });
}

function renderExplainResult() {
  const c = document.getElementById("explain-result"); clear(c);
  if (!STATE.explanation) { c.appendChild(el("div", { class: "empty-state", text: "No explanation generated yet." })); return; }
  const card = el("div", { class: "card" }, [el("h3", { text: "Grounded explanation" })]);
  card.appendChild(el("p", { class: "quote-block", text: STATE.explanation }));
  c.appendChild(card);
}

/* ---------------------------- init ---------------------------- */

(async function init() {
  buildRail();
  wireEvents();
  try {
    applyState(await apiGet("/api/state"));
  } catch (e) {
    document.getElementById("main-panel").prepend(el("div", { class: "card", style: "border-color:var(--violation-line)", text: "Could not reach the server: " + e.message }));
  }
})();
