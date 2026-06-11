# LexiPopup - Advanced Android Popup Dictionary

A production-ready Android popup dictionary that integrates with Moon+ Reader and any app via PROCESS_TEXT intent. Instant definitions, Hindi meanings, offline-first, with a beautiful modern 3D UI.

---

## 🚀 Push to GitHub in 4 Commands

The `LexiPopup/` folder **IS** your complete Android project root. Push it directly:

```bash
# 1. Navigate into the project folder
cd LexiPopup

# 2. Initialize git (first time only)
git init
git add .
git commit -m "Initial LexiPopup release"

# 3. Connect to your GitHub repo (create an empty repo on github.com first)
git remote add origin https://github.com/YOUR_USERNAME/LexiPopup.git

# 4. Push — workflows trigger automatically
git push -u origin main
```

That's it. No secrets, no configuration, no extra setup.

---

## ⚡ Automated CI/CD

### Debug APK — triggers on every `git push`

| Action | Result |
|--------|--------|
| Push any branch | Builds debug APK → creates **GitHub Pre-Release** tagged `debug-latest` |
| Download from | GitHub → Releases → "Debug Build (latest)" |

### Release APK — triggers on version tag push

```bash
# Tag and push to trigger the signed release build
git tag v1.0.0
git push origin v1.0.0
```

| Action | Result |
|--------|--------|
| Push `v*` tag | Builds **signed release APK** → creates **GitHub Release** |
| Signing | Hardcoded in workflow — no secrets setup required |
| Key alias | `my-key` |
| Download from | GitHub → Releases → latest |

> **No secrets to configure.** The keystore is embedded (base64) directly in `.github/workflows/release.yml`.

---

## Features

- **Instant popup** (<150ms) from Moon+ Reader or any app via PROCESS_TEXT
- **Offline-first** — 1000 seed words bundled; full Wiktionary + WordNet + Hindi pack downloadable in-app
- **Hindi meanings** with Devanagari script and transliteration
- **Synonyms, antonyms, etymology, example sentences**
- **IPA pronunciation** with TTS (text-to-speech)
- **Glassmorphism + 3D effects** — Compose `graphicsLayer`, parallax tilt via accelerometer, spring animations
- **Particle burst** animation on favorite toggle
- **Collapsible bubble mode** with pulse animation
- **Save Note dialog** — attach your own notes to any word
- **Resize handle** — drag the popup taller or shorter
- **Persistent notification** — quick search from anywhere without switching apps
- **20+ toggleable UI elements** — disable any element from the Customize tab
- **Flashcard system** with SM-2 spaced repetition algorithm
- **Vocabulary dashboard** — bar charts, pie chart, GitHub-style calendar heatmap
- **Export** vocabulary as CSV, JSON, or Anki TSV deck
- **Export settings** as JSON (share/restore your toggle config)
- **About/License screen** with proper legal attributions
- **Database Pack selection** — Minimal (35MB), Standard (65MB), Full (105MB)
- **Dark mode** + Material You dynamic color
- **Edge-to-edge** display with notch/cutout support

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Clean Architecture |
| DI | Hilt |
| Database | Room (SQLite) + WAL mode, page_size=4096 |
| Networking | Retrofit + OkHttp |
| Settings | DataStore Preferences |
| Background | WorkManager (dictionary downloads) |
| Sensors | Android SensorManager (parallax tilt) |

---

## Project Structure

```
LexiPopup/                         ← Push THIS folder as your GitHub repo root
├── .github/
│   └── workflows/
│       ├── debug.yml              ← Auto debug APK → pre-release (every push)
│       └── release.yml            ← Signed release APK → GitHub release (on v* tag)
├── app/
│   └── src/main/java/com/lexipopup/
│       ├── data/
│       │   ├── download/          DictionaryDownloadWorker (checksum verified)
│       │   ├── local/
│       │   │   ├── dao/           WordDao, VocabularyDao, FlashcardDao,
│       │   │   │                  FavoriteWordDao, UserNoteDao
│       │   │   ├── database/      LexiDatabase (Room), DatabaseSeeder (1000 words)
│       │   │   └── entities/      WordEntity, VocabularyHistoryEntity, FlashcardEntity,
│       │   │                      FavoriteWordEntity, UserNoteEntity, UserSettingsEntity
│       │   └── remote/            DictionaryApi (FreeDictionaryAPI fallback)
│       ├── di/                    AppModule, DataStoreModule
│       ├── domain/
│       │   ├── models/            WordEntry, AppSettings, Flashcard, UserNote, VocabularyHistory
│       │   ├── repositories/      DictionaryRepository, VocabularyRepository (interfaces)
│       │   └── usecases/          LookupWordUseCase (LRU cache), SpacedRepetitionUseCase (SM-2)
│       ├── presentation/
│       │   ├── about/             AboutScreen (legal attributions)
│       │   ├── dashboard/         DashboardScreen (4 tabs), CalendarHeatmap, DashboardViewModel
│       │   ├── flashcards/        FlashcardsScreen (swipe gestures), FlashcardsViewModel
│       │   ├── onboarding/        OnboardingActivity, DatabasePackScreen (Minimal/Standard/Full)
│       │   ├── popup/             PopupActivity, PopupScreen (full 3D UI), PopupViewModel
│       │   └── theme/             Material 3 theme
│       └── utils/
│           ├── ExportHelper       CSV / JSON / Anki TSV export
│           ├── NotificationHelper Persistent notification
│           ├── SensorHelper       Accelerometer → parallax tilt flow
│           ├── SettingsDataStore  29 toggle keys (all with defaults)
│           └── TtsHelper          TTS word/meaning speaking
└── app/src/test/                  SpacedRepetitionTest, LookupWordNormalizationTest,
                                   DatabaseSizeAndWordTest (word existence + performance)
```

---

## Moon+ Reader Setup

1. Install LexiPopup APK
2. Grant **Display over other apps** permission
3. Open Moon+ Reader → **Settings → Reading → Dictionary**
4. Select **LexiPopup** from the list
5. Long-press any word → **Dictionary** → definition popup appears instantly

---

## Word Lookup Flow

```
Selected word from Moon+ Reader (PROCESS_TEXT intent)
  ↓ normalize (lowercase, trim punctuation)
  ↓ LRU memory cache (< 10ms)
  ↓ Room database — seed words (< 30ms)
  ↓ Downloaded dictionary pack (if installed)
  ↓ Online API fallback (FreeDictionaryAPI, 2s timeout)
  ↓ Show popup — skeleton visible in < 50ms
```

---

## Database Sources & Licenses

| Source | License | Words |
|--------|---------|-------|
| Wiktionary | CC BY-SA 3.0 | 4.7M (Full pack) |
| WordNet (Princeton) | WordNet 3.1 (free) | 155,000 |
| Hindi WordNet (CFILT, IIT Bombay) | GNU FDL — non-commercial | 28,000+ |

Hindi WordNet used for non-commercial/research purposes per IIT Bombay license.
Attribution shown in **About → Licenses** screen inside the app.

---

## Building Locally

```bash
# Debug build
./gradlew assembleDebug

# Release build (signed with your own keystore)
./gradlew assembleRelease \
  -Pandroid.injected.signing.store.file=/path/to/keystore.jks \
  -Pandroid.injected.signing.store.password=YOUR_PASS \
  -Pandroid.injected.signing.key.alias=YOUR_ALIAS \
  -Pandroid.injected.signing.key.password=YOUR_KEY_PASS

# Run unit tests
./gradlew test
```

Requirements: JDK 17, Android SDK (API 29+), Gradle 8.6
