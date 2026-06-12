---
name: Mode intermingling fixes
description: Root causes and fixes for English/Biology data bleeding across modes in LexiPopup
---

## Rule
Every DAO call that reads or writes dictionary data must pass an explicit mode string. The DAO defaults all optional `mode` parameters to `"english"`, so any call that omits mode silently reads/writes English data even when the app is in Biology mode.

**Why:** Room DAO default parameter values (`mode: String = "english"`) are invisible at call sites, making it easy to introduce cross-mode contamination. AiChatViewModel, WordDetailViewModel, and SeedWordListViewModel all had this bug.

## Fixed call sites
- `AiChatViewModel.extractVocabulary()` — `findWord` + `updateAccess` now use `modeManager.currentMode.value.id`
- `AiChatViewModel.lookupWord()` — `findWord` now passes current mode id
- `AiChatViewModel.saveWordToHistory()` — `findWord` + `updateAccess` now pass current mode id
- `WordDetailViewModel.load()` — `lookupWord(word, mode)` now passes `modeManager.currentMode.value`
- `WordDetailViewModel.toggleFavorite()` / `saveNote()` — repo calls now pass current mode
- `SeedWordListViewModel` — injected `ModeManager`; all repo/dao calls pass current mode

## Seed word queries were hardcoded
`WordDao.getSeedWords` and `WordDao.getSeedWordCount` had `mode = 'english'` hardcoded in SQL.
Fixed by: adding `:mode` parameter to both queries, updating Repository interface + impl.

## How to apply
Whenever adding a new ViewModel that touches `wordDao` or `repo`, always inject `ModeManager` and pass `modeManager.currentMode.value` (or `.value.id`) to every read/write call.
