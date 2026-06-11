# LexiPopup - Advanced Android Popup Dictionary

A production-ready Android popup dictionary that integrates with Moon+ Reader and any app via PROCESS_TEXT intent. Instant definitions, Hindi meanings, offline-first, with a beautiful modern 3D UI.

## Features

- **Instant popup** (<150ms) when you long-press any word in Moon+ Reader or any app
- **Offline-first**: 1000 seed words bundled; full Wiktionary + WordNet + Hindi download available
- **Hindi meanings** with Devanagari script and transliteration
- **Synonyms, antonyms, etymology, example sentences**
- **IPA pronunciation** with TTS (text-to-speech)
- **Glassmorphism + 3D effects** using Compose `graphicsLayer`, shadows, spring animations
- **Collapsible bubble mode** with flip animation
- **Persistent notification** for quick search from anywhere
- **Fully toggleable UI** — disable any element from the settings dashboard
- **Flashcard system** with SM-2 spaced repetition algorithm
- **Vocabulary dashboard** with bar charts, pie charts, search history
- **Dark mode** + Material You dynamic color

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Clean Architecture |
| DI | Hilt |
| Database | Room (SQLite) + WAL mode |
| Networking | Retrofit + OkHttp |
| Settings | DataStore Preferences |
| Async | Coroutines + Flow |
| Background | WorkManager |
| Min SDK | Android 10 (API 29) |

## Building via GitHub Actions

Push to GitHub — Actions will automatically build debug + release APKs.

### Required GitHub Secrets (for signed release):
| Secret | Description |
|--------|-------------|
| `KEYSTORE_BASE64` | Base64-encoded keystore file |
| `KEY_ALIAS` | Key alias in the keystore |
| `KEY_PASSWORD` | Key password |
| `STORE_PASSWORD` | Keystore password |

## Moon+ Reader Integration

1. Long-press any word in Moon+ Reader
2. Tap **Dictionary** in the context menu
3. Select **LexiPopup** from the list
4. The popup appears instantly above Moon+ Reader

LexiPopup registers `android.intent.action.PROCESS_TEXT` with `text/plain` MIME type — the standard Android mechanism for dictionary integration.

## Project Structure

```
LexiPopup/
├── app/src/main/java/com/lexipopup/
│   ├── di/                      # Hilt modules
│   ├── domain/
│   │   ├── models/              # WordEntry, Flashcard, AppSettings, etc.
│   │   ├── repositories/        # Repository interfaces
│   │   └── usecases/            # LookupWordUseCase (LRU cache), SpacedRepetitionUseCase (SM-2)
│   ├── data/
│   │   ├── local/
│   │   │   ├── database/        # LexiDatabase, DatabaseSeeder (1000 seed words), Repository impls
│   │   │   ├── dao/             # WordDao, VocabularyDao, FlashcardDao
│   │   │   └── entities/        # Room entities
│   │   └── remote/              # FreeDictionaryAPI (fallback)
│   ├── presentation/
│   │   ├── popup/               # PopupActivity, PopupScreen, PopupViewModel (main feature)
│   │   ├── dashboard/           # MainActivity, DashboardScreen, DashboardViewModel
│   │   ├── flashcards/          # FlashcardsScreen, FlashcardsViewModel
│   │   ├── onboarding/          # OnboardingActivity
│   │   └── theme/               # LexiPopupTheme (light/dark)
│   └── utils/                   # SettingsDataStore, NotificationHelper, TtsHelper, BootReceiver
└── .github/workflows/build.yml  # GitHub Actions CI/CD
```

## Word Lookup Flow

```
User selects word → Intent received
         ↓
Normalize (lowercase, strip punctuation)
         ↓
LRU Memory cache (200 words, <1ms)
         ↓ miss
Room database (indexed, <30ms)
         ↓ miss
FreeDictionaryAPI (online fallback, 2s timeout)
         ↓
Display popup + save to cache
```

## Settings (All Toggleable)

Open the app dashboard → **Customize** tab to toggle:

- Pronunciation, Part of speech, Detailed meaning, Hindi, Example
- Synonyms, Antonyms, Etymology, Difficulty badge, Frequency meter
- All 6 action buttons (Copy, Speak, Translate, Share, Note, Details)
- Popup dragging, resizing, bubble collapse
- Persistent notification
- Search history tracking, auto-flashcard generation

## Database Sources & Licenses

| Source | License | Words |
|--------|---------|-------|
| Wiktionary | CC BY-SA 3.0 | 4.7M+ |
| WordNet (Princeton) | WordNet License (free) | 155k |
| Hindi WordNet (CFILT, IIT Bombay) | GNU FDL (non-commercial) | 28k+ |

Attribution shown in app's About screen as required.

## First-Run Experience

1. Overlay permission request (required for popup)
2. Notification permission
3. 3-screen onboarding carousel
4. Auto-start persistent notification

## Performance Targets

| Metric | Target |
|--------|--------|
| Popup display | <150ms |
| Cache lookup | <10ms |
| DB lookup (indexed) | <30ms |
| Animations | 60fps |
| Memory (idle) | <80MB |
