---
name: Glance widget color API
description: How to implement adaptive day/night colors in Glance widgets without DayNightColorProvider
---

In Glance's stable public API (tested through 1.1.0), `ColorProvider` only has two overloads:
- `ColorProvider(color: Color)`
- `ColorProvider(@ColorRes resId: Int)`

There is NO `ColorProvider(day, night)` or `DayNightColorProvider` in the stable API. Any code using those will fail with "Unresolved reference".

**Correct pattern for adaptive colors:**

```kotlin
@Composable
private fun isNight(): Boolean {
    val config = LocalContext.current.resources.configuration
    return (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
}

@Composable
private fun adaptiveColor(day: Color, night: Color): ColorProvider =
    ColorProvider(if (isNight()) night else day)
```

Call `adaptiveColor(day, night)` inside any Glance `@Composable` function to get a properly resolved `ColorProvider`. Store raw `Color` pairs as `private val` constants; resolve them to `ColorProvider` at composition time.

**Other Glance stable API notes:**
- `ActionCallback` is in `androidx.glance.appwidget.action` (NOT `androidx.glance.action`)
- `GlanceModifier.defaultWeight()` does NOT need an explicit import; calling it as a method works without `import androidx.glance.layout.defaultWeight`
- Empty `Box` used as a divider/accent bar must include a content lambda: `Box(modifier = ...) {}`
