# Feature Research

**Domain:** Android speech-to-text (dictation) keyboard (IME) + LLM rewrite/transform (“command mode” on selected text)
**Researched:** 2026-02-26
**Confidence:** MEDIUM

## Feature Landscape

### Table Stakes (Users Expect These)

Features users assume exist. Missing these = product feels incomplete.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Push-to-talk dictation (mic button) | Clear, intentional capture; avoids “always listening” fear | MEDIUM | Needs robust IME UI state + AudioRecord lifecycle + permission UX |
| Fast start/stop + visible recording state | Users abandon if latency/confusing state | MEDIUM | Warm-up, VAD thresholds, cancellation, “listening…” indicator |
| Insert dictated text into any text field that allows IME input | “Keyboard must work everywhere” baseline | HIGH | Many edge cases: rich editors, WebView, Compose vs Views, input connection quirks |
| Basic punctuation voice phrases | Users expect “comma/period/new line” | MEDIUM | Gboard documents phrases like “Period, Comma, New line, New paragraph” and more |
| Capitalization + sentence casing | Minimizes manual cleanup | MEDIUM | Heuristics + optional LLM pass; must be predictable |
| Partial/streaming results (when supported) | Feels realtime, fewer end-of-utterance delays | HIGH | Depends on provider + insertion strategy (compose events, cursor mgmt) |
| Manual corrections without fighting the keyboard | Users frequently correct names/terms | MEDIUM | Cursor navigation, selection, deleting dictated chunk; don’t “re-dictate” incorrectly |
| Undo last insertion/replacement | Safety net for misrecognition | MEDIUM | Track change ranges; handle multi-step insertion and clipboard fallback |
| Language selection + multilingual support | Dictation keyboards typically support multiple languages | MEDIUM | UI to pick language; provider model/language mapping; remember per-app |
| Error handling + offline/poor network messaging | Cloud STT fails; user needs clear recovery | MEDIUM | Timeouts, retry, “tap to re-send”, don’t silently drop audio |
| Privacy basics: explicit permission prompts + clear policy knobs | Voice capture is sensitive | MEDIUM | Opt-in telemetry, “never store audio” mode when possible; redact logs |
| Battery/thermal awareness | Mic + streaming can drain fast | MEDIUM | Avoid continuous background work; backoff; choose sample rates wisely |
| Works with common keyboard affordances | Users expect clipboard, emoji, basic toolbar | LOW | Not core to dictation, but missing basics hurts adoption |
| Doesn’t break secure fields / respects OS restrictions | Password fields often disallow IME access | MEDIUM | Detect and disable dictation for restricted fields; explain why |

### Differentiators (Competitive Advantage)

Features that set the product apart. Not required, but valuable.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| BYO provider (endpoint/key) for STT + LLM | Power users choose cost/quality/privacy; avoids vendor lock-in | HIGH | Requires “OpenAI-compatible” STT plus multi-provider LLM adapters + key vaulting |
| On-device / local-first mode (where feasible) | Strong privacy + works offline | HIGH | Model size, CPU/GPU constraints; packaging/update strategy; may be phased |
| “Command mode” on selected text (transform/operate) | Turns keyboard into an editing tool, not just dictation | HIGH | Needs reliable selected-text retrieval; fallback flow when selection access is limited |
| Transform library: rewrite (shorten/expand), tone (formal/casual), fix grammar, summarize, translate | Direct value for LLM-enhanced keyboard | MEDIUM | Must preserve meaning; provide multiple candidates; avoid over-editing |
| Per-app prompt profiles | Different writing style by app (Slack vs Email) | MEDIUM | Profile selection UX + app detection + safe defaults |
| Realtime “compose then insert” vs “type as you speak” modes | Better for different apps/editors | HIGH | Some fields hate streaming edits; offer stable final-insert mode |
| Glossary / custom vocabulary + hotwords | Improves names/brands/technical terms | MEDIUM | Provider-specific vocab injection where supported; otherwise post-correction |
| Structured voice commands (edit/navigation) | Hands-free control (delete last word, move cursor, etc.) | HIGH | Needs command grammar, locale handling, conflict resolution with normal dictation |
| Privacy controls beyond baseline (per-app allowlist, local logs off by default) | Trust differentiator | MEDIUM | Per-app policies, “never send from this app”, domain allowlists |
| Auditability: show transcript diff + change highlights | Confidence in LLM rewrites | MEDIUM | Diff UI, reversible operations, “apply” vs “copy” flows |
| Latency/cost controls (model selection, streaming toggles) | Power users tune for speed or accuracy | MEDIUM | UI complexity; defaults matter |
| Enterprise-ish mode: self-hosted endpoints, policy export/import | Teams adoption | HIGH | Provisioning UX, secure storage, backup/restore |

### Anti-Features (Commonly Requested, Often Problematic)

Features that seem good but create problems.

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Always-on / hotword listening | “Hands-free” convenience | Major privacy/trust risk; battery drain; higher false triggers; platform restrictions | Push-to-talk + optional accessibility shortcut; explicit “listening” UI |
| Auto-rewrite everything by default | “Make my writing better” | Surprising edits; tone drift; higher error impact in professional contexts | Opt-in transforms; preview + diff; apply per-app |
| Hidden cloud sync of audio/transcripts | “Seamless across devices” | Sensitive data exfiltration; compliance risk | Explicit export/import; user-controlled sync targets |
| Aggressive autocorrect over dictated text | “Fewer typos” | Can destroy proper nouns/technical terms; fights STT | Separate modes: STT-first, optional post-process with revert |
| Deep integration requiring Accessibility overlay for everything | “Works in all apps” | Fragile, high review risk, user distrust | Prefer IME InputConnection; use clipboard fallback for hard cases |
| Recording when UI not visible | “Continue dictation while switching apps” | Hard to communicate mic state; security red flags | Stop on focus loss; require explicit re-start |
| Collecting/learning from user text by default | “Better personalization” | Privacy; regulatory risk | Local-only personalization; explicit opt-in |
| Ad-supported keyboard | “Free” | UX degradation + trust issues | Paid tier / BYO keys; keep UI minimal |

## Feature Dependencies

```
[IME foundation + insertion engine]
    └──requires──> [Mic capture + permission UX]
                       └──requires──> [STT provider integration]
                                         └──enables──> [Dictation insertion + undo]

[Selected-text access]
    └──requires──> [Reliable selection retrieval]
                       └──fallback──> [Clipboard-based selection workflow]
                                         └──enables──> [Command mode transforms]

[LLM transforms]
    └──requires──> [Text-in/text-out provider integration]
                       └──enhanced by──> [Per-app prompt profiles]
                       └──enhanced by──> [Diff preview + undo for replacements]

[Streaming / realtime composing]
    └──conflicts with──> [Editors that dislike frequent edits]
            └──mitigate──> [Final-insert mode + per-app capability flags]
```

### Dependency Notes

- **Dictation insertion requires IME foundation + insertion engine:** Without robust cursor/range tracking, undo and partial results become unreliable.
- **Command mode transforms require selected-text access:** Many apps limit what IMEs can read; clipboard fallback is essential for coverage.
- **LLM transforms require provider integration + safety UX:** Must guard against accidental sensitive data sending; per-app policies should gate.
- **Streaming conflicts with some editors:** Provide a “commit-only” mode (insert once at end) and/or per-app heuristics.

## MVP Definition

### Launch With (v1)

- [ ] Push-to-talk dictation + clear recording state — core product value
- [ ] Basic punctuation phrases + casing — reduces cleanup work
- [ ] Reliable insertion engine + undo — safety + usability
- [ ] BYO STT endpoint/key (OpenAI-compatible) — core project positioning
- [ ] Selected-text command mode with clipboard fallback — validates “LLM keyboard” angle
- [ ] 3–5 transforms: fix grammar, rewrite concise, rewrite formal/casual, translate — high value with limited scope
- [ ] Per-app profiles (minimal) — at least: default + per-app override

### Add After Validation (v1.x)

- [ ] Streaming partial results (where supported) — once insertion is stable
- [ ] Glossary/custom vocabulary — improves accuracy for power users
- [ ] Diff preview + multi-candidate rewrite — improves trust/acceptance
- [ ] Per-app privacy policies (denylist/allowlist) — “don’t send from banking apps”

### Future Consideration (v2+)

- [ ] On-device/local-first STT or rewrite — large eng + distribution cost
- [ ] Rich voice command grammar (cursor nav, select, delete ranges) — high complexity + locale work
- [ ] Enterprise provisioning/export/import — only after clear demand

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Push-to-talk dictation | HIGH | MEDIUM | P1 |
| Reliable insertion + undo | HIGH | HIGH | P1 |
| Basic punctuation phrases | HIGH | MEDIUM | P1 |
| BYO STT endpoint/key | HIGH | HIGH | P1 |
| Selected-text command mode (clipboard fallback) | HIGH | HIGH | P1 |
| Core LLM transforms (rewrite/tone/translate) | HIGH | MEDIUM | P1 |
| Per-app prompt profiles (basic) | MEDIUM | MEDIUM | P1 |
| Diff preview + multi-candidate apply | MEDIUM | MEDIUM | P2 |
| Streaming partial results | MEDIUM | HIGH | P2 |
| On-device/local-first mode | HIGH | HIGH | P3 |

**Priority key:**
- P1: Must have for launch
- P2: Should have, add when possible
- P3: Nice to have, future consideration

## Competitor Feature Analysis

| Feature | Competitor A (Gboard voice typing) | Competitor B (Grammarly mobile) | Our Approach |
|---------|-----------------------------------|---------------------------------|-------------|
| Talk-to-text in apps | Documented “Talk to write” in supported contexts | Not the core pitch (writing assistance is) | Core (IME-first) |
| Punctuation phrases | Documented phrases like period/comma/new line/new paragraph | N/A | Support phrases + optional customization |
| Replace word by voice | Documented “Replace a word” flow | N/A | Consider as v1.x (depends on selection/edit engine) |
| Voice toolbar / command discovery | Documented voice toolbar + “Show voice commands” | N/A | Command-mode UI for transforms + voice-command hints |
| Writing polish (grammar/spelling) | General keyboard features; plus emerging “proofread” reported in media | Core pitch: real-time suggestions | Offer LLM polish as explicit operation (preview + undo) |
| Tone checking | Not core to voice typing docs | Documented tone analysis | Provide tone transform + per-app tone profiles |
| Rewrite suggestions | Media reports “proofread”/rewrite-style help; varies by device rollout | Documented “Rewriting made easy” via generative AI | Provide deterministic transforms + multi-candidate suggestions |
| Cross-app coverage | High for Gboard as default keyboard | Claims works across iOS/Android apps | Focus on robust IME + clipboard fallback for hard cases |

## Sources

- Google Gboard Help: “Type with your voice” (talk-to-write + punctuation phrases + voice toolbar) — https://support.google.com/gboard/answer/2781851?hl=en
- Microsoft SwiftKey product page (baseline keyboard features; not dictation-focused) — https://www.microsoft.com/en-us/swiftkey
- Grammarly for Mobile (tone analysis + generative rewrite + real-time suggestions across apps) — https://www.grammarly.com/keyboard
- Reports on Gboard “proofread” feature (non-official; verify at implementation time):
  - https://9to5google.com/2024/02/21/gboard-ai-proofreading-rolling-out/
  - https://www.androidpolice.com/gboard-on-device-proofread-feature-gemini-nano/
  - https://www.androidheadlines.com/2024/02/gboard-ai-proofreading-on-device-without-internet.html

---
*Feature research for: Android dictation IME + LLM rewrite keyboard*
*Researched: 2026-02-26*
