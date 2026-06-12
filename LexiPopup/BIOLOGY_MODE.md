# Biology Mode — LexiPopup

## Overview

LexiPopup supports two distinct lookup modes side-by-side:

| Feature | English Mode | Biology Mode |
|---------|-------------|--------------|
| Emoji | 📚 | 🧬 |
| AI prompt | Dictionary-style | Biology-expert style |
| Data shown | Definition, synonyms, antonyms, etymology | Category, functions, structure, classification, diseases, related terms |
| AI sources | groq, openai, on_device | groq_bio, openai_bio, on_device_bio |
| DB key | word + mode = "english" | word + mode = "biology" |
| History | Separate | Separate |
| Favorites | Separate | Separate |

## Architecture

### AppMode (domain/models/AppMode.kt)
Sealed class with `ENGLISH` and `BIOLOGY` values.  
Each mode has: `id` (string), `displayName`, `emoji`.  
`AppMode.fromId(id)` is the safe factory — falls back to `ENGLISH`.

### ModeManager (utils/ModeManager.kt)
Singleton `@Inject`-able class that holds the current `AppMode` as a `StateFlow`.  
Survives ViewModel recreation. Widgets and PopupActivity read from it.

### Database (DB version 5)
All three main tables are mode-partitioned:

- **`dictionary_cache`** — unique index on `(word, mode)`. Added `bio_ext_data TEXT` column for extended biology JSON.
- **`favorite_words`** — composite PK `(word, mode)`.
- **`vocabulary_history`** — added `mode TEXT NOT NULL DEFAULT 'english'` column.

Migration `4 → 5` recreates `dictionary_cache` and `favorite_words` (SQLite can't add to composite indices) and ALTER TABLEs `vocabulary_history`.

### BiologyData (domain/models/BiologyData.kt)
Kotlin data class for biology extended fields:
- `scientificClassification: Map<String, String>` — Domain, Kingdom, Phylum, Class, Order, Family, Genus, Species
- `functions: List<String>`
- `structure: List<String>`
- `diseases: List<String>`
- `relatedTerms: List<String>`
- `difficultyLabel: String` — "Basic" / "Intermediate" / "Advanced"
- `difficultyPercent: Int` — 0–100
- `frequencyPercent: Int` — 0–100

Serialised to/from JSON via `BiologyData.toJson()` / `BiologyData.fromJson()`.  
Stored in `dictionary_cache.bio_ext_data`.  
Read from `WordEntry.bioExtData`; parsed via `WordEntry.biologyData()`.

### AI Providers (AI biology prompt)

All three AI providers have a parallel biology method:

| Provider | English method | Biology method | Biology source tag |
|----------|---------------|---------------|-------------------|
| GroqAiProvider | `explainWord()` | `explainBiologyTerm()` | `groq_bio` |
| AiExplanationHelper (OpenAI) | `explainWord()` | `explainBiologyTerm()` | `openai_bio` |
| OnDeviceAiProvider | `explainWord()` | `explainBiologyTerm()` | `on_device_bio` |

The biology prompt requests a rich JSON schema including classification, functions, structure, diseases, related terms, difficulty and frequency. `parseBiologyEntryFromJson()` in `AiProvider.kt` converts this JSON to a `WordEntry` with `mode = "biology"` and `bioExtData` populated.

**API keys are SHARED between modes** — only the prompt changes.

### LookupWordUseCase
`invoke(word, mode)` now takes an `AppMode` parameter.  
- `LAYER_ONLINE_API` is skipped for Biology (free dictionary APIs don't know biology)  
- `LAYER_RULE_BASED` is skipped for Biology  
- `LAYER_GROQ_AI`, `LAYER_OPENAI`, `LAYER_ON_DEVICE` dispatch to biology methods when `mode == BIOLOGY`  
- Separate `LruCache<String, WordEntry>` per mode (`englishCache`, `biologyCache`)

## UI Components

### Dashboard — Pill Mode Switcher
Located at the **top-centre of the Home tab**, above the search bar.  
Shows `📚 English` and `🧬 Biology` pills.  
Active mode has a filled primary background; inactive is transparent.  
Switching mode re-fetches recent words and favorites in the new mode.

### Popup — Mode Switcher (header)
`PopupModeSwitcher` composable added to the popup header row (right of the word title).  
Only visible when both modes are enabled in settings.  
Tapping a mode pill re-looks up the current word in that mode.

### Popup — Mode Selection Sheet (Moon+ Reader)
When a PROCESS_TEXT / SEND intent arrives and **both** modes are enabled,  
`ModeSelectionSheet` appears as a bottom sheet with two large cards (📚 English | 🧬 Biology).  
User picks a mode → `PopupViewModel.confirmModeSelection(mode)` → lookup proceeds.  
If only one mode is enabled, the sheet is skipped automatically.

### Popup — Biology Word Card
`BiologyWordCard` composable (in `BiologyWordCard.kt`) shown instead of the regular English card when `entry.isBiology()`.  
Sections (all individually toggle-able in Biology Settings):
- Category badge + pronunciation
- Definition
- Hindi name
- Example context
- Scientific classification (expandable)
- Functions
- Structure (chips)
- Related terms (chips)
- Associated diseases (chips)
- Etymology
- Difficulty badge + frequency

### Biology Settings Screen
`BioSettingsScreen` — accessible via **Settings → Biology Settings**.  
Controls all `bioShow*` toggles + TOTD notification time.  
Has a "Reset Biology Defaults" button.

## Settings

All biology-specific settings are stored in `AppSettings` with `bio_` prefix:
- `biologyModeEnabled` — master toggle to disable biology mode entirely
- `englishModeEnabled` — master toggle to disable English mode
- `bioShowPronunciation`, `bioShowCategory`, `bioShowDefinition`, `bioShowHindi`, `bioShowExample`, `bioShowClassification`, `bioShowFunctions`, `bioShowStructure`, `bioShowRelatedTerms`, `bioShowDiseases`, `bioShowEtymology`, `bioShowDifficulty`, `bioShowFrequency`
- `bioShowCopyButton`, `bioShowSpeakButton`, `bioShowShareButton`, `bioShowFavoriteButton`, `bioShowSearchWebButton`
- `totdNotificationEnabled`, `totdNotificationHour`

## Widgets

### WOTD Widget — Biology Variant
The widget cycles between English Word of Day and Biology Term of Day based on the current mode stored in `ModeManager`. Both variants use the same visual layout but different colors (purple for English, green for Biology).

### Favorites Widget — Mode Toggle
Header includes a mode toggle icon button (🔤 / 🧬) that switches between English and Biology favorites.

### Recent Words Widget — Mode Toggle
Header includes a mode toggle icon button that switches between English and Biology recent words.

## Disabling a Mode

- **Settings → General → Enable English Mode** (toggle off to disable English dictionary)
- **Settings → General → Enable Biology Mode** (toggle off to disable biology)

If both are disabled by mistake, the app shows an error message on lookup prompting the user to re-enable at least one mode.

If only one mode is enabled, the mode selection sheet is skipped and that mode is used automatically.
