---
name: LexiPopup Biology mode completion
description: Architecture decisions and gotchas for the Biology mode feature — DB version, label system, flashcard isolation, backup.
---

## DB version
- Now at **version 7** (v5: dictionary_cache/favorites/vocabulary_history mode columns; v6: user_notes/flashcards mode columns + flashcards unique index change; v7: chat_sessions mode column + index).
- `flashcards` unique index changed from `(word)` to `(word, mode)` via full table recreate in MIGRATION_5_6 — needed so the same word can be both an English and Biology card.
- `user_notes` unique index changed from `(word)` to `(word, mode)` via ALTER TABLE + DROP/CREATE INDEX.

## ModeStrings.kt
- `utils/ModeStrings.kt` is the single source of truth for all biology-aware UI strings.
- Use `ModeStrings.*` functions in all Composables — never hard-code English-only labels in screens.
- Covers: nav labels, TopAppBar titles, search placeholders, section headers, stat card labels, history labels, source labels (including groq_bio / openai_bio / on_device_bio variants), flashcard empty states.

## Mode isolation pattern
- All ViewModels that show mode-specific data (WordHistoryViewModel, DictionaryBrowserViewModel, FlashcardsViewModel) inject `ModeManager` and use `flatMapLatest { mode -> repo.method(mode) }` to react to mode changes automatically.
- WordHistoryScreen resets `filterSource` to "all" via `LaunchedEffect(activeMode)` when mode switches, because the English and Biology source filter chip lists are different.
- Biology sourceFilters: groq_bio, openai_bio, on_device_bio. English: online, groq, openai, on_device, seed.

## Flashcard mode isolation
- `Flashcard` domain model has a `mode: String = "english"` field; `toDomain()` and `toEntity()` both map it — mode is preserved through SRS review cycles.
- `createFlashcard(word, front, back, mode)` now accepts mode; PopupViewModel passes `AppMode.BIOLOGY.id` or `AppMode.ENGLISH.id` based on `entry.isBiology()`.
- `VocabularyRepository.getAllFlashcardsAllModes()` exists for backup — uses the legacy mode-agnostic DAO `getAllCards()`.

**Why:** Without `getAllFlashcardsAllModes()`, calling `getAllFlashcards()` with the new default `mode="english"` would silently omit biology cards from backups.

## Repository pattern
- New mode-aware DAO methods are suffixed with `ByMode` (e.g. `getDueCardsByMode`, `getAllNotesByMode`).
- Legacy non-suffixed methods kept intact for WorkManager workers and backward compat.
