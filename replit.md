# LexiPopup

An advanced Android popup dictionary that integrates with Moon+ Reader via PROCESS_TEXT intent, featuring offline-first lookup, Groq AI fallback, on-device AI, Hindi meanings, flashcards with SM-2 spaced repetition, and a glassmorphism Material 3 UI.

## Run & Operate

This is an Android project — it is not run via pnpm. The app is built with Gradle.

- Build debug APK: push to GitHub → Actions auto-build (see SETUP_COMPLETE.md)
- OR locally: `cd LexiPopup && ./gradlew assembleDebug`
- Generate dictionary packs: GitHub Actions → "Generate Dictionary Packs" → Run workflow
- Required: JDK 17, Android SDK API 29+

## Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Architecture**: MVVM + Clean Architecture + Hilt DI
- **Database**: Room (SQLite) + WAL mode, DataStore Preferences
- **Networking**: Retrofit + OkHttp
- **AI Cloud**: Groq API (llama-3.3-70b-versatile, free tier)
- **AI Device**: MediaPipe LLM (Gemma 2B / Phi-2 .task models)
- **Background**: WorkManager (dictionary downloads, flashcard reminders)

## Where things live

- Android project root: `LexiPopup/`
- App source: `LexiPopup/app/src/main/java/com/lexipopup/`
- Dictionary download worker: `data/download/DictionaryDownloadWorker.kt`
- AI providers: `utils/ai/` (AiProvider, GroqAiProvider, OnDeviceAiProvider, AiProviderManager)
- AI settings UI: `presentation/ai/` (AiSettingsScreen, AiSettingsViewModel)
- Database schema (Room entity): `data/local/entities/WordEntity.kt`
- Settings keys: `utils/SettingsDataStore.kt`
- Dictionary generator Python script: `LexiPopup/scripts/generate_dicts.py`
- GitHub Actions workflows: `LexiPopup/.github/workflows/`

## Architecture decisions

- **5-layer word lookup**: LRU cache → Room SQLite → FreeDictionaryAPI → Groq AI → On-Device AI
- **State-based navigation**: sealed class `AppDestination` (no Jetpack Nav component); overlays via early return
- **AI is last resort**: AI is only called when offline DB + online API both fail
- **Dictionary packs**: gzipped SQLite databases hosted on GitHub Releases; downloaded via WorkManager with resume support and SHA-256 verification
- **Dual AI system**: Groq Cloud (free, fast) + MediaPipe on-device (offline) + Hybrid mode that runs both in parallel

## Product

LexiPopup is a floating popup dictionary for Android. Long-press any word in Moon+ Reader (or any app) to get instant English definitions, Hindi meanings, synonyms, antonyms, etymology, IPA pronunciation, and AI explanations — all in a beautiful glassmorphism popup. Includes flashcards, vocabulary tracking, and full offline support.

## User preferences

_Populate as you build._

## Gotchas

- Dictionary pack URLs in `DictionaryDownloadWorker.kt` point to `github.com/neet-ctrl/LexiPopup/releases/download/dict-v1/`. Run the "Generate Dictionary Packs" GitHub Actions workflow first to create that release, then update the SHA-256 checksums in the same file.
- Checksums set to `"SKIP"` during development — update to real SHA-256 values before shipping.
- `@file:OptIn(ExperimentalMaterial3Api::class)` is needed in `AiSettingsScreen.kt` because `ExposedDropdownMenuBox` and `menuAnchor()` are still experimental in Material3 1.2.x.
- MediaPipe `tasks-genai:0.10.14` uses `.task` model files (not `.gguf`) — model files go in `filesDir`, not `assets`.
- Hilt requires `hilt-work` for `@HiltWorker`-annotated WorkManager workers — both `hilt-work` and `hilt.work.compiler` (KSP) must be in `build.gradle.kts`.

## Pointers

- Full user-facing setup guide: `LexiPopup/SETUP_COMPLETE.md`
- User guide: `LexiPopup/USER_GUIDE.md`
- Project README: `LexiPopup/README.md`
