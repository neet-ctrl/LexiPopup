---
name: LexiPopup state-based navigation
description: Navigation pattern used in DashboardScreen — sealed class, no Jetpack Navigation component.
---

# LexiPopup Navigation Pattern

## Key decision
Uses `var destination by remember { mutableStateOf<AppDestination>(AppDestination.Home) }` in DashboardScreen — NOT Jetpack Navigation component.

**Why:** Popup overlay app; all screens share ViewModels and single Activity. Adding nav-compose would require NavHost and route strings without benefit.

## Adding a new full-screen destination
1. Add `object MyScreen : AppDestination()` to sealed class.
2. Add back-nav mapping in `onBack` when-expression.
3. Add `AppDestination.MyScreen -> { MyComposable(onBack = { ... }); return }` in the overlay when-block before the Scaffold.
4. Wire a button: `onClick = { destination = AppDestination.MyScreen }`.

## Current destinations
Home, Dictionary, Flashcards, Stats, Settings, WordDetail(word), About, DownloadPacks.

**How to apply:** All new full-screen views follow this pattern. Do not introduce NavHost without discussing with user first.
