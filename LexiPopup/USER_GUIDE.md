# LexiPopup — Complete User Guide
### From Installation to Looking Up Any Word

---

## ⚠️ Dictionary Coverage (Read This First)

| Tier | Words Available | Requires |
|------|----------------|----------|
| **Built-in seed data** | 1,000 common words (fully offline) | Nothing — works immediately |
| **Online fallback** | ~200,000+ common English words | Internet connection (no key needed) |
| **Groq AI fallback** | Any word including rare/technical | Free API key from console.groq.com |
| **On-Device AI** | Any word, fully offline | One-time model download (1.5–2.7 GB) |
| **Downloaded packs** | ~10K – 700K+ offline words | Internet once to download |

> **Short answer:** You get 1,000 seed words on first install. For best coverage: download the Standard pack (offline, ~155K words) + configure a free Groq AI key (explains anything the dictionary misses).

---

## Part 1 — Installation

### Option A: Download from GitHub Releases (easiest)
1. Go to **https://github.com/neet-ctrl/LexiPopup/releases**
2. Download the latest `LexiPopup-debug-*.apk` or the signed release APK
3. Transfer to your phone → tap to install
4. Enable "Install from unknown sources" if prompted

### Option B: Build from source
See [SETUP_COMPLETE.md](SETUP_COMPLETE.md) for full instructions.

Minimum Android version: **Android 10 (API 29)**

### First Launch — Onboarding
The app opens to a 4-screen onboarding:
1. **Instant Definitions** → tap Next
2. **Hindi + English** → tap Next
3. **Grant Overlay Permission** ⭐ *Critical — tap "Grant Overlay Permission" and enable it*
4. **Download Dictionary (Optional)** → choose "Download Dictionary Packs" or "Start with seed words"

---

## Part 2 — Setting Up Moon+ Reader

1. Install **Moon+ Reader** from Google Play
2. Open Moon+ Reader → **Settings (⚙) → Read → Dictionary app** → select **LexiPopup**
3. Open any ebook → long-press a word → tap **LexiPopup** in the toolbar
4. The floating popup appears instantly

**Alternative (works in any app):** Select any text → tap **Share** → select **LexiPopup**

---

## Part 3 — The Popup Window

```
┌──────────────────────────────────────────────────┐
│  procrastination              ★ Favorite    ✕   │
│  /prəˌkræs.tɪˈneɪ.ʃən/  [noun]                  │
├──────────────────────────────────────────────────┤
│  📖 The action of delaying or postponing         │
│     something                                    │
│                                                  │
│  🇮🇳 टालमटोल (Taalmatol)                         │
│                                                  │
│  📌 "Procrastination is the thief of time"       │
│                                                  │
│  🔗 Synonyms: delay · postponement · deferral    │
│  ↔ Antonyms: promptness · diligence              │
│                                                  │
│  🌱 From Latin procrastinatus…                   │
│                                                  │
│  ⚡ Advanced          Frequency  ███░░  55%      │
├──────────────────────────────────────────────────┤
│  📋 Copy  🔊 Speak  🎙 Meaning  🌐 Translate     │
│  📤 Share  📝 Note  📖 Full Details              │
└──────────────────────────────────────────────────┘
                ☰ Drag handle (resize)
```

| Action | How |
|--------|-----|
| **Move popup** | Drag the header bar |
| **Resize** | Drag the ◢ corner handle |
| **Collapse to bubble** | Tap ◉ top-left |
| **Expand from bubble** | Tap the pulsing circle |
| **Close** | Tap ✕ or press Back |
| **Favorite** | Tap ★ |
| **Copy definition** | Tap 📋 |
| **Speak word** | Tap 🔊 |
| **Speak definition** | Tap 🎙 |
| **Translate** | Tap 🌐 (opens Google Translate) |
| **Share** | Tap 📤 |
| **Add note** | Tap 📝 |
| **Full details** | Tap 📖 |
| **Tap synonym/antonym** | Looks up that word immediately |

---

## Part 4 — Manual Search (Notification)

A **persistent notification** stays in your notification shade:
1. Pull down notification shade
2. Tap the **LexiPopup** notification
3. Type any word → suggestions appear → tap or press GO

Toggle it: **Settings tab → Notification → Persistent notification**

---

## Part 5 — Dashboard Tabs

### 🏠 Home
- Today's lookup count
- Word of the Day (changes daily)
- Recent words (last 50)
- Favorites ★
- Weekly activity chart

### 📖 Dictionary
Full browser with A–Z tabs, search bar, sort, filter by POS.

### 🃏 Flashcards (SM-2 Spaced Repetition)
- Word on front, definition + Hindi + example on back
- Tap or swipe up to flip
- Rate: **Again (0)** · **Hard (3)** · **Good (4)** · **Easy (5)**
- SM-2 algorithm schedules your next review automatically

### 📊 Statistics
- GitHub-style 12-week activity heatmap
- Weekly bar chart
- Top 10 most-searched words
- Difficulty distribution pie chart

### ⚙ Settings
See Part 6 below.

---

## Part 6 — Settings Tab

### 🎨 Popup Layout (toggles)
IPA pronunciation · Part of speech · Detailed meaning · Hindi meaning · Example sentence · Synonyms · Antonyms · Etymology · Difficulty badge · Frequency meter

### 🔘 Action Buttons (toggles)
Copy · Speak Word · Speak Meaning · Translate · Share · Save Note · Favorite · Full Details

### 🧩 Popup Behavior
Dragging · Resizing · Bubble collapse mode · Auto-close (5s)

### 🔔 Notification
Persistent notification launcher on/off

### 📚 Vocabulary Tracking
Save history · Auto-generate flashcards

### 📖 Dictionary Data
**Manage Dictionary Packs** — download offline packs:

| Pack | Words | Size | Contents |
|------|-------|------|----------|
| **Minimal** | ~10,000 | ~15 MB | Most common English + Hindi |
| **Standard** | ~155,000 | ~80 MB | Full WordNet + Hindi — recommended |
| **Full** | ~700,000+ | ~200 MB | Wiktionary + WordNet + Hindi WordNet |

### 🤖 AI Assistant
LexiPopup uses AI only as a last resort — offline dictionary first, then online FreeDictionaryAPI, then AI.

Tap **"Configure"** to open the AI settings:

#### Groq Cloud (recommended, free)
1. Go to **https://console.groq.com** → sign up (no credit card)
2. API Keys → Create → copy the `gsk_...` key
3. Paste it in LexiPopup → AI Settings → Groq API Key
- **Free: 1,000 lookups/day, 30/minute**
- Model: llama-3.3-70b-versatile

#### On-Device AI (offline, no API key)
1. Select a model:
   - **Gemma 2B Tiny** — 1.5 GB download, ~4 GB RAM needed
   - **Phi-2** — 2.7 GB download, ~6 GB RAM needed
2. Tap "Download Model"
3. Works offline forever after download

#### Hybrid Mode
Runs Groq + On-Device simultaneously, shows the best result (or lets you compare both).

### 📤 Export
- **Export Vocabulary** as CSV (Excel), JSON, or Anki TSV
- **Export Settings** as JSON backup
- **Reset to Defaults**

---

## Part 7 — Word Lookup Flow (Technical)

```
Word tapped in Moon+ Reader
         │
         ▼
┌──────────────────────┐
│  Layer 1: LRU Cache  │  <1 ms — last 200 words this session
└───────┬──────────────┘
        │ miss
        ▼
┌──────────────────────┐
│  Layer 2: Room DB    │  <5 ms — seed + downloaded pack
└───────┬──────────────┘
        │ miss
        ▼
┌──────────────────────┐
│  Layer 3: Online API │  ~200ms — api.dictionaryapi.dev (free, no key)
│  (FreeDictionaryAPI) │  Result saved to Room for next time
└───────┬──────────────┘
        │ miss / no internet
        ▼
┌──────────────────────┐
│  Layer 4: Groq AI    │  ~1-3s — llama-3.3-70b (free key from groq.com)
│  (Cloud AI)          │  Result saved to Room for next time
└───────┬──────────────┘
        │ miss / no key
        ▼
┌──────────────────────┐
│  Layer 5: On-Device  │  ~3-8s — Gemma 2B / Phi-2 (downloaded model)
│  AI (MediaPipe)      │  Result saved to Room for next time
└───────┬──────────────┘
        │ miss
        ▼
   "Word not found"
```

---

## Part 8 — About & Legal

| Source | License | Coverage |
|--------|---------|----------|
| **Wiktionary** (kaikki.org) | CC BY-SA 3.0 | ~700,000+ English entries |
| **WordNet 3.1** (Princeton) | Free for all use | ~155,000 lemmas, synonyms, antonyms |
| **CMU Pronouncing Dictionary** | Free / Public Domain | IPA pronunciation |
| **Hindi WordNet / OMW** (IIT Bombay) | GNU FDL — **non-commercial only** | Hindi meanings |
| **FreeDictionaryAPI** | Free | Online fallback |
| **Groq AI** | Free tier | AI explanations |

> ⚠️ **Commercial use:** If you use LexiPopup commercially, you need a separate licence from IIT Bombay for Hindi WordNet data. Contact: cfilt@cse.iitb.ac.in

---

## Part 9 — Troubleshooting

| Problem | Fix |
|---------|-----|
| Popup doesn't appear | Settings → Apps → LexiPopup → "Display over other apps" → ON |
| Word shows "not found" | Need: internet, or dictionary pack, or Groq key |
| AI says "No API key" | Settings → AI Assistant → Configure → enter Groq key |
| Dictionary download fails | Check WiFi; tap retry (download resumes where it left off) |
| Notification doesn't appear | Settings → Notification → toggle ON |
| TTS (speak) doesn't work | Android Settings → Accessibility → Text-to-speech → install Google TTS |
| Popup disappeared after restart | Open app → Settings → toggle notification off then on |
| Flashcards not auto-generating | Settings → Vocabulary Tracking → Auto-generate flashcards → ON |

---

## Quick-Start Checklist

- [ ] Install APK
- [ ] Grant "Display over other apps" during onboarding
- [ ] Install Moon+ Reader
- [ ] In Moon+: Settings → Read → Dictionary → LexiPopup
- [ ] Download dictionary pack (Standard recommended — ~155K words)
- [ ] Get free Groq key → console.groq.com → paste in AI Settings
- [ ] Long-press a word in Moon+ → LexiPopup → enjoy 🎉

---

*LexiPopup v1.0.0 — Built for Android readers who love words.*
