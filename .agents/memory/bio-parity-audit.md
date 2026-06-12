---
name: Biology mode feature parity audit
description: Gaps between English and Biology modes found in the 2026-06 audit, and how they were fixed.
---

# Biology Mode Feature Parity — Fixed Gaps

**Why:** Biology mode was added after English mode. Several features that were wired only for English were never backfilled for Biology.

## Gaps fixed

1. **Auto-flashcard creation** (`PopupViewModel.kt`) — `autoGenerateFlashcards` guard had `&& effectiveMode == AppMode.ENGLISH`; removed the mode check so biology lookups also auto-create flashcards.

2. **difficultyDistribution reactive** (`DashboardViewModel.kt`) — used a one-shot `flow { emit(...) }` that captured the mode at creation time; replaced with `modeManager.currentMode.flatMapLatest { mode -> flow { emit(...) } }` so it re-queries when the mode switches.

3. **SourceLayerBadge** (`PopupScreen.kt`) — `when` expression lacked `groq_bio`, `openai_bio`, `on_device_bio` cases; they fell to the generic else. Added explicit branches matching the labels in `ModeStrings.sourceLabel()`.

4. **Empty state strings** (`DashboardScreen.kt`) — hardcoded "Your dictionary is empty" / "Browse Dictionary"; replaced with `ModeStrings.emptyDictMsg(activeMode)` / `ModeStrings.emptyDictAction(activeMode)`.

5. **WotD/TotD card label** (`DashboardScreen.kt`) — `WotDHomeCard` hardcoded "WORD OF THE DAY"; added `activeMode: AppMode` param and replaced with `ModeStrings.wotdCardTitle(activeMode)` which returns "TERM OF THE DAY" for Biology.

6. **Export biology fields** (`ExportHelper.kt`) — CSV/JSON/Anki export only included English fields. Updated all three formats to include `scientificClassification`, `functions`, `structure`, `diseases`, `relatedTerms` from `entry.biologyData()` when `entry.isBiology()` is true. Mixed-mode exports get the full column set.

## How to apply

When adding any new English-mode feature: check `PopupViewModel`, `DashboardViewModel`, `DashboardScreen` (HomeContent + WotDHomeCard), `PopupScreen` (SourceLayerBadge), and `ExportHelper` — these are the recurring locations where the biology path was missed.

Always use `ModeStrings.*` for any user-visible label; never hardcode "word", "dictionary", "WORD OF THE DAY" etc.
