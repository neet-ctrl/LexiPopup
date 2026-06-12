# LexiPopup

Android popup dictionary app — instant word definitions as a floating overlay when text is selected in Moon+ Reader or any app via PROCESS_TEXT intent.

## Run & Operate

- Build APK: push to GitHub, run "Debug APK" or "Release APK" workflow in GitHub Actions
- Generate dict packs: run "Generate Dictionary Packs" workflow in GitHub Actions
- Local Android build: `cd LexiPopup && ./gradlew assembleDebug`
- `pnpm run typecheck` — typecheck TypeScript workspace packages

## Where things live

- `LexiPopup/` — Android project root (Gradle)
- `LexiPopup/app/src/main/java/com/lexipopup/` — all Kotlin source
- `LexiPopup/scripts/generate_dicts.py` — dictionary pack generator (WordNet + Wiktionary)
- `.github/workflows/` — CI/CD workflows (root only — GitHub only reads here)
- `lib/api-spec/openapi.yaml` — OpenAPI spec for any backend routes

## Stack

- **Android**: Kotlin, Jetpack Compose, Material 3, Hilt, Room, WorkManager, DataStore, Retrofit
- **AI**: Groq Cloud (llama-3.3-70b, free) + MediaPipe On-Device (Gemma 2B / Phi-2)
- **Dict data**: WordNet 3.1 + Wiktionary (kaikki.org JSONL) + CMU Pronouncing + Hindi OMW
- **Build**: Gradle 8.6, JDK 17, Android SDK API 29+
- **CI/CD**: GitHub Actions (3 manual workflows)
- **TypeScript workspace**: pnpm, Node.js 24, Express 5, Drizzle ORM

## Architecture decisions

- 5-layer word lookup: LRU cache → Room DB → FreeDictionaryAPI → Groq AI → On-Device AI
- Dictionary packs are gzipped SQLite files downloaded at runtime (not bundled)
- SHA-256 checksums auto-patched into `DictionaryDownloadWorker.kt` by the generate-dicts workflow
- Workflows live only at repo root `.github/workflows/` — `LexiPopup/.github/` was deleted (was dead code)
- Wiktionary download is optional: Minimal + Standard packs always build; Full pack skipped if kaikki.org is unavailable

## Gotchas

- **kaikki.org URL**: changed from `.json` → `.jsonl` in 2025. Current URL: `kaikki.org/dictionary/English/kaikki.org-dictionary-English.jsonl`
- Checksums in `DictionaryDownloadWorker.kt` default to `"SKIP"` (dev mode). Run Generate Dictionary Packs workflow to get real checksums.
- Release APK uses a hardcoded keystore embedded in `release.yml` — no GitHub secrets needed
- GitHub Actions only reads `.github/workflows/` at the repo root, never inside subdirectories

## User preferences

_Populate as you build — explicit user instructions worth remembering across sessions._

## Product

LexiPopup provides:
- Instant popup definitions (<150ms) from Moon+ Reader or any app
- Offline-first: 1,000 seed words built-in; Minimal/Standard/Full packs downloadable
- Hindi meanings (Devanagari + transliteration)
- Synonyms, antonyms, etymology, IPA pronunciation, TTS
- Glassmorphism 3D UI with parallax tilt and spring animations
- SM-2 flashcard system + vocabulary dashboard with heatmap
- CSV / JSON / Anki TSV export
