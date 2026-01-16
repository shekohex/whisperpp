# Whisper++

An Android keyboard that performs speech-to-text (STT/ASR) using OpenAI Whisper. Input recognized text seamlessly in any app.

## Features

- **Modern UI**: Settings and Keyboard UI rewritten in Jetpack Compose.
- **Edge-to-Edge**: Full support for modern Android displays.
- **WAV PCM Recording**: Reliable high-quality audio recording with WAV header support.
- **Auto-Transcribe**: Automatically stops and transcribes after a pause in speaking.
- **Recording Timer**: Real-time display of recording duration.
- **Custom Feedback**: Haptic and audio feedback during recording.
- **Multiple Backends**:
    - **OpenAI API**: Official Whisper-1 model.
    - **Whisper ASR Webservice**: Self-hosted open-source backend.
    - **NVIDIA NIM**: Highly optimized self-hosted backend.
    - **OpenAI Compatible**: Supports any API that follows the OpenAI Whisper specification.
- **SmartFix (LLM Post-processing)**: Automatically cleans up, punctuates, and corrects transcriptions using LLMs (OpenAI GPT or Google Gemini).

## Installation

1. Download the latest APK from the [Releases](https://github.com/shekohex/whisper-to-input/releases) page.
2. Install the APK (allow "Install from unknown sources").
3. Grant `RECORD_AUDIO` and `POST_NOTIFICATIONS` permissions.
4. Enable **Whisper++** in your system Language & Input settings.

## Configuration

Open the **Whisper++** app from your launcher to configure your preferred STT backend:

### OpenAI API
- **Endpoint**: `https://api.openai.com/v1/audio/transcriptions`
- **Model**: `whisper-1`
- **API Key**: Your OpenAI secret key.

### Self-Hosted (Whisper ASR / NVIDIA NIM / Custom)
- **Endpoint**: `http://<YOUR_IP>:9000/asr` (or `/v1/audio/transcriptions`)
- **API Key**: Optional (if required by your proxy).

### SmartFix Configuration
Enable **SmartFix** in settings to use an LLM for post-processing:
- **Backends**: OpenAI (any compatible provider) or Google Gemini.
- **Custom Prompts**: Fine-tune how the LLM cleans up your text.

## Development & CI/CD

This project uses **Fastlane** and **GitHub Actions** for automated releases.

- **Nightly Builds**: Every push to `main` triggers a nightly release.
- **Official Releases**: Tagged pushes (e.g., `v1.0.0`) generate full releases with automated changelogs.

### Build Locally

```bash
JAVA_HOME="/path/to/jdk-17" ./android/gradlew assembleDebug
```

## License

GPLv3 - See [LICENSE](LICENSE) for details.
