# LexiPopup — Complete Setup Guide
### Everything is automated — no Termux, no manual code edits required

---

## ⏱ Total time: ~1 hour
Almost all of it is waiting for GitHub Actions to run — you're barely doing anything yourself.

| Step | What happens | Your time |
|------|-------------|-----------|
| 1 | Push code to GitHub | 2 min |
| 2 | Run "Generate Dictionary Packs" workflow | 30–45 min (auto, you wait) |
| 3 | Run "Debug APK" or "Release APK" workflow | 10–15 min (auto, you wait) |
| 4 | Install APK + grant permissions | 5 min |
| 5 | Configure AI + download dictionary pack | 5 min |
| 6 | Connect Moon+ Reader | 2 min |

---

## STEP 1 — Push code to GitHub

From wherever you have the code (Replit, your PC, etc.):

```bash
git add .
git commit -m "Initial LexiPopup setup"
git push origin main
```

If GitHub asks for credentials:
- Username: your GitHub username (e.g. `neet-ctrl`)
- Password: use a **Personal Access Token** (PAT), not your password
  - github.com → Settings → Developer settings → Personal access tokens → Tokens (classic)
  - Click "Generate new token" → check `repo` and `workflow` → copy the `ghp_...` token

---

## STEP 2 — Generate dictionary packs (run once)

The dictionary databases are too large to commit to GitHub. Instead, they are generated automatically by a GitHub Actions workflow and stored as a GitHub Release.

### 2.1 — Run the workflow

1. Open a browser → go to **https://github.com/neet-ctrl/LexiPopup/actions**
2. In the left sidebar, click **"Generate Dictionary Packs"**
3. Click the **"Run workflow"** button (top right) → click the green **"Run workflow"**
4. Wait. The yellow circle means running. This takes **30–45 minutes**.
5. When it turns green ✅, everything is done.

### What this workflow does automatically
- Downloads Wiktionary English data from kaikki.org (~700K+ entries)
- Downloads WordNet 3.1 + Hindi WordNet from NLTK
- Generates 3 dictionary databases (Minimal / Standard / Full)
- Publishes them as a GitHub Release tagged `dict-v1`
- **Auto-patches the SHA-256 checksums into the app source code**
- **Commits the patched file back** (`[skip ci]` so it doesn't loop)

> ✅ **No manual code editing needed.** Checksums are handled automatically.

---

## STEP 3 — Build the APK

### Option A: Debug APK (for testing)

1. Go to **https://github.com/neet-ctrl/LexiPopup/actions**
2. Click **"Debug APK → Pre-Release"** in the sidebar
3. Click **"Run workflow"** → **"Run workflow"**
4. Wait ~10–15 minutes
5. When green ✅ → go to **https://github.com/neet-ctrl/LexiPopup/releases**
6. Find **"Debug Build (latest)"** → download `LexiPopup-debug-*.apk`

### Option B: Signed Release APK (for distribution)

1. Go to **https://github.com/neet-ctrl/LexiPopup/actions**
2. Click **"Release APK → GitHub Release"** in the sidebar
3. Click **"Run workflow"** → type a version tag like `v1.0.0` → **"Run workflow"**
4. Wait ~10–15 minutes
5. When green ✅ → go to **https://github.com/neet-ctrl/LexiPopup/releases**
6. Find the release **"🚀 LexiPopup v1.0.0"** → download `LexiPopup-1.0.0-signed.apk`

> The keystore is embedded in the workflow — no secrets setup needed.

---

## STEP 4 — Install the APK on your phone

### 4.1 — Allow installation from unknown sources
On Android 10+:
1. Go to **Settings → Apps → Special app access → Install unknown apps**
2. Find your browser (or Files app) → Enable "Allow from this source"

### 4.2 — Install
1. Transfer the APK to your phone (download directly, or use a cable)
2. Tap the APK file
3. Tap **Install** → **Open**

### 4.3 — Grant Overlay Permission (CRITICAL — popup won't work without this)
The app asks for this during onboarding:
1. On the onboarding screen, tap **"Grant Overlay Permission"**
2. Find **LexiPopup** in the list
3. Toggle **Allow** → ON
4. Press Back

### 4.4 — Grant Notification Permission (Android 13+)
When prompted, tap **Allow**.

---

## STEP 5 — Download the dictionary pack inside the app

### From the app
1. Open **LexiPopup**
2. Go to **Settings tab** (⚙ gear icon)
3. Scroll to **"📖 Dictionary Data"**
4. Tap **"Manage Dictionary Packs"**
5. Choose a pack:

| Pack | Words | Size | Use case |
|------|-------|------|---------|
| **Minimal** | ~10,000 | ~15 MB | Everyday common words |
| **Standard** | ~155,000 | ~80 MB | Full WordNet — **recommended** |
| **Full** | ~700,000+ | ~200 MB | Maximum coverage incl. rare/technical |

6. Tap **Download**
7. Watch the progress bar: downloading → decompressing → verifying → importing
8. When it says **"✅ Done — X words imported"**, you're set

---

## STEP 6 — Set up Free AI (Groq) — 3 minutes

LexiPopup uses AI only as a last resort — dictionary lookup happens first.

### 6.1 — Get your free Groq API key
1. Open a browser → **https://console.groq.com**
2. Click **Sign Up** (no credit card required)
3. After login → click **API Keys** → **"Create API Key"** → name it "LexiPopup"
4. **Copy the key** (starts with `gsk_`)

### 6.2 — Enter the key in LexiPopup
1. In LexiPopup → **Settings tab**
2. Tap **"Configure"** next to **"🤖 AI Assistant"**
3. Make sure **"Groq Cloud"** is selected
4. Paste your `gsk_...` key in the **"Groq API Key"** field
5. You should see **"✅ API key set — Groq is ready"**

**Groq gives you 1,000 free lookups per day** — more than enough.

---

## STEP 7 — Set up Moon+ Reader

### 7.1 — Install Moon+ Reader
- Download from Google Play: search "Moon+ Reader"
- Free version works fine

### 7.2 — Connect LexiPopup
1. Open Moon+ Reader
2. Go to **Settings (⚙) → Read → Dictionary app**
3. Select **LexiPopup** from the list

### 7.3 — Test it
1. Open any ebook in Moon+ Reader
2. Long-press any word
3. In the text toolbar, tap **LexiPopup**
4. The floating popup appears instantly 🎉

---

## STEP 8 — Optional: On-Device AI (works offline, no API key)

1. LexiPopup → **Settings → AI Assistant → Configure**
2. Select **"On-Device AI"**
3. Choose a model:
   - **Gemma 2B Tiny** — 1.5 GB download, needs ~4 GB RAM (recommended for phones)
   - **Phi-2** — 2.7 GB download, needs ~6 GB RAM
4. Tap **"Download Model"**
5. Wait 15–30 minutes on WiFi
6. When it says **"✅ Model ready — works offline"**, it's done

---

## All done! ✅ Quick checklist

```
[✓] Code pushed to GitHub
[✓] Dictionary packs generated (GitHub Actions — auto-patches checksums)
[✓] APK built (debug or release)
[✓] APK installed
[✓] Overlay permission granted
[✓] Dictionary pack downloaded in-app (Standard recommended)
[✓] Groq AI key configured (free, optional)
[✓] Moon+ Reader connected
```

---

## Troubleshooting

### "The popup doesn't appear"
→ Settings → Apps → LexiPopup → "Display over other apps" → ON

### "Word not found"
→ You need at least one of:
1. Internet (online fallback via FreeDictionaryAPI — no key needed)
2. A dictionary pack downloaded (Settings → Manage Dictionary Packs)
3. A Groq API key configured (Settings → AI Assistant)

### "Dictionary download fails / stuck"
→ Check your internet connection, then retry. The download resumes where it left off.

### "Checksum mismatch error"
→ Re-run the "Generate Dictionary Packs" workflow. Checksums are auto-patched — no manual editing needed.

### "The APK build fails on GitHub Actions"
→ Open the failed run → click the build step → read the error.
→ Common fix: the "Generate Dictionary Packs" workflow must run first so the dict-v1 release exists.

### "Push to GitHub asks for password"
→ GitHub no longer accepts passwords. Use a Personal Access Token:
- github.com → Settings → Developer settings → Personal access tokens → Generate new token
- Check `repo` and `workflow` scopes → use the `ghp_...` token as your password

---

## Dictionary pack schema (for reference)

If you want to create a custom dictionary pack, it must be a **gzipped SQLite database** with this table:

```sql
CREATE TABLE dictionary_cache (
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    word               TEXT    UNIQUE NOT NULL,
    pronunciation      TEXT    DEFAULT '',   -- IPA  e.g.  /prəˌkræs/
    part_of_speech     TEXT    DEFAULT '',   -- noun / verb / adjective / adverb
    short_meaning      TEXT    DEFAULT '',   -- max 120 chars
    detailed_meaning   TEXT    DEFAULT '',   -- full definition
    hindi_meaning      TEXT    DEFAULT '',   -- Hindi + transliteration
    hindi_pronunciation TEXT   DEFAULT '',
    example_sentence   TEXT    DEFAULT '',
    synonyms           TEXT    DEFAULT '[]', -- JSON array of strings
    antonyms           TEXT    DEFAULT '[]', -- JSON array of strings
    etymology          TEXT    DEFAULT '',
    difficulty_level   INTEGER DEFAULT 2,    -- 1=beginner 2=intermediate 3=advanced 4=expert
    frequency_rating   INTEGER DEFAULT 50,   -- 1–100, higher = more common
    source             TEXT    DEFAULT ''    -- 'minimal' / 'standard' / 'full'
);
```

To create the .db.gz:
```bash
gzip -6 -c your_dictionary.db > dict_minimal_v1.db.gz
sha256sum dict_minimal_v1.db.gz
```

---

*LexiPopup — Built for Android readers who love words.*
