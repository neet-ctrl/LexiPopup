---
name: LexiPopup download system
description: Architecture decisions for the dictionary download pipeline (WorkManager + DataStore + SQLite import).
---

# LexiPopup Download System

## Key decisions

**Two separate DataStores:**
- `lexi_settings` (in DataStoreModule, injected as `DataStore<Preferences>`) — app UI settings.
- `lexi_download_state` (in DownloadStateStore, via private `Context.downloadDataStore` extension) — per-pack install/resume state.
- Do NOT merge them; the extension properties must stay `private` to avoid Kotlin property clash.

**Why:** DataStore extension properties are per-file-private. Two files can declare different named extensions on `Context` without conflict as long as each is `private val`.

**WorkManager provided via Hilt:**
`AppModule.provideWorkManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)`
- Must be @Singleton to avoid multiple instances.
- DownloadViewModel injects `WorkManager` (not Context) for testability.

**Unique work names:** `"dict_download_${pack.name}"` — used for `enqueueUniqueWork` + `getWorkInfosForUniqueWorkFlow`.

**Resume support:**
- Temp file: `filesDir/dict_{PACKNAME}.db.gz`
- On start: check file size, send `Range: bytes=<size>-` header.
- 206 Partial Content → append mode; 200 OK → delete and restart.
- Downloaded bytes persisted to DataStore every buffer flush.

**Import pipeline:** Download (.db.gz) → GZip decompress (.db) → verify SHA-256 on .gz → SQLiteDatabase.openDatabase → cursor → batch insert 500 via WordDao.insertAll → markInstalled.

**How to apply:** When adding new pack types, add URL + checksum to DictionaryDownloadWorker companion maps, add enum entry to DatabasePack.
