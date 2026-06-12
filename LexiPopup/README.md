# LexiPopup — Advanced Android Popup Dictionary

A production-ready Android popup dictionary that integrates with Moon+ Reader and any app via PROCESS_TEXT intent. Instant definitions, Hindi meanings, offline-first, Groq AI fallback, with a beautiful Material 3 glassmorphism UI.

---

## 🚀 GitHub repo: https://github.com/neet-ctrl/LexiPopup

---

## ⚡ Quick start (3 steps)

```bash
# 1. Push code to GitHub
git push origin main

# 2. Generate dictionary packs (once):
#    GitHub → Actions → "Generate Dictionary Packs" → Run workflow
#    (auto-patches checksums into the app, auto-commits — you just wait)

# 3. Build APK:
#    GitHub → Actions → "Debug APK" → Run workflow   (debug, any time)
#    GitHub → Actions → "Release APK" → Run workflow → enter v1.0.0   (signed release)
```

→ **See [SETUP_COMPLETE.md](SETUP_COMPLETE.md) for the full step-by-step guide.**

---

## CI/CD Workflows

All three workflows live at **`.github/workflows/`** in the repository root (not inside `LexiPopup/`).

| Workflow | Trigger | Result |
|----------|---------|--------|
| **Debug APK** | Manual (Actions tab) | Debug APK → GitHub pre-release `debug-latest` |
| **Release APK** | Manual — enter version tag (e.g. `v1.0.0`) | Signed APK → GitHub Release |
| **Generate Dictionary Packs** | Manual (Actions tab) | `.db.gz` files → GitHub Release `dict-v1`; checksums auto-patched into app |

No secrets to configure. `GITHUB_TOKEN` is automatic. Keystore is embedded.

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
.github/workflows/               ← Root-level (triggers on GitHub)
├── debug.yml                    ← Manual debug APK build
├── release.yml                  ← Manual signed release APK
└── generate-dicts.yml           ← Manual dictionary pack generation + auto-checksum patch

LexiPopup/
├── scripts/
│   └── generate_dicts.py        ← Python script: Wiktionary + WordNet + Hindi WordNet
├── app/src/main/java/com/lexipopup/
│   ├── data/
│   │   ├── download/            DictionaryDownloadWorker, DownloadStateStore, DatabasePack enum
│   │   ├── local/
│   │   │   ├── dao/             WordDao, VocabularyDao, FlashcardDao, ...
│   │   │   ├── database/        LexiDatabase (Room), DatabaseSeeder (1000 words)
│   │   │   └── entities/        WordEntity, VocabularyHistoryEntity, ...
│   │   └── remote/              DictionaryApi (FreeDictionaryAPI)
│   ├── di/                      AppModule, DataStoreModule
│   ├── domain/
│   │   ├── models/              WordEntry, AppSettings, Flashcard, ...
│   │   ├── repositories/        DictionaryRepository, VocabularyRepository
│   │   └── usecases/            LookupWordUseCase (5-layer), SpacedRepetitionUseCase
│   ├── presentation/
│   │   ├── ai/                  AiSettingsScreen, AiSettingsViewModel
│   │   ├── dashboard/           DashboardScreen (5 tabs), DashboardViewModel
│   │   ├── flashcards/          FlashcardsScreen, FlashcardsViewModel
│   │   ├── onboarding/          OnboardingActivity, DatabasePackScreen
│   │   ├── popup/               PopupActivity, PopupScreen (glassmorphism UI)
│   │   └── theme/               Material 3 theme
│   └── utils/
│       ├── ai/                  AiProvider, GroqAiProvider, OnDeviceAiProvider,
│       │                        AiProviderManager (Hybrid orchestrator)
│       ├── ExportHelper         CSV / JSON / Anki TSV
│       ├── NotificationHelper   Persistent notification
│       ├── SettingsDataStore    30 toggle keys (all with defaults)
│       └── TtsHelper            TTS word/meaning speaking
└── SETUP_COMPLETE.md            ← Full step-by-step setup guide
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
| Wiktionary (kaikki.org JSONL) | CC BY-SA 3.0 | ~700,000+ English entries |
| WordNet 3.1 (Princeton) | Free for all use | ~155,000 lemmas |
| CMU Pronouncing Dictionary | Public Domain | IPA pronunciation |
| Hindi WordNet / OMW 1.4 (IIT Bombay) | GNU FDL — non-commercial | Hindi meanings |
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
- How to generate dictionary packs (fully automated via GitHub Actions)
- APK build + install
- Groq AI configuration
- Moon+ Reader setup
- Troubleshooting
