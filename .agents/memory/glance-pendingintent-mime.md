---
name: Glance widget PendingIntent MIME strip
description: Glance wraps actionStartActivity intents in PendingIntents; Android can strip the MIME type, breaking ACTION_SEND routing. Use a custom String extra instead.
---

## The rule
When a Glance widget uses `actionStartActivity(intent)` where the intent has `action = Intent.ACTION_SEND` and `type = "text/plain"`, Android's PendingIntent wrapping can strip the MIME type. In `PopupActivity.processIntent`, the guard `intent.type == "text/plain"` then fails, and the activity falls through to manual-search mode instead of looking up the tapped word.

**Why:** PendingIntents created by the Glance framework pass through the system's PendingIntent machinery, which may normalise or drop the MIME type for component-targeted intents.

**How to apply:**
- Widget word-tap intents: use `putExtra("lookup_word", word)` — no action or MIME type needed. `processIntent` checks for this extra first, before any MIME-dependent path.
- Widget header search intents: use `putExtra("mode", "manual_search")` — same pattern, already the existing convention.
- Do NOT use `action = Intent.ACTION_SEND` / `type = "text/plain"` for Glance-launched intents targeting a specific component.
