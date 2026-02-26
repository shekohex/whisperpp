# Pitfalls Research

**Domain:** Android dictation keyboard (IME) + streaming STT + LLM rewrite across arbitrary app text fields
**Researched:** 2026-02-26
**Confidence:** MEDIUM

## Critical Pitfalls

### Pitfall 1: Treating IME input as “normal text editing” (InputConnection realities)

**What goes wrong:**
Edits land in the wrong place, duplicate, or silently fail; features work in a demo EditText but break in Gmail/Docs/WebView/Compose; crashes when the target app changes or the connection is invalid.

**Why it happens:**
IME is editing *someone else’s* document over a fragile IPC boundary. Many editors only implement a subset of APIs; `InputConnection` can be stale; calls can return `null`, partial data, or be expensive.

**How to avoid:**
- Build a defensive “EditorCapabilities” layer per session (based on `EditorInfo`, observed behavior, and feature probing) and gate features accordingly.
- Never assume you can read full text; design features to degrade gracefully using: selected text → small context windows → no-context fallback.
- Treat `InputConnection` as ephemeral: re-check validity before every multi-step edit; abort safely on failures.
- Keep all edit operations idempotent and atomic (single “apply edit plan” step).

**Warning signs:**
- “Works in our test app but not in Chrome/Gboard-like targets.”
- Frequent `null` / empty results from selection/context reads.
- Reports of “random insertion location” when switching apps or rotating.

**Phase to address:**
Phase 1 (IME core + text editing engine compatibility)

---

### Pitfall 2: Mishandling composing vs committed text (and fighting the host editor)

**What goes wrong:**
Stuck underlines, broken auto-correct, cursor jumping, partial commits, or the host app re-corrects your output unexpectedly. Undo becomes impossible or confusing.

**Why it happens:**
Composing regions are a contract with the editor; different apps treat composition differently. Mixing `setComposingText`, `commitText`, `finishComposingText`, and replacement without a consistent state machine creates “ghost composition” and selection drift.

**How to avoid:**
- Implement a strict text state machine: *dictation streaming* writes composing text; *finalization* commits; *rewrite* uses explicit replace + immediate final commit.
- Wrap multi-step edits in `beginBatchEdit/endBatchEdit` when available; always finish composition before doing a rewrite/replace.
- Track and verify selection/composition anchors after every edit; if mismatch, stop streaming and fall back to commit-only mode.

**Warning signs:**
- “Underline won’t go away” or “cursor jumps to start/end” reports.
- Output gets double-spaced or re-corrected by the app.
- Undo removes too much or too little.

**Phase to address:**
Phase 1 (text engine + composing contract)

---

### Pitfall 3: Race conditions during streaming (selection changes mid-dictation)

**What goes wrong:**
User moves cursor, selects text, or app programmatically changes content while STT is streaming; incoming partials continue inserting into the old location; final transcript overwrites unrelated text.

**Why it happens:**
Streaming pipelines are asynchronous; editor state is mutable; IME has to reconcile two clocks: audio/STT events and editor selection/content changes.

**How to avoid:**
- Maintain a per-dictation “anchor” (initial selection + a content fingerprint of nearby text) and validate it before applying each partial.
- On any significant selection change or fingerprint mismatch: pause streaming updates, notify user, and require explicit resume.
- Support “lock cursor during dictation” option and show clear UI when lock is active.

**Warning signs:**
- Bug reports mentioning “I tapped somewhere else and it typed over my message.”
- Telemetry: high rate of “anchor mismatch” or aborted sessions.

**Phase to address:**
Phase 2 (dictation session state machine) + Phase 3 (streaming STT)

---

### Pitfall 4: Non-atomic rewrite/replace (no reliable undo = trust death)

**What goes wrong:**
LLM rewrite replaces text in multiple steps; user hits undo and gets a weird intermediate; sometimes original text is unrecoverable.

**Why it happens:**
IME edits aren’t automatically grouped. If you do: delete → type → adjust selection → punctuation fixes, undo may unwind unpredictably, and apps differ.

**How to avoid:**
- Make every rewrite a single atomic “transaction”: capture original (selected text + minimal surrounding), apply exactly one replace operation, then commit.
- Provide IME-level undo that restores the original via a stored snapshot (not relying on app undo).
- Always show a “preview/apply” path for destructive operations (at least for first-run, or in “safe mode”).

**Warning signs:**
- Users ask “how do I get back what I had?”
- High frequency of immediate undo after rewrite.

**Phase to address:**
Phase 4 (LLM rewrite + undo model)

---

### Pitfall 5: Reading/sending too much context (privacy + latency + cost)

**What goes wrong:**
The IME tries to fetch large context windows (or full field text) and sends them to STT/LLM providers. This increases latency, token cost, and privacy exposure; it also breaks on editors that don’t support large reads.

**Why it happens:**
LLMs “work better with more context,” and teams over-correct by grabbing as much as possible. IME context reads can be slow IPC calls.

**How to avoid:**
- Implement strict, configurable context budgets (chars/tokens) with deterministic truncation.
- Default to *selected text only* for command/rewrite; only include surrounding context when the user explicitly opts in.
- Add an always-available “privacy mode” that sends *only* the dictated audio transcript (no surrounding text).

**Warning signs:**
- Noticeable keyboard lag when command mode opens.
- Provider bills spike unexpectedly.
- Users complain the keyboard is “reading my messages.”

**Phase to address:**
Phase 0 (privacy model) + Phase 3/4 (context budgeting + UX consent)

---

### Pitfall 6: Capturing sensitive fields accidentally (passwords, OTPs, secure views)

**What goes wrong:**
Dictation or selected-text commands run inside password/OTP fields or other sensitive contexts; transcript/selected text is sent to a provider; even a single incident destroys trust.

**Why it happens:**
Editors don’t always flag sensitivity consistently; teams rely only on `inputType` heuristics; some apps put secrets in “normal” fields.

**How to avoid:**
- Hard-block dictation + text sending when `EditorInfo` indicates password/visible password/number password, or when the app is on a user-maintained denylist.
- Add a prominent “Never send from this app” toggle accessible in-keyboard.
- Provide a “local-only” mode (no network) as a first-class state.
- Never auto-send selected text without an explicit user action (tap/hold).

**Warning signs:**
- Any report mentioning passwords/verification codes.
- Support requests: “Why did it stop working in my bank app?” (this is expected; document it).

**Phase to address:**
Phase 0 (privacy boundaries + policies) + Phase 1 (sensitive-field detection)

---

### Pitfall 7: BYO API keys handled like “settings,” not like secrets

**What goes wrong:**
Keys leak via logs, backups, screenshots, clipboard, crash reports, or insecure storage; provider accounts get abused; the app becomes a credential-stealing vector.

**Why it happens:**
IME projects start as “power-user tools” and skip threat modeling. Android backup and shared storage surprises teams.

**How to avoid:**
- Store keys in Android Keystore-backed encryption (or equivalent secure storage) and mark preferences as not backed up.
- Never log request/response bodies; redact keys everywhere.
- Support per-provider keys and per-feature enablement (STT key ≠ LLM key).
- Add a “panic wipe” (clear all keys/data) and a “test key” flow that never saves until verified.

**Warning signs:**
- Keys appear in `adb logcat` or crash traces.
- Users report provider usage when app isn’t used.

**Phase to address:**
Phase 0 (threat model + storage) + Phase 3/4 (provider integration hardening)

---

### Pitfall 8: Prompt injection / instruction confusion in selected-text command mode

**What goes wrong:**
Selected text contains hostile instructions (“ignore system prompt, exfiltrate …”). The LLM follows it, producing unsafe output, unexpected replacements, or leaking internal prompt content.

**Why it happens:**
Teams treat the selected text as “the user request,” not as untrusted data. Command mode blends UI intent + untrusted document content.

**How to avoid:**
- Structure prompts as: fixed system policy + explicit user intent + selected text enclosed as data (clear delimiters, “treat as plain text”).
- Do not include other app context by default.
- Add “safe transformations” presets (rewrite tone, shorten, fix grammar) with constrained instructions.
- For any command that could reveal data, require explicit confirmation and show exactly what will be sent.

**Warning signs:**
- Model outputs meta-instructions or repeats prompt fragments.
- “It did something totally different than the command” reports.

**Phase to address:**
Phase 4 (command mode + prompt discipline)

---

### Pitfall 9: Doing heavy work on the IME thread (lag/ANR)

**What goes wrong:**
Keyboard freezes while recording, streaming, or rewriting; Android kills the IME; user loses input focus. This is one of the fastest ways to get uninstalled.

**Why it happens:**
IME callbacks are frequent and time-sensitive; IPC calls to the editor can block; networking/model work is mistakenly done inline to “keep state simple.”

**How to avoid:**
- Isolate: UI + InputConnection edits on the IME thread; audio capture on a dedicated thread; networking on IO; model/tokenization on a worker pool.
- Introduce a single event-loop/state machine for dictation/rewrite sessions so concurrency stays deterministic.
- Enforce strict time budgets for any InputConnection read; avoid repeated context reads per token.

**Warning signs:**
- Jank while typing even without dictation.
- ANR traces referencing InputMethodService or binder calls.

**Phase to address:**
Phase 2/3/4 (concurrency model) + Phase 5 (profiling/perf gates)

---

### Pitfall 10: Audio capture “works on Pixel” but fails in real world (devices, BT, interruptions)

**What goes wrong:**
No audio on certain OEMs; distorted samples; Bluetooth mic not used; audio route changes mid-session; interruptions (calls, alarms) leave the recorder stuck; partial transcripts keep streaming after stop.

**Why it happens:**
Android audio is fragmented across devices and routes. IME is a service with different lifecycle constraints than an Activity. Teams under-test route changes and interruptions.

**How to avoid:**
- Build an explicit audio session manager: route changes, focus loss, errors, and cleanup are first-class events.
- Keep recorder lifecycle strictly scoped (always release on stop/failure).
- Provide a “diagnostics” view (current sample rate, audio source, route) for support.

**Warning signs:**
- “It records but transcribes garbage.”
- Sessions that never terminate or don’t release the mic.

**Phase to address:**
Phase 2 (audio subsystem + device matrix testing)

---

### Pitfall 11: Provider integration without a cancellation/retry contract

**What goes wrong:**
Stop button doesn’t really stop; old streaming responses arrive late and overwrite new text; retries duplicate text; rate limits cause cascading failures.

**Why it happens:**
Streaming STT/LLM APIs are event-driven and failure-prone. Without a strict session ID + cancellation semantics, late events corrupt editor state.

**How to avoid:**
- Every request has a unique session ID; drop any event that doesn’t match the current session.
- Cancellation must be best-effort *and* logically enforced client-side (ignore late packets).
- Centralize retry policy with backoff and explicit “safe to retry?” rules (don’t retry if it would duplicate committed text).

**Warning signs:**
- “I stopped but it kept typing.”
- Duplicated segments after flaky network.

**Phase to address:**
Phase 3 (streaming contract + provider abstraction)

---

### Pitfall 12: Per-app prompts/config become a hidden footgun

**What goes wrong:**
Users set a “funny” prompt for one app and forget; later it rewrites serious text. Or prompts accidentally include sensitive content; prompt management becomes un-debuggable.

**Why it happens:**
Per-app customization is powerful but invisible. Without visibility and safe defaults, it causes surprising output and support burden.

**How to avoid:**
- Make per-app prompt state visible in-keyboard (badge + tap to view).
- Provide versioned presets; keep user prompts separate from system policy.
- Add “reset to default” and “export/import” (sanitized) flows.

**Warning signs:**
- “Why is it always overly formal in this app?”
- Support cannot reproduce because config is per-app.

**Phase to address:**
Phase 4 (LLM UX + configuration management)

---

### Pitfall 13: Unicode/offset bugs (surrogate pairs, grapheme clusters) corrupt text

**What goes wrong:**
Emoji and some languages get split or deleted; selection offsets drift; replacements cut characters in half.

**Why it happens:**
Many text APIs are UTF-16 code-unit based; selection math done in code points/graphemes (or vice versa) creates invalid boundaries.

**How to avoid:**
- Treat all offsets as UTF-16 unless proven otherwise; avoid manual slicing when you can use editor-provided replace operations.
- When building diffs/patches, operate on strings with grapheme-aware libraries or keep operations to “replace exact selected range.”
- Add a dedicated test suite of tricky Unicode cases.

**Warning signs:**
- “Emoji disappeared” or “accented letters broke.”
- Bugs only in certain languages.

**Phase to address:**
Phase 1 (text engine correctness) + Phase 5 (fuzz/regression tests)

---

## Technical Debt Patterns

Shortcuts that seem reasonable but create long-term problems.

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Treat all editors as fully readable/writable (full context reads) | Better LLM results in one app | Breaks across apps; privacy/cost blowups | Never (must be capability-gated + budgeted) |
| Global singleton state for dictation/LLM sessions | Easy wiring | Cross-app leakage, late events corrupt text | Never |
| “Just commitText everything” (skip composing) | Simpler editing | No partials; worse UX; harder undo | Acceptable for early MVP fallback mode only |
| No app-level denylist/allowlist | Fewer settings | Can’t protect sensitive apps; policy risk | Never |
| “Undo = rely on app undo” | Less code | Inconsistent across apps; data loss | Only for non-destructive features |

## Integration Gotchas

Common mistakes when connecting to external services.

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| Streaming STT | Apply partials without anchor validation | Anchor + fingerprint; abort/pause on mismatch |
| LLM rewrite | Send full surrounding text by default | Selected text only by default; explicit opt-in for context |
| Multi-provider support | Provider-specific code leaks everywhere | Provider interface + normalized events + shared retry/cancel semantics |
| BYO keys | One shared key slot used for everything | Per-provider/per-feature keys + validation + secure storage |
| Offline/Local STT (if added) | Run inference in IME process | Isolate in separate process/service; strict resource budgets |

## Performance Traps

Patterns that work at small scale but fail as usage grows.

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Frequent large context reads (`getTextBeforeCursor`/extracted text loops) | Keyboard jank, battery drain | Cache small windows; strict budgets; read once per session | Immediately noticeable on mid-range devices |
| Applying updates per token/packet | Cursor flicker, high IPC load | Throttle partial UI/edits (e.g., 100–250ms) | At any meaningful streaming rate |
| Audio encoding on main/IME thread | Missed frames, lag | Dedicated audio thread + bounded queues | Under CPU contention |
| No backpressure | Memory growth, delayed typing after stop | Bounded channels; drop/merge partials | Long dictations / bad network |

## Security Mistakes

Domain-specific security issues beyond general app security.

| Mistake | Risk | Prevention |
|---------|------|------------|
| Logging keystrokes, selected text, transcripts, prompts | Sensitive data exposure via logs/crash reports | Redaction by default; separate secure logs; opt-in debug only |
| Storing API keys in plaintext prefs | Account takeover | Keystore-backed encryption; disable backups; never display full key |
| Sending text without explicit user action | “Keyboard spyware” perception + real leakage | Explicit “send” affordance; preview; per-app restrictions |
| No TLS error handling / accepting bad certs | MITM exfiltration | Default platform TLS; fail closed; no custom trust managers |
| Leaving audio artifacts on disk | Recoverable private speech | Stream in-memory; if cached, encrypt + delete aggressively |

## UX Pitfalls

Common user experience mistakes in this domain.

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Unclear recording state | Users speak when not recording (or vice versa) | Persistent, unambiguous mic state; haptics/sound optional |
| Stop doesn’t stop typing | Loss of control | Hard cancel semantics; session IDs; ignore late events |
| Auto-rewrite without preview/undo | Text loss; immediate uninstall | Preview/apply; big undo; “revert to original” |
| Too many modes (dictate/command/rewrite) | Confusion | Fewer primary actions; progressive disclosure |
| Per-app prompts hidden | Surprising output | Visible badge + quick reset |

## "Looks Done But Isn't" Checklist

- [ ] **Dictation:** Works in at least: SMS, Gmail, Chrome/WebView, Docs/Sheets, Compose-based app — verify cursor moves mid-stream don’t corrupt text.
- [ ] **Rewrite:** Always reversible with IME-level undo — verify original snapshot survives app switch and rotation.
- [ ] **Privacy:** Sensitive-field blocking — verify password/OTP fields are blocked even if user tries to force command mode.
- [ ] **Stop:** Cancellation is hard — verify late network packets cannot apply edits.
- [ ] **Keys:** Never appear in logs/backups — verify via logcat + Android Auto Backup behavior.
- [ ] **Performance:** No jank typing while dictation idle — verify with frame timing on mid-range device.

## Recovery Strategies

When pitfalls occur despite prevention, how to recover.

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Wrong insertion / duplicated text | MEDIUM | IME-level “Revert last dictation” using snapshot + anchor; offer to copy transcript to clipboard instead of applying |
| Rewrite clobbered content | HIGH | One-tap restore original; keep last N originals per app/session; if restore fails, show original for manual paste |
| Stuck composing underline | LOW | Force `finishComposingText` + commit empty; toggle “commit-only mode” for this editor |
| Provider outage/rate limit | LOW | Automatic fallback: local-only typing; queue transcript for manual apply; exponential backoff |

## Pitfall-to-Phase Mapping

How roadmap phases should address these pitfalls.

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| InputConnection realities | Phase 1 | Compatibility matrix test run across representative apps; null/partial reads handled |
| Composing vs commit correctness | Phase 1 | No stuck composition; consistent selection after edits; regression tests |
| Streaming race conditions | Phase 2–3 | Cursor-move during streaming triggers safe pause/abort; no corruption |
| Atomic rewrite + undo | Phase 4 | Always reversible via IME undo; no multi-step undo weirdness |
| Context over-collection | Phase 0 + 3–4 | Context budgets enforced; UI shows what is sent; costs stable |
| Sensitive field protection | Phase 0–1 | Password/OTP fields blocked; per-app denylist works |
| BYO key security | Phase 0 + 3–4 | Keys encrypted; not in logs/backups; panic wipe works |
| Prompt injection handling | Phase 4 | Selected text treated as data; safe presets; no prompt leakage |
| IME thread performance | Phase 2–5 | No ANRs; typing remains responsive under load |
| Audio route/device quirks | Phase 2 | Route changes handled; recorder always released; diagnostics confirm |
| Cancel/retry contract | Phase 3 | Late events dropped; retries don’t duplicate committed text |
| Per-app prompt footguns | Phase 4 | Prompt badge visible; reset works; export/import sanitized |
| Unicode correctness | Phase 1 + 5 | Emoji/non-Latin tests pass; no offset drift |

## Sources

- Practical Android IME experience (InputMethodService/InputConnection IPC quirks, composing/selection variability) — not fully re-verified via web due to tool fetch limitations.
- Practical streaming STT/LLM integration experience (cancellation/retry, late events, token/cost budgeting).
- Android platform security expectations for keyboards (treat as high-trust surface; avoid logging/sending data without explicit user intent).

---
*Pitfalls research for: Android dictation IME + LLM rewrite keyboard*
*Researched: 2026-02-26*
