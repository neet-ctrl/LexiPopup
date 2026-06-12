# LexiPopup — Advanced Android Popup Dictionary

A production-ready Android popup dictionary that integrates with Moon+ Reader and any app via PROCESS_TEXT intent. Instant definitions, Hindi meanings, offline-first, Groq AI fallback, with a beautiful Material 3 glassmorphism UI.

---

## 🚀 GitHub repo: https://github.com/neet-ctrl/LexiPopup

---

## ⚡ Quick start (3 steps)

```bash
# 1. Every push → debug APK auto-builds → download from Releases
git push origin main

# 2. Version release → signed APK
git tag v1.0.0 && git push origin v1.0.0

# 3. Generate dictionary packs (once) → GitHub Actions → "Generate Dictionary Packs" → Run
```

→ **See [SETUP_COMPLETE.md](SETUP_COMPLETE.md) for the full step-by-step guide including Termux commands.**

---

## CI/CD Workflows

| Workflow | Trigger | Result |
|----------|---------|--------|
| **Debug APK** | Every `git push` | Debug APK → GitHub pre-release `debug-latest` |
| **Release APK** | Push `v*` tag | Signed APK → GitHub Release |
| **Generate Dictionary Packs** | Manual (Actions tab) | `.db.gz` files → GitHub Release `dict-v1` |

No secrets to configure. `GITHUB_TOKEN` is automatic.

---

## Features

- **Instant popup** (<150ms) from Moon+ Reader or any app via PROCESS_TEXT
- **Offline-first** — 1,000 seed words built-in; Minimal/Standard/Full packs downloadable
- **Dual AI Layer** — Groq Cloud (free) + On-Device AI (Gemma 2B / Phi-2) + Hybrid mode
- **Hindi meanings** with Devanagari script and transliteration
- **Synonyms, antonyms, etymology, example sentences**
- **IPA pronunciation** with TTS (text-to-speech)
- **Glassmorphism + 3D effects** — parallax tilt, spring animations, particle burst
- **Flashcard system** with SM-2 spaced repetition
- **Vocabulary dashboard** — bar charts, pie chart, GitHub-style calendar heatmap
- **Export** vocabulary as CSV, JSON, or Anki TSV deck
- **Persistent notification** — quick search from anywhere
- **20+ toggleable UI elements** — customize every part of the popup
- **Dark mode** + Material You dynamic color

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Clean Architecture |
| DI | Hilt |
| Database | Room (SQLite) + WAL mode |
| Networking | Retrofit + OkHttp |
| AI (Cloud) | Groq API — llama-3.3-70b-versatile (free tier) |
| AI (Device) | MediaPipe LLM — Gemma 2B / Phi-2 |
| Background | WorkManager |
| Settings | DataStore Preferences |

---

## Word Lookup Flow (5 layers)

```
Word tapped in Moon+ Reader
  ↓ 1. LRU memory cache         <1 ms
  ↓ 2. Room SQLite (offline)    <5 ms  (seed + downloaded pack)
  ↓ 3. FreeDictionaryAPI        ~200ms (free, no key)
  ↓ 4. Groq AI Cloud            ~1-3s  (free API key from console.groq.com)
  ↓ 5. On-Device AI             ~3-8s  (Gemma 2B / Phi-2, downloaded once)
```

---

## Project Structure

```
LexiPopup/
├── .github/workflows/
│   ├── debug.yml            ← Auto debug APK on every push
│   ├── release.yml          ← Signed release APK on v* tag
│   └── generate-dicts.yml   ← Generate dictionary .db.gz packs
├── scripts/
│   └── generate_dicts.py    ← Python script for dict generation (WordNet-based)
├── app/src/main/java/com/lexipopup/
│   ├── data/
│   │   ├── download/        DictionaryDownloadWorker (checksum + resume)
│   │   ├── local/
│   │   │   ├── dao/         WordDao, VocabularyDao, FlashcardDao, ...
│   │   │   ├── database/    LexiDatabase (Room), DatabaseSeeder (1000 words)
│   │   │   └── entities/    WordEntity, VocabularyHistoryEntity, ...
│   │   └── remote/          DictionaryApi (FreeDictionaryAPI)
│   ├── di/                  AppModule, DataStoreModule
│   ├── domain/
│   │   ├── models/          WordEntry, AppSettings, Flashcard, ...
│   │   ├── repositories/    DictionaryRepository, VocabularyRepository
│   │   └── usecases/        LookupWordUseCase (5-layer), SpacedRepetitionUseCase
│   ├── presentation/
│   │   ├── ai/              AiSettingsScreen, AiSettingsViewModel
│   │   ├── dashboard/       DashboardScreen (5 tabs), DashboardViewModel
│   │   ├── flashcards/      FlashcardsScreen, FlashcardsViewModel
│   │   ├── onboarding/      OnboardingActivity, DatabasePackScreen
│   │   ├── popup/           PopupActivity, PopupScreen (glassmorphism UI)
│   │   └── theme/           Material 3 theme
│   └── utils/
│       ├── ai/              AiProvider, GroqAiProvider, OnDeviceAiProvider,
│       │                    AiProviderManager (Hybrid orchestrator)
│       ├── ExportHelper     CSV / JSON / Anki TSV
│       ├── NotificationHelper Persistent notification
│       ├── SettingsDataStore 30 toggle keys (all with defaults)
│       └── TtsHelper        TTS word/meaning speaking
└── SETUP_COMPLETE.md        ← Full step-by-step setup guide
```

---

## Moon+ Reader Setup

1. Install LexiPopup APK
2. Grant **"Display over other apps"** permission
3. Open Moon+ Reader → **Settings → Read → Dictionary app** → select **LexiPopup**
4. Long-press any word → **LexiPopup** → popup appears instantly

---

## Dictionary Pack Sources & Licenses

| Source | License | Words |
|--------|---------|-------|
| WordNet 3.1 (Princeton) | Free for all use | ~117,000 |
| CMU Pronouncing Dictionary | Free/Public Domain | IPA pronunciation |
| Hindi WordNet (IIT Bombay) | GNU FDL — non-commercial | ~28,000 |
| FreeDictionaryAPI | Free | Online fallback |

---

## Building Locally

Requirements: JDK 17, Android SDK (API 29+), Gradle 8.6

```bash
cd LexiPopup

# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run unit tests
./gradlew test
```

---

## Complete Setup Guide

**→ [SETUP_COMPLETE.md](SETUP_COMPLETE.md)** — Step-by-step guide including:
- Termux setup commands
- How to generate and host dictionary packs
- APK build + install
- Groq AI configuration
- Moon+ Reader setup
- Troubleshooting
