# LexiPopup — Complete Setup Guide
### Every single step, from zero to 100% working app (Termux edition)

---

## ⏱ Total time: ~1.5 hours
Most of that is waiting for downloads/builds — you're not doing much yourself.

| Phase | What happens | Your time |
|-------|-------------|-----------|
| 1 | Termux setup | 5 min |
| 2 | Clone repo | 1 min |
| 3 | Generate dictionary databases on GitHub | 45 min (auto, you wait) |
| 4 | Update 2 lines of code + push | 5 min |
| 5 | Build APK on GitHub | 15 min (auto, you wait) |
| 6 | Install APK + grant permissions | 5 min |
| 7 | Configure AI + download dictionary | 5 min |

---

## PHASE 1 — Set up Termux

### Step 1.1 — Install Termux
- Open **F-Droid** on your phone (or download Termux APK from f-droid.org)
- Do **NOT** install Termux from Google Play — that version is outdated
- Install Termux from F-Droid

### Step 1.2 — Update Termux and install required packages

Open Termux and paste these commands **one line at a time**:

```bash
pkg update -y
```
```bash
pkg upgrade -y
```
```bash
pkg install git python python-pip -y
```
```bash
pip install nltk tqdm requests
```

### Step 1.3 — Download NLTK data (dictionary source)
This downloads WordNet and CMU pronunciation data (~200 MB). It may take 5–10 minutes.

```bash
python -c "
import nltk
nltk.download('wordnet')
nltk.download('omw-1.4')
nltk.download('cmudict')
nltk.download('words')
print('Done! NLTK data downloaded.')
"
```

You should see: `Done! NLTK data downloaded.`

### Step 1.4 — Set up Git identity (do this once)
Replace with your actual name and email:

```bash
git config --global user.name "Your Name"
git config --global user.email "your@email.com"
```

---

## PHASE 2 — Clone your GitHub repo into Termux

### Step 2.1 — Clone
```bash
cd ~
git clone https://github.com/neet-ctrl/LexiPopup.git
cd LexiPopup
```

### Step 2.2 — Verify the structure is correct
```bash
ls
```
You should see: `app/  gradle/  gradlew  README.md  scripts/  ...`

---

## PHASE 3 — Generate dictionary databases (via GitHub Actions — easiest way)

The dictionary packs are SQLite databases containing 10,000–60,000 words.
They are too big to commit to GitHub. Instead, you generate them once using a GitHub Actions workflow and store them as a GitHub Release.

### Step 3.1 — Push the code to GitHub
First time push:
```bash
cd ~/LexiPopup
git add .
git commit -m "Add dictionary generator and CI workflows"
git push origin main
```

If it asks for your GitHub username/password:
- Username: `neet-ctrl`
- Password: use a **Personal Access Token** (PAT), not your actual password
  - Go to github.com → Settings → Developer settings → Personal access tokens → Tokens (classic)
  - Click "Generate new token (classic)"
  - Check: `repo`, `workflow`
  - Copy the token (starts with `ghp_`)
  - Paste it as your "password" in Termux

### Step 3.2 — Run the dictionary generation workflow
1. Open a browser and go to: **https://github.com/neet-ctrl/LexiPopup/actions**
2. On the left sidebar, click **"Generate Dictionary Packs"**
3. Click the **"Run workflow"** button (top right of the list)
4. Click the green **"Run workflow"** button in the dropdown
5. Wait — the yellow circle means "running". This takes **30–45 minutes**.
6. When it turns green ✅, the dictionary packs are ready.

### Step 3.3 — Find the checksums
After the workflow finishes:
1. Go to **https://github.com/neet-ctrl/LexiPopup/releases**
2. Find the release called **"Dictionary Packs v1"**
3. Download **checksums.txt**
4. Open it — it shows 3 SHA-256 hashes like:
   ```
   MINIMAL  = a3f8d2...
   STANDARD = b1c9e4...
   FULL     = d7a2f1...
   ```
5. Keep this file open — you'll need these in Phase 4.

---

## PHASE 4 — Update the download URLs in the app code

This is the only code change you need to make manually. 2 small edits.

### Step 4.1 — Edit DictionaryDownloadWorker.kt

In Termux:
```bash
cd ~/LexiPopup
nano app/src/main/java/com/lexipopup/data/download/DictionaryDownloadWorker.kt
```

Find the section that looks like this (around line 61):
```kotlin
private val PACK_URLS = mapOf(
    DatabasePack.MINIMAL.name  to "https://github.com/YOUR_ORG/lexipopup-data/releases/latest/download/dict_minimal_v1.db.gz",
    DatabasePack.STANDARD.name to "https://github.com/YOUR_ORG/lexipopup-data/releases/latest/download/dict_standard_v1.db.gz",
    DatabasePack.FULL.name     to "https://github.com/YOUR_ORG/lexipopup-data/releases/latest/download/dict_full_v1.db.gz"
)
```

**Change `YOUR_ORG/lexipopup-data` → `neet-ctrl/LexiPopup`** so it looks like:
```kotlin
private val PACK_URLS = mapOf(
    DatabasePack.MINIMAL.name  to "https://github.com/neet-ctrl/LexiPopup/releases/download/dict-v1/dict_minimal_v1.db.gz",
    DatabasePack.STANDARD.name to "https://github.com/neet-ctrl/LexiPopup/releases/download/dict-v1/dict_standard_v1.db.gz",
    DatabasePack.FULL.name     to "https://github.com/neet-ctrl/LexiPopup/releases/download/dict-v1/dict_full_v1.db.gz"
)
```

### Step 4.2 — Update the checksums

In the same file, find (around line 68):
```kotlin
private val PACK_CHECKSUMS = mapOf(
    DatabasePack.MINIMAL.name  to "PLACEHOLDER_SHA256_MINIMAL",
    DatabasePack.STANDARD.name to "PLACEHOLDER_SHA256_STANDARD",
    DatabasePack.FULL.name     to "PLACEHOLDER_SHA256_FULL"
)
```

Replace the 3 placeholder values with the actual SHA-256 values from `checksums.txt`:
```kotlin
private val PACK_CHECKSUMS = mapOf(
    DatabasePack.MINIMAL.name  to "PUT_MINIMAL_SHA256_HERE",
    DatabasePack.STANDARD.name to "PUT_STANDARD_SHA256_HERE",
    DatabasePack.FULL.name     to "PUT_FULL_SHA256_HERE"
)
```

### Step 4.3 — Save and exit nano
- Press `Ctrl + X`
- Press `Y` to confirm save
- Press `Enter`

### Step 4.4 — Commit and push
```bash
cd ~/LexiPopup
git add app/src/main/java/com/lexipopup/data/download/DictionaryDownloadWorker.kt
git commit -m "Set dictionary pack URLs and checksums"
git push origin main
```

---

## PHASE 5 — Build the APK (automatic via GitHub Actions)

### Step 5.1 — Watch the build
1. Go to **https://github.com/neet-ctrl/LexiPopup/actions**
2. Click the latest **"Debug APK"** run (triggered by your push)
3. Watch it build. It takes **10–15 minutes**.
4. When it turns green ✅, the APK is ready.

### Step 5.2 — Download the APK
1. Go to **https://github.com/neet-ctrl/LexiPopup/releases**
2. Find **"Debug Build (latest)"**
3. Download `LexiPopup-debug-*.apk`

---

## PHASE 6 — Install the APK on your phone

### Step 6.1 — Allow installation from unknown sources
On Android 10+:
1. Go to **Settings → Apps → Special app access → Install unknown apps**
2. Find your browser (or Files app) → Enable "Allow from this source"

### Step 6.2 — Install
1. Open the downloaded APK file
2. Tap **Install**
3. Tap **Open**

### Step 6.3 — Grant Overlay Permission (CRITICAL — popup won't work without this)
The app will ask for this during onboarding:
1. On the onboarding screen, tap **"Grant Overlay Permission"**
2. Android opens "Display over other apps" settings
3. Find **LexiPopup** in the list
4. Tap it → toggle **Allow** to ON
5. Press Back

### Step 6.4 — Grant Notification Permission (Android 13+)
When prompted, tap **Allow** to let LexiPopup show its persistent notification.

---

## PHASE 7 — Download the dictionary pack inside the app

### Step 7.1 — From the app
1. Open LexiPopup
2. Go to **Settings tab** (⚙ gear icon at bottom)
3. Scroll to **"📖 Dictionary Data"**
4. Tap **"Manage Dictionary Packs"**
5. Choose a pack:
   - **Minimal** (~15 MB) — 10,000 most common words
   - **Standard** (~80 MB) — ~60,000 words, recommended
   - **Full** (~120 MB) — ~60,000 words + Hindi meanings
6. Tap **Download**
7. Watch the progress bar (downloading → decompressing → verifying → importing)
8. When it says **"✅ Done — X words imported"**, you're set

---

## PHASE 8 — Set up Free AI (Groq) — takes 3 minutes

LexiPopup now includes a free AI that explains any rare/technical word not in the dictionary.

### Step 8.1 — Get your free Groq API key
1. Open a browser → go to **https://console.groq.com**
2. Click **Sign Up** (no credit card required)
3. After login → click **API Keys** in the left sidebar
4. Click **"Create API Key"** → give it a name like "LexiPopup"
5. **Copy the key** (it starts with `gsk_`)

### Step 8.2 — Enter the key in LexiPopup
1. In LexiPopup → go to **Settings tab**
2. Tap the **"Configure"** button next to **"🤖 AI Assistant"**
3. Make sure **"Groq Cloud"** is selected as the provider
4. Paste your `gsk_...` key in the **"Groq API Key"** field
5. You should see **"✅ API key set — Groq is ready"**

**That's it.** Groq gives you 1,000 free lookups per day. More than enough.

---

## PHASE 9 — Set up Moon+ Reader

### Step 9.1 — Install Moon+ Reader
- Download from Google Play: search "Moon+ Reader"
- Free version works fine

### Step 9.2 — Connect LexiPopup
1. Open Moon+ Reader
2. Go to **Settings (⚙) → Read → Dictionary app**
3. Select **LexiPopup** from the list

### Step 9.3 — Test it
1. Open any ebook in Moon+ Reader
2. Long-press any word
3. In the text toolbar, tap **LexiPopup** (or the **⋯ More** button → LexiPopup)
4. The floating popup should appear with the definition 🎉

---

## PHASE 10 — Optional: On-Device AI (works offline, no API key)

If you want AI explanations even without internet:

### Step 10.1 — In LexiPopup → Settings → AI Assistant → Configure
1. Select **"On-Device AI"** as the provider
2. Choose a model:
   - **Gemma 2B Tiny** (recommended for phones) — 1.5 GB download, needs 4 GB RAM
   - **Phi-2** — 2.7 GB download, needs 6 GB RAM
3. Tap **"Download Model (X GB)"**
4. Wait for the download (15–30 minutes on WiFi)
5. When it says **"✅ Model ready — works offline"**, it's done

---

## All done! ✅ Quick checklist

```
[✓] Termux installed + packages ready
[✓] Repo cloned
[✓] Dictionary packs generated (GitHub Actions)
[✓] APK built and installed
[✓] Overlay permission granted
[✓] Dictionary pack downloaded in-app (Standard recommended)
[✓] Groq AI key configured (free)
[✓] Moon+ Reader connected
```

---

## Troubleshooting

### "The popup doesn't appear"
→ Check overlay permission: Settings → Apps → LexiPopup → "Display over other apps" → ON

### "Word not found"
→ You need at least one of:
1. Internet (online fallback via FreeDictionaryAPI — no key needed)
2. A dictionary pack downloaded (Settings → Manage Dictionary Packs)
3. A Groq API key configured (Settings → AI Assistant)

### "Dictionary download fails / stuck"
→ Check your internet connection, then try again. The download resumes where it left off.
→ If it says "Checksum mismatch", the files on GitHub may have changed. Re-run the "Generate Dictionary Packs" workflow and update the checksums again.

### "Push to GitHub asks for password"
→ GitHub no longer accepts passwords for git push. Use a Personal Access Token:
  - github.com → Settings → Developer settings → Personal access tokens → Generate new token
  - Check `repo` and `workflow` scopes
  - Use the token (starts with `ghp_`) as your password

### "Termux: command not found: python"
```bash
pkg install python -y
```

### "pip install fails"
```bash
pkg install python-pip -y
pip install --upgrade pip
pip install nltk tqdm requests
```

### "NLTK download fails in Termux"
Add storage permission to Termux first:
```bash
termux-setup-storage
```
Then retry the NLTK download.

### "The APK build fails on GitHub Actions"
→ Open the failed run → click the build step → read the error
→ Common fix: make sure `gradlew` has execute permission (the workflow does this automatically)

---

## Nano editor quick reference (for editing files in Termux)

```
Open file:    nano filename
Move around:  arrow keys
Edit:         just type
Save:         Ctrl + X → Y → Enter
Cancel edit:  Ctrl + X → N
Search:       Ctrl + W → type → Enter
Copy line:    Ctrl + K (cuts it)
Paste line:   Ctrl + U
```

---

## Dictionary pack schema (for reference)

If you ever want to create your own dictionary pack, it must be a **gzipped SQLite database** with this table:

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

To create the .db.gz file from your SQLite .db file:
```bash
gzip -6 -c your_dictionary.db > dict_minimal_v1.db.gz
sha256sum dict_minimal_v1.db.gz   # get the checksum
```

---

*LexiPopup — Built for Android readers who love words.*
