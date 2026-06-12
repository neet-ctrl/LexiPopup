---
name: LexiPopup Gson TypeToken R8 crash
description: Release builds crash with "TypeToken must be created with a type argument" — root cause and fix.
---

## Rule
Never use `object : TypeToken<List<String>>() {}.type` in release builds.
Use `TypeToken.getParameterized(List::class.java, String::class.java).type` everywhere.

**Why:** R8/ProGuard strips anonymous class generic signatures even when `-keepattributes Signature` is present. The anonymous `TypeToken<T>` subclass trick relies on those signatures at runtime. `getParameterized()` constructs the type reflection dynamically — no generic signatures needed, always R8-safe.

**How to apply:** Any file that does `gson.fromJson<List<String>>(json, someType)` must use the `getParameterized` form. Also: never use `gson.fromJson(json, DataClass::class.java)` when the data class contains `List<SomeObject>` fields — Gson loses the type parameter and creates `LinkedTreeMap` objects instead, crashing on cast (shows as `com.google.gson.internal.n` after R8). Fix: parse as `JsonObject` first, then extract each typed list field separately with `TypeToken.getParameterized()`.

In LexiPopup this affected these files:
- `DictionaryRepositoryImpl.kt` — `toDomain()` extension function (called on every word lookup)
- `AiProvider.kt` — `parseWordEntryFromJson()` (called on every AI response)
- `AiExplanationHelper.kt` — `parseResponse()` (called on OpenAI AI responses)
- `BackupViewModel.kt` — `importBackup()` — `BackupData` has `historyEntries: List<WordEntry>`; was using `gson.fromJson(json, BackupData::class.java)` which erased the WordEntry type → fixed by parsing root as `JsonObject` then using `TypeToken.getParameterized(List::class.java, WordEntry::class.java).type`

Also add to `proguard-rules.pro`:
```
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers class com.google.gson.internal.$Gson$Types { *; }
```
