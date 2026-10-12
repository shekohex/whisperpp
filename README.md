<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="app_icon.svg">
    <img alt="Whisper++ Logo" src="app_icon.svg" width="200">
  </picture>
  <br>
  <b>Whisper++</b>
  <br>
  <i>Seamless speech-to-text integration for Android.</i>
  <br><br>
  <a href="https://github.com/shekohex/whisperpp/releases/latest">
    <img src="https://img.shields.io/github/v/release/shekohex/whisperpp?label=Stable&logo=github&style=for-the-badge&color=green" alt="Stable Release">
  </a>
  <a href="https://github.com/shekohex/whisperpp/releases/tag/nightly">
    <img src="https://img.shields.io/badge/Nightly-Download-blue?logo=github&style=for-the-badge" alt="Nightly Release">
  </a>
</p>

An Android keyboard that turns speech into text in any app, using the cloud speech-to-text provider you choose. Audio is only ever sent to that provider; there are no on-device models.

## Features

- **Telegram-style recording**: hold to talk, slide left to cancel, slide up to lock hands-free, or tap to start (configurable). A live waveform follows your voice.
- **Pauses your music** while recording and resumes it afterwards (can be turned off).
- **Providers**: OpenAI and any OpenAI-compatible API (Groq, self-hosted Whisper ASR webservice, NVIDIA NIM, ...), plus Google Gemini and the cloud speech providers Deepgram, ElevenLabs, Mistral (Voxtral), Soniox, Speechmatics, AssemblyAI, xAI and Cartesia. Add a key, tap **Test connection**, and pull the model list straight from the provider.
- **Realtime transcription**: partial text appears while you speak (OpenAI Realtime, Deepgram, ElevenLabs, Mistral, Soniox, Speechmatics, AssemblyAI, xAI, Cartesia), with an automatic fall back to normal transcription where the provider offers it (Deepgram, ElevenLabs, Mistral and the OpenAI-compatible providers). Soniox, Speechmatics, AssemblyAI, xAI and Cartesia are realtime only.
- **Enhancement**: clean up, punctuate and rewrite the transcript with an LLM (OpenAI-compatible or Gemini). If enhancement fails you still get your raw text.
- **Modes**: bundle language, models, prompt and output behaviour, and assign modes to apps. Switch modes from the keyboard.
- **Dictionary**: word replacements and custom vocabulary. Optional **Auto-Learn** (off by default) proposes new entries from the corrections you make by hand.
- **History and insights**: searchable transcript history (text only, audio kept only if you opt in), time saved, activity and model usage.
- **Privacy first**: API keys are encrypted with the Android Keystore, secure fields and blocked apps are never dictated into, and encrypted backup/restore keeps your settings portable.
- **Material 3** with dynamic color, edge-to-edge, tablet-friendly layouts, TalkBack actions, and Arabic (RTL) localization.

## Installation

1. Download the latest APK from the [Releases](https://github.com/shekohex/whisperpp/releases) page (a [nightly build](https://github.com/shekohex/whisperpp/releases/tag/nightly) is published for every push to `main`).
2. Install the APK (allow "Install from unknown sources").
3. Open **Whisper++**. The first-run guide walks you through enabling the keyboard, selecting it, granting the microphone permission and adding a provider.
4. In any text field, switch to Whisper++ and press the mic.

The microphone permission is requested only when you ask for it, either in the first-run guide or the first time you press the mic on the keyboard.

## Configuration

### OpenAI
- **Endpoint**: `https://api.openai.com/v1/audio/transcriptions`
- **Model**: for example `whisper-1` or `gpt-4o-transcribe`
- **API key**: your OpenAI secret key.

### Self-hosted (Whisper ASR / NVIDIA NIM / custom)
- **Endpoint**: `https://<YOUR_SERVER>:9000/asr` (or `/v1/audio/transcriptions`)
- **API key**: optional (if required by your proxy).
- **HTTPS is required** for all servers. Plain `http://` is only allowed for local-network servers
  (`localhost`, `10.x.x.x`, `172.16-31.x.x`, `192.168.x.x`, link-local, or `*.local`), e.g. `http://192.168.1.10:9000/asr`.

### Using a gateway (LiteLLM etc.) for realtime
Realtime dictation (text while you speak) works with any OpenAI-compatible gateway that exposes the Realtime API over WebSocket at `/v1/realtime`, for example LiteLLM.
1. Add a **Custom** provider with your gateway as the base URL (`https://litellm.example.com`, `https://litellm.example.com/v1` or `https://host/litellm/v1`; a LAN address such as `http://192.168.1.10:4000` is fine).
2. Add the transcription model exactly as the gateway names it (the `model_name` in your LiteLLM config) and tick **Realtime (WebSocket)** on it. OpenAI's own `whisper-1` and `*-transcribe` models stream without the checkbox.
3. Tap **Test connection**. For a realtime model it also opens the WebSocket and reports whether the key was rejected, the gateway has no `/v1/realtime` (HTTP 404/426), or the session was never confirmed.

The app connects to `wss://<gateway>/v1/realtime?model=<model>&intent=transcription` and uses the current (GA) session format; if the gateway rejects it, it retries once with the older beta format. If realtime fails for any reason, the recording is transcribed normally instead. The **Use realtime transcription when available** switch in Dictation settings turns it off globally.

### Enhancement
Turn on **Enhancement** and pick a text model (OpenAI-compatible or Gemini). Prompts and presets are editable, and each mode can use its own.

## Development & CI/CD

This project uses **Fastlane** and **GitHub Actions** for automated releases.

- **Nightly Builds**: Every push to `main` triggers a nightly release.
- **Official Releases**: Tagged pushes (e.g., `v1.0.0`) generate full releases with automated changelogs.

### Build Locally

```bash
JAVA_HOME="/path/to/jdk-17" ./android/gradlew -p android assembleDebug lintDebug testDebugUnitTest
```

## License

GPLv3 - See [LICENSE](LICENSE) for details.
