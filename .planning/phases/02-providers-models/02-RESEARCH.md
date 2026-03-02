# Phase 2: Providers & Models - Research

**Researched:** 2026-03-02
**Domain:** Android (Kotlin) provider/model configuration, selection, and endpoint routing (OpenAI-compatible + Gemini-compatible)
**Confidence:** HIGH (repo architecture), MEDIUM (provider API surface, esp. “OpenAI-compatible” variance)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
### Provider setup UX
- Provider names default to `type + host` and are user-editable.
- Base URL is prefilled per provider type and remains overrideable.
- Save is blocked until all required fields are valid.
- API key is required by default, with explicit no-auth mode for local/self-hosted endpoints.
- If deleting a provider that is currently selected, deletion flow requires reassignment before delete completes.
- Provider type is immutable after creation; include a Duplicate action to clone an existing provider into a new editable draft.
- Provide a manual non-blocking provider test action after save.
- When same type + base URL already exists, warn but still allow duplicates.

### Model catalog rules
- Models are manually manageable, with optional provider fetch/import.
- `multimodal` models are available in both STT and text selectors.
- New STT-capable models default `streaming partials supported` to off.
- If deleting a model currently selected, deletion flow requires reassignment before delete completes.

### Default selection behavior
- Active STT and text selections require explicit user choice; no auto-selection.
- Enhancement and command mode share one text selection by default, with optional advanced command override.
- If STT is set but text is unset, STT-only actions remain available while text-dependent actions stay blocked.
- When active selections become invalid, show an immediate setup-needed banner with CTA to the direct selectors page.
- Selection UIs show compatible options only.
- Active selection uses a two-step picker: provider, then model.
- Users can explicitly clear STT/text selections to `none`.
- If command override is unset, it inherits shared text selection changes until explicitly overridden.

### Compatibility behavior
- Provider type does not hard-block usage; compatibility filtering is based on model kind.
- For potentially unsupported operations, attempt the request and show provider response.
- Provider setup uses base URL only (no per-operation endpoint input).
- Test UI offers capability-specific tests and explicit test-anyway options.
- Error detail preference is full raw provider response.

### OpenCode's Discretion
- No explicit discretion granted beyond microcopy/layout details.

### Deferred Ideas (OUT OF SCOPE)

None - discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| PROV-01 | User can add/edit/delete providers (type, base URL, API key) and models per provider | Existing repo already persists providers via `SettingsRepository` + DataStore JSON and stores API keys in `SecretsStore` (Keystore-backed). Phase needs schema + UI changes to match base-URL + immutable type + reassignment-on-delete rules. |
| PROV-02 | Provider models include kind (stt/text/multimodal) + capability flags (at minimum: streaming partials supported) | Define `ModelKind` + `streamingPartialsSupported` in provider model catalog; wire compatibility filtering to selectors only (not hard provider-type). Note OpenAI transcription streaming: `stream=true` exists but is not supported for `whisper-1` per OpenAI docs. |
| PROV-03 | User can choose an STT model/provider for dictation | Persist an explicit `{sttProviderId, sttModelId}` selection (nullable) and integrate into IME dictation entry points. Two-step picker and compatible filtering is a UI requirement. |
| PROV-04 | User can choose a text model/provider for enhancement + command mode (can differ from STT) | Persist explicit `{textProviderId, textModelId}` plus optional `{commandOverrideProviderId, commandOverrideModelId}` with inheritance behavior. |
| PROV-05 | OpenAI-compatible endpoints can be used for STT and text transforms (user-configured base URL) | Treat base URL as user input (default `https://api.openai.com/v1`), derive operation paths (`/audio/transcriptions`, `/chat/completions`) from it; auth via `Authorization: Bearer ...` when API-key mode. |
| PROV-06 | Gemini-compatible endpoints can be used for text transforms (user-configured base URL) | Gemini REST discovery shows `rootUrl https://generativelanguage.googleapis.com/` + `v1beta/{model}:generateContent` and API key via `key` query param. Base URL should default to `https://generativelanguage.googleapis.com/v1beta` and app derives `/models/{model}:generateContent`. |
</phase_requirements>

## Summary

This repo already has the core persistence/security primitives Phase 2 needs: provider configs are stored as JSON in Preferences DataStore (`SettingsRepository`), and provider API keys are explicitly kept out of serialized JSON via `@Transient` and stored in `SecretsStore` (AES-GCM key in Android Keystore). Settings UI is Jetpack Compose with a simple NavHost routing scheme.

Phase 2 is primarily a product-level schema + UX alignment effort: migrate from “provider has a single endpoint + loose model list” to “provider has immutable type + base URL + auth mode, models have kind + streaming capability, and users explicitly select STT vs text provider/model via a two-step compatible picker”. The tricky parts are (1) safe migrations without leaking API keys, (2) keeping selections valid across edits/import/deletes, and (3) robust endpoint construction for OpenAI-compatible and Gemini-compatible base URLs.

**Primary recommendation:** keep DataStore JSON + SecretsStore as-is; introduce a new provider schema (baseUrl/authMode + model kind/capabilities) and migrate existing saved providers/keys forward while preserving the “no API keys in JSON” invariant.

## Standard Stack

### Core
| Library/Tech | Version | Purpose | Why Standard |
|---|---:|---|---|
| Kotlin + Android | (project) | App language/runtime | Existing codebase |
| Jetpack Compose (Material 3) | (project) | Settings UI | Existing `SettingsScreen.kt` uses M3 components |
| Preferences DataStore | (AndroidX) | Persist settings/providers JSON + selection IDs | Existing `Context.dataStore` + `SettingsRepository` |
| OkHttp | (project) | Provider HTTP calls (STT + transforms + tests) | Existing `WhisperTranscriber`/`SmartFixer` |
| Gson | (project) | Serialize/deserialize provider JSON | Existing `SettingsRepository` |

### Supporting
| Library/Tech | Version | Purpose | When to Use |
|---|---:|---|---|
| Android Keystore (AES/GCM) | platform | Secure provider API-key storage | Use via existing `SecretsStore` |
| OkHttp `HttpUrl` | (project) | Base URL parsing + host extraction | For `type + host` defaults, validation, disclosure formatting |

## Architecture Patterns

### Recommended Project Structure (Phase 2 additions follow existing conventions)
Existing relevant areas:
- `android/app/src/main/java/com/github/shekohex/whisperpp/data/` (settings persistence + models)
- `android/app/src/main/java/com/github/shekohex/whisperpp/privacy/` (SecretsStore + disclosures)
- `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/` (Compose settings screens)

### Pattern: Provider configs persisted as JSON + secrets out-of-band
**What:** Providers list serialized to a DataStore string; API keys stored separately and injected at call time.

**Example (repo):** `android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsRepository.kt` + `android/app/src/main/java/com/github/shekohex/whisperpp/privacy/SecretsStore.kt`

```kotlin
// SettingsRepository: providers JSON
val providers: Flow<List<ServiceProvider>> = dataStore.data.map { prefs ->
    val json = prefs[PROVIDERS_JSON]
    if (json.isNullOrEmpty()) emptyList() else gson.fromJson(json, type)
}

// ServiceProvider: apiKey is transient (not serialized)
data class ServiceProvider(
    /* ... */
    @Transient val apiKey: String = "",
)

// SecretsStore: Keystore-backed encryption for api keys
fun setProviderApiKey(providerId: String, apiKey: String) { /* ... */ }
fun getProviderApiKey(providerId: String): String? { /* ... */ }
```

**Planning implication:** Phase 2 should preserve this invariant: provider API keys never appear in exported/imported JSON (`SettingsExport.providers`) and are always injected via `provider.copy(apiKey = secretsStore.getProviderApiKey(id))` at call time.

### Pattern: Base URL + derived endpoint path
**What:** Store base URL only; derive per-operation endpoint paths consistently, and reuse the same formatter for disclosures.

**Example (repo):** `android/app/src/main/java/com/github/shekohex/whisperpp/privacy/PrivacyDisclosureFormatter.kt` already splits base URL and path, and has Gemini path derivation.

```kotlin
private fun toBaseUrl(url: HttpUrl): String = "${url.scheme}://${url.host}" // (port-aware in repo)

private fun geminiGenerateContentPath(basePath: String, selectedModelId: String): String {
    val model = selectedModelId.trim().ifEmpty { "{model}" }
    return "/models/$model:generateContent" // (prefixed/normalized in repo)
}
```

**Planning implication:** centralize endpoint building (both for execution and for test/disclosure UI) to avoid mismatched URLs and to make “base URL only” enforceable.

## Provider API Surface (what to know before planning UI + routing)

### OpenAI-compatible
**STT (transcription):** OpenAI documents `POST /audio/transcriptions` (under the `v1` base). Request is `multipart/form-data` with `file` + `model` and optional params; streaming exists via `stream=true` but is **ignored for `whisper-1`** per OpenAI docs.

**Text transforms:** OpenAI documents `POST /chat/completions` (under the `v1` base).

**Key planning choice:** treat “base URL” as including the version path (default `https://api.openai.com/v1`) and append relative operation paths (e.g. `audio/transcriptions`, `chat/completions`). This matches common OpenAI-compatible deployments (many self-hosted endpoints expose `/v1/*`).

### Gemini-compatible
Gemini REST discovery for `generativelanguage.googleapis.com` shows:
- `rootUrl`: `https://generativelanguage.googleapis.com/`
- `generateContent`: `POST v1beta/{+model}:generateContent` where `model` matches `models/{model}`
- API key in query parameter `key`.

**Key planning choice:** store base URL as `https://generativelanguage.googleapis.com/v1beta` (overrideable) and derive `/models/{model}:generateContent`.

## UI/UX Planning Notes (Compose)

### Provider create/edit
Current repo screens (`SettingsScreen.kt`) already support provider add/edit/delete; Phase 2 needs UX changes:
- Provider type immutable after creation (edit shows read-only type)
- Base URL field (not per-operation endpoint)
- Auth mode toggle (API key required by default; explicit “no-auth”)
- Save disabled until valid (URL validity + required fields based on auth mode)
- Default name = `type + host` (host parsed from base URL)
- Duplicate action creates a new draft cloned from an existing provider (type copied, but still immutable in the new draft)
- Delete provider requires reassignment if currently selected (STT/text/command override selectors)

### Models per provider
Repo currently treats models as `{id,name,isThinking}`; Phase 2 needs per-model:
- kind: `stt` / `text` / `multimodal`
- streaming partials supported (bool)

Selection UIs must filter compatibility by model kind only:
- STT picker shows models of kind `stt` + `multimodal`
- Text picker shows models of kind `text` + `multimodal`

### Active selections
Repo currently has `SPEECH_TO_TEXT_BACKEND` and SmartFix keys; Phase 2 needs explicit IDs:
- active STT `{providerId, modelId}` (nullable)
- active text `{providerId, modelId}` (nullable)
- optional command override `{providerId, modelId}` with inheritance rules

The IME entry points should gate behavior exactly per decision:
- STT-only actions OK if STT set but text unset
- Text-dependent actions blocked until text selection exists
- If selections become invalid (import/edit/delete), show a persistent “setup needed” banner with CTA into the selectors screen

## Common Pitfalls (plan to avoid)

### API key leakage via JSON/export
**What goes wrong:** provider API keys accidentally serialize into `providers_json` or export payload.
**Avoid:** keep `apiKey` transient + secrets stored only in `SecretsStore`; ensure any new provider DTOs keep secrets out of Gson serialization.

### Migration breaks due to Gson + non-null fields
**What goes wrong:** Gson can materialize missing JSON fields as `null` even for non-null Kotlin properties; current repo already compensates with `copy(...)` defaults.
**Avoid:** continue sanitize-on-read for new fields (auth mode, baseUrl, model kind/capabilities) and add a one-time migration flag similar to `PROVIDER_API_KEY_MIGRATION_DONE`.

### Base URL normalization
**What goes wrong:** double `v1` (e.g. user enters `.../v1` and code appends `/v1/...`), missing slashes, or losing ports.
**Avoid:** treat stored base URL as the authoritative prefix; append relative paths safely (OkHttp `HttpUrl` builder) and keep ports.

### Selection invalidation edge cases
**What goes wrong:** provider/model deleted or removed by import causes dangling selection IDs; app later crashes or silently falls back.
**Avoid:** explicitly allow `none` selections; validate selections on every providers list change and surface banner + CTA immediately.

### “OpenAI-compatible” variance
**What goes wrong:** some OpenAI-compatible servers diverge on request shape, supported params, or endpoint paths.
**Avoid:** keep compatibility logic minimal: base URL + standard path + standard headers; for unsupported operations, attempt and surface raw response (as per decisions) in the provider test UI.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---|---|---|---|
| Secret storage/encryption | Custom crypto or plaintext prefs | `SecretsStore` (Android Keystore AES/GCM) | Avoid foot-guns, aligns with PRIV-02 |
| URL parsing/host extraction | Regex URL parsing | OkHttp `HttpUrl` (`toHttpUrlOrNull()`) | Correct handling of ports/paths |
| Settings persistence | Custom files/SQLite for this phase | Preferences DataStore + JSON (`SettingsRepository`) | Existing pattern + export/import already built |

## Code Examples (repo patterns to reuse)

### Inject API key at call time (runtime-only)
Source: `android/app/src/main/java/com/github/shekohex/whisperpp/WhisperInputService.kt`

```kotlin
val providerWithApiKey = provider.copy(
    apiKey = secretsStore.getProviderApiKey(provider.id).orEmpty()
)
```

### Privacy disclosure uses baseUrl + derived path
Source: `android/app/src/main/java/com/github/shekohex/whisperpp/privacy/PrivacyDisclosureFormatter.kt`

```kotlin
val baseUrl = toBaseUrl(parsed)
val path = if (provider.type == ProviderType.GEMINI) {
    geminiGenerateContentPath(parsed.encodedPath, selectedModelId)
} else {
    parsed.encodedPath
}
```

## Open Questions (flag for planner)

1. **HTTP vs HTTPS for local/self-hosted base URLs**
   - Locked decision explicitly calls out local/self-hosted no-auth mode; repo guidelines say “Use HTTPS endpoints only.”
   - Recommendation: allow `http://` only for localhost/LAN patterns (MEDIUM confidence; needs explicit product decision).

2. **Current repo has provider types beyond OpenAI/Gemini**
   - Existing `ProviderType` includes `WHISPER_ASR` and generic `CUSTOM` with endpoint-based behavior.
   - Recommendation: keep types but map Phase 2 “OpenAI-compatible” to both `OPENAI` and “OpenAI-compatible custom” semantics, or collapse types; needs a deliberate migration plan.

3. **Error messaging: “raw provider response” vs “safe error summary”**
   - Phase 2 decisions want raw provider response detail; earlier project state prefers constrained errors.
   - Recommendation: show raw response only in the explicit Provider Test UI; keep normal runtime errors safe/summary.

## Sources

### Primary (HIGH confidence)
- Repo code (persistence/security/UI):
  - `android/app/src/main/java/com/github/shekohex/whisperpp/data/SettingsRepository.kt`
  - `android/app/src/main/java/com/github/shekohex/whisperpp/privacy/SecretsStore.kt`
  - `android/app/src/main/java/com/github/shekohex/whisperpp/ui/settings/SettingsScreen.kt`
  - `android/app/src/main/java/com/github/shekohex/whisperpp/privacy/PrivacyDisclosureFormatter.kt`
- Gemini REST discovery (shows `key` query param + v1beta paths): https://generativelanguage.googleapis.com/$discovery/rest?version=v1beta

### Secondary (MEDIUM confidence)
- OpenAI API reference:
  - Audio transcription endpoint + streaming note: https://platform.openai.com/docs/api-reference/audio/createTranscription
  - Chat completions endpoint: https://platform.openai.com/docs/api-reference/chat/create

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH (present in repo)
- Architecture: HIGH (patterns already implemented)
- Provider API details: MEDIUM (OpenAI-compatible ecosystems vary; Gemini verified via discovery)

**Research date:** 2026-03-02
**Valid until:** 2026-04-01
