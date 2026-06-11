# LexiPopup — Complete User Guide
### From Installation to Looking Up Any Word

---

## ⚠️ Honest Dictionary Coverage (Read This First)

| Tier | Words Available | Requires |
|------|----------------|----------|
| **Built-in seed data** | **129 hand-curated words** (high quality, fully offline) | Nothing — works immediately |
| **Online fallback** | ~200,000+ common English words | Internet connection |
| **AI fallback** | Any word, rare/technical/regional included | Your OpenAI API key (Settings) |
| **Downloaded packs** | 100,000+ offline (Wiktionary + WordNet + Hindi) | Internet once to download, then fully offline |

> **Short answer:** On first install you get 129 seed words offline. For everything else, internet or a downloaded pack is needed. The 129 seed words include the highest-frequency academic and literary vocabulary — see the full list below.

### ✅ Confirmed working words (in seed data)
`belittle` · `procrastination` · `serendipity` · `ephemeral` · `eloquent` · `resilient` · `ambiguous` · `pragmatic` · `meticulous` · `ubiquitous` · `tenacious` · `candid` · `clandestine` · `anomaly` · `altruistic` · `capricious` · `diligent` · `eloquence` · `enigmatic` · `equanimity` · `benevolent` · `audacious` · `rhetoric` · `stoic` · `sagacious` · `verbose` · `succinct` · `plethora` · `perennial` · `profound` + 99 more

---

## Part 1 — Installation

### Step 1: Build & Install the APK
1. Open the project in Android Studio (File → Open → select `LexiPopup/` folder)
2. Connect your Android phone (USB debugging ON) or start an emulator
3. Click **Run ▶** or press `Shift+F10`
4. Android Studio builds the APK and installs it automatically
5. Minimum Android version: **Android 10 (API 29)**

### Step 2: First Launch — Onboarding
The app opens to a 4-screen onboarding flow:

**Screen 1 — Instant Definitions**
> "Long-press any word in Moon+ Reader, tap Dictionary, select LexiPopup"
- Tap **Next**

**Screen 2 — Hindi + English**
> Explains bilingual popup features
- Tap **Next**

**Screen 3 — Grant Overlay Permission** ⭐ *Critical*
> This permission lets the popup float over Moon+ Reader
- Tap **Grant Overlay Permission**
- Android opens the "Display over other apps" system settings screen
- Find **LexiPopup** in the list → tap it → toggle **Allow** ON
- Press Back to return to the app
- Tap **Skip for now** if you want to continue without it (popup won't work yet)

**Screen 4 — Download Dictionary (Optional)**
> Choose how to start:
- **Download Dictionary Packs** → opens the download manager (recommended)
- **Start with seed words only** → goes to the home screen immediately with 129 built-in words

---

## Part 2 — Setting Up Moon+ Reader

### Step 1: Install Moon+ Reader
- Download from Google Play: **Moon+ Reader** (free version works fine)

### Step 2: Open any ebook in Moon+ Reader

### Step 3: Connect LexiPopup as the dictionary
1. In Moon+ Reader, go to **Settings (⚙) → Read → Dictionary app**
2. Select **LexiPopup** from the list
3. *(If not listed, skip to Step 4)*

### Step 4: Use the text-selection method (always works)
1. Long-press any word in Moon+ Reader
2. The text selection toolbar appears
3. Look for **"LexiPopup"** icon OR tap the **⋯ More** button
4. Select **LexiPopup** — the popup appears instantly

### Alternative: Share method (works in any app)
1. Select any word in any app (browser, Kindle, PDF viewer, etc.)
2. Tap **Share**
3. Select **LexiPopup** from the share sheet
4. The popup appears

---

## Part 3 — The Popup Window

### What the popup shows

```
┌─────────────────────────────────────────────┐
│  procrastination           ★ Favorite   ✕  │
│  /prəˌkræs.tɪˈneɪ.ʃən/   [noun]           │
├─────────────────────────────────────────────┤
│  📖 The action of delaying or postponing    │
│     something                               │
│                                             │
│  🇮🇳 टालमटोल (Taalmatol)                    │
│                                             │
│  📌 "Procrastination is the thief of       │
│      time; it robs you of productivity."   │
│                                             │
│  🔗 Synonyms: delay · postponement ···     │
│  ↔ Antonyms: promptness · diligence ···    │
│                                             │
│  🌱 From Latin procrastinatus…             │
│                                             │
│  ⚡ Intermediate     Frequency ████░ 72%  │
├─────────────────────────────────────────────┤
│  📋 Copy  🔊 Speak  🎙 Meaning  🌐 Translate│
│  📤 Share  📝 Note  📖 Full Details        │
└─────────────────────────────────────────────┘
         ☰ Drag handle (resize popup)
```

### How to interact with the popup

| Action | How |
|--------|-----|
| **Move popup** | Drag anywhere on the header bar |
| **Resize popup** | Drag the ◢ handle at the bottom-right corner |
| **Collapse to bubble** | Tap the **◉** button at top-left (if enabled) |
| **Expand from bubble** | Tap the pulsing circle |
| **Close popup** | Tap **✕** or press Android Back |
| **Mark favorite** | Tap **★** star icon |
| **Copy definition** | Tap 📋 Copy button |
| **Hear pronunciation** | Tap 🔊 Speak — speaks the word aloud |
| **Hear the definition** | Tap 🎙 Meaning — reads the definition aloud |
| **Translate** | Tap 🌐 Translate — opens Google Translate |
| **Share** | Tap 📤 Share — system share sheet |
| **Add a note** | Tap 📝 Note — type your personal note |
| **Full dictionary page** | Tap 📖 Full Details — opens full word screen |

### Tap a synonym/antonym chip
Tapping any word in the Synonyms or Antonyms row looks up **that word** immediately — you can chain through related words.

---

## Part 4 — Manual Search (Notification)

You don't need Moon+ Reader to look up a word. A **persistent notification** lives in your notification drawer:

1. Pull down notification shade
2. Tap the **LexiPopup** notification
3. A search popup appears
4. Type any word → results appear as suggestions
5. Tap a suggestion or press GO

**To toggle the notification on/off:**
Dashboard → Settings tab → *Notification* section → **Persistent notification** switch

---

## Part 5 — Dashboard App

Tap the LexiPopup icon on your home screen to open the main dashboard.

### Tab 1: Home
- **Today's count** — how many words you've looked up today
- **Word of the Day** — changes daily (deterministic, same word all day)
- **Recent words** — last 50 words you looked up, tap any to see details
- **Favorites** — words you've starred ★ in the popup
- **Weekly activity chart** — bar chart of lookups per day

### Tab 2: Dictionary
Full dictionary browser:
- **A–Z tabs** — browse all words starting with each letter
- **Search bar** at top — type any word to search
- **Sort** — alphabetical / by frequency / by difficulty
- **Filter** — show only nouns / verbs / adjectives / etc.
- Tap any word → full word detail screen with all sections + TTS + share

### Tab 3: Flashcards
Spaced-repetition flashcard system (SM-2 algorithm):
- Shows the **word** on front, **definition + Hindi + example** on back
- **Tap or swipe up** to flip the card
- After flipping, rate yourself:
  - **Again (0)** — didn't remember, will repeat soon
  - **Hard (3)** — remembered with difficulty
  - **Good (4)** — remembered correctly
  - **Easy (5)** — perfect, interval increases significantly
- The app automatically schedules the next review date
- Cards you rate low come back the next day; cards you rate high come back in days/weeks

> **Auto-generate:** If enabled in Settings, every word you look up in the popup is automatically added as a flashcard.

### Tab 4: Statistics
- **Activity heatmap** — GitHub-style 12-week calendar showing how many words you looked up per day
- **Weekly bar chart** — lookups per day for the current week
- **Most searched** — top 10 words you look up most often
- **Difficulty breakdown** — pie chart showing the distribution of your vocabulary (Beginner / Intermediate / Advanced / Expert)

### Tab 5: Settings

#### 🎨 Popup Layout (10 toggles)
Turn on/off: Pronunciation (IPA) · Part of Speech chip · Detailed meaning · Hindi meaning · Example sentence · Synonyms · Antonyms · Etymology · Difficulty badge · Frequency meter

#### 🔘 Action Buttons (8 toggles)
Turn on/off any of the 7 buttons: Copy · Speak Word · Speak Meaning · Translate · Share · Save Note · Favorite · Full Details

#### 🧩 Popup Behavior
- Enable/disable dragging
- Enable/disable resizing
- Enable/disable collapse to bubble mode
- Auto-close after 5 seconds

#### 🔔 Notification
- Toggle the persistent notification launcher on/off

#### 📚 Vocabulary Tracking
- Save search history on/off
- Auto-generate flashcards on/off

#### 📖 Dictionary Data
- **Manage Dictionary Packs** → download Wiktionary (4.7M words), WordNet (155,000), and Hindi packs

#### 🤖 AI Features (Optional)
- Enter your **OpenAI API key** (starts with `sk-`)
- Once set, any word not found in the offline database is automatically explained by **GPT-4o-mini**
- The AI result is cached locally so the same word is fast next time
- To get a key: visit [platform.openai.com](https://platform.openai.com) → API Keys

#### 📤 Export
- **Export Vocabulary (CSV / JSON / Anki)** — exports all your looked-up words
  - *CSV*: open in Excel or Google Sheets
  - *JSON*: developer backup format
  - *Anki (.txt)*: import into the Anki desktop app for advanced flashcards
- **Export Settings as JSON** — backup your toggle settings
- **Reset to Defaults** — restores all toggles to factory settings

---

## Part 6 — Downloading Full Dictionary Packs

> The pack download URLs must be set up by the developer before these work. See the "Developer Setup" section.

### From onboarding
Tap **Download Dictionary Packs** on the last onboarding screen.

### From the app
Settings tab → **Manage Dictionary Packs** → choose a pack:

| Pack | Words | Size | Contents |
|------|-------|------|----------|
| **Minimal** | ~10,000 | ~15 MB | Most common English words |
| **Standard** | ~60,000 | ~80 MB | Wiktionary + WordNet |
| **Full** | ~100,000+ | ~200 MB | Wiktionary + WordNet + Hindi WordNet |

**Download progress screen shows:**
- Phase: Downloading → Decompressing → Verifying (SHA-256) → Importing
- MB downloaded / total MB
- Download speed (KB/s)
- ETA (seconds remaining)
- Words imported count

The download **resumes automatically** if interrupted (no need to start over).

---

## Part 7 — Word Lookup Logic (Technical Flow)

When you look up a word, LexiPopup searches in this order:

```
Word tapped in Moon+ Reader
         │
         ▼
┌─────────────────────┐
│  Layer 1: Memory    │ ← <1ms — LruCache of last 200 words this session
│  Cache (LruCache)   │
└────────┬────────────┘
         │ Not found
         ▼
┌─────────────────────┐
│  Layer 2: Room DB   │ ← <5ms — SQLite database (seed + downloaded packs)
│  (offline SQLite)   │
└────────┬────────────┘
         │ Not found
         ▼
┌─────────────────────┐
│  Layer 3: Online    │ ← ~200ms — api.dictionaryapi.dev (FREE, no key needed)
│  FreeDictionaryAPI  │ Result is saved to Room for next time (offline)
└────────┬────────────┘
         │ Not found / no internet
         ▼
┌─────────────────────┐
│  Layer 4: AI        │ ← ~2-5s — GPT-4o-mini via your OpenAI key
│  Explanation        │ Result is saved to Room for next time (offline)
└────────┬────────────┘
         │ Not found / no key set
         ▼
     Error shown:
  "Word not found in
   local dictionary"
```

---

## Part 8 — About & Legal

The app is built on these open data sources:

| Source | License | Coverage |
|--------|---------|----------|
| **Wiktionary** | CC BY-SA 3.0 | 4.7M+ entries, IPA, etymology, examples |
| **WordNet 3.1** (Princeton) | Free for all use | 155,000 words, synonyms, antonyms |
| **Hindi WordNet** (IIT Bombay) | GNU FDL — **non-commercial only** | Hindi meanings, Devanagari |
| **FreeDictionaryAPI** | Free | Online fallback |

> ⚠️ **If you plan to use LexiPopup commercially**, you must obtain a separate licence from IIT Bombay for the Hindi WordNet data. Contact: cfilt@cse.iitb.ac.in

---

## Part 9 — Troubleshooting

### Popup doesn't appear after tapping a word
1. Open **Settings** → Apps → LexiPopup → **"Display over other apps"** → Enable
2. Or: Dashboard → top bar warning icon ⚠ → tap to open permission screen

### Word shows "not found"
- Check internet connection (for online fallback)
- Or set an OpenAI API key in Settings → AI Features
- Or download a dictionary pack from Settings → Manage Dictionary Packs

### Notification doesn't appear
- Settings tab → Notification → toggle **Persistent notification** ON
- Check Android notification permissions for LexiPopup

### Popup disappeared after phone restart
- The persistent notification relaunches automatically after boot via a boot receiver
- If it doesn't: open the app once → go to Settings → toggle the notification

### TTS (speak) button doesn't work
- Go to Android Settings → Accessibility → Text-to-speech → install a TTS engine
- Google TTS or Samsung TTS both work

### Flashcards not generating automatically
- Settings tab → Vocabulary Tracking → **Auto-generate flashcards** → ON

---

## Part 10 — Developer Setup (Hosting Dictionary Packs)

To make the dictionary pack download work, you need to host the database files:

### 1. Generate the database files
The pack files must be gzipped SQLite databases with the same schema as `dictionary_cache` in Room:
```
table: dictionary_cache
columns: word, pronunciation, part_of_speech, short_meaning, detailed_meaning,
         hindi_meaning, hindi_pronunciation, example_sentence, synonyms (JSON),
         antonyms (JSON), etymology, difficulty_level, frequency_rating,
         source, is_favorite, user_note, last_accessed, access_count
```

### 2. Host the .db.gz files
Recommended: GitHub Releases (free, fast CDN):
```
https://github.com/YOUR_ORG/lexipopup-data/releases/latest/download/dict_minimal_v1.db.gz
https://github.com/YOUR_ORG/lexipopup-data/releases/latest/download/dict_standard_v1.db.gz
https://github.com/YOUR_ORG/lexipopup-data/releases/latest/download/dict_full_v1.db.gz
```

### 3. Update the URLs in code
Edit `DictionaryDownloadWorker.kt`:
```kotlin
private val PACK_URLS = mapOf(
    DatabasePack.MINIMAL.name  to "https://YOUR_REAL_URL/dict_minimal_v1.db.gz",
    DatabasePack.STANDARD.name to "https://YOUR_REAL_URL/dict_standard_v1.db.gz",
    DatabasePack.FULL.name     to "https://YOUR_REAL_URL/dict_full_v1.db.gz"
)
private val PACK_CHECKSUMS = mapOf(
    DatabasePack.MINIMAL.name  to "SHA256_OF_MINIMAL_FILE",
    DatabasePack.STANDARD.name to "SHA256_OF_STANDARD_FILE",
    DatabasePack.FULL.name     to "SHA256_OF_FULL_FILE"
)
```

### 4. Get the SHA-256 checksum
```bash
sha256sum dict_minimal_v1.db.gz
```

---

## Quick-Start Checklist

- [ ] Install APK on Android 10+ device
- [ ] Grant **"Display over other apps"** permission during onboarding
- [ ] Install Moon+ Reader from Google Play
- [ ] In Moon+ Reader: Settings → Read → Dictionary app → LexiPopup
- [ ] Open a book, long-press a word, tap LexiPopup
- [ ] Optionally: download a dictionary pack (Settings → Manage Dictionary Packs)
- [ ] Optionally: add OpenAI API key for AI fallback (Settings → AI Features)
- [ ] Optionally: enable persistent notification for quick search anywhere

---

*LexiPopup v1.0.0 — Built for Android readers who love words.*
