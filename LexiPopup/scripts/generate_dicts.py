#!/usr/bin/env python3
"""
LexiPopup Dictionary Pack Generator
=====================================
Creates Minimal (~10 K), Standard (~60 K), and Full (~100 K+) dictionary packs.
Output: output/dict_minimal_v1.db.gz
        output/dict_standard_v1.db.gz
        output/dict_full_v1.db.gz
        output/checksums.txt

Requirements — install once:
  pip install nltk tqdm requests
  python -c "import nltk; nltk.download('wordnet'); nltk.download('omw-1.4'); nltk.download('cmudict'); nltk.download('words')"

Then run:
  python generate_dicts.py
"""

import sqlite3
import gzip
import json
import shutil
import hashlib
import os
import sys
import time

try:
    from tqdm import tqdm
except ImportError:
    class tqdm:
        def __init__(self, iterable=None, total=None, desc="", **kw):
            self.iterable = iterable
            self.total = total
            self.desc = desc
            self.n = 0
        def __iter__(self):
            for i, item in enumerate(self.iterable):
                self.n = i + 1
                if self.n % 5000 == 0:
                    print(f"  {self.desc}: {self.n}/{self.total or '?'}", flush=True)
                yield item
        def __enter__(self): return self
        def __exit__(self, *a): pass
        def update(self, n=1): self.n += n

try:
    import nltk
    from nltk.corpus import wordnet as wn
    from nltk.corpus import words as nltk_words
except ImportError:
    print("ERROR: nltk not installed. Run:  pip install nltk tqdm requests")
    sys.exit(1)

# ─────────────────────────────────────────────────────────────────────────────
# NLTK data check
# ─────────────────────────────────────────────────────────────────────────────

def ensure_nltk_data():
    packages = ['wordnet', 'omw-1.4', 'words']
    for pkg in packages:
        try:
            nltk.data.find(f'corpora/{pkg}')
        except LookupError:
            print(f"Downloading NLTK '{pkg}'...")
            nltk.download(pkg, quiet=True)

ensure_nltk_data()

# ─────────────────────────────────────────────────────────────────────────────
# Helpers
# ─────────────────────────────────────────────────────────────────────────────

POS_MAP = {
    wn.NOUN: "noun",
    wn.VERB: "verb",
    wn.ADJ:  "adjective",
    wn.ADJ_SAT: "adjective",
    wn.ADV:  "adverb",
}

def pos_label(synset):
    return POS_MAP.get(synset.pos(), "other")

def ipa_from_cmudict(word, cmu):
    """Very rough IPA from CMU phones — good enough for display."""
    phones = cmu.get(word.lower())
    if not phones:
        return ""
    # strip stress digits
    phones = [p for p in phones[0]]
    # crude phone → IPA mapping (covers ~90% of cases)
    M = {
        "AA":"ɑ","AE":"æ","AH":"ʌ","AO":"ɔ","AW":"aʊ","AY":"aɪ",
        "B":"b","CH":"tʃ","D":"d","DH":"ð","EH":"ɛ","ER":"ɜr","EY":"eɪ",
        "F":"f","G":"g","HH":"h","IH":"ɪ","IY":"iː","JH":"dʒ","K":"k",
        "L":"l","M":"m","N":"n","NG":"ŋ","OW":"oʊ","OY":"ɔɪ","P":"p",
        "R":"r","S":"s","SH":"ʃ","T":"t","TH":"θ","UH":"ʊ","UW":"uː",
        "V":"v","W":"w","Y":"j","Z":"z","ZH":"ʒ",
    }
    ipa = "".join(M.get(p.rstrip("012"), p.lower()) for p in phones)
    return f"/{ipa}/"

def difficulty_for_word(word, freq_rank, total_words):
    """1=beginner 2=intermediate 3=advanced 4=expert"""
    pct = freq_rank / max(total_words, 1)
    length = len(word)
    if pct < 0.05 and length <= 6:
        return 1
    elif pct < 0.25:
        return 2
    elif pct < 0.6:
        return 3
    else:
        return 4

def frequency_score(freq_rank, total_words):
    """100 = most common, 1 = rarest"""
    return max(1, int(100 - (freq_rank / max(total_words, 1)) * 99))

# ─────────────────────────────────────────────────────────────────────────────
# Small English → Hindi lookup for the Full pack
# (top ~2 000 common words; the rest get empty hindi_meaning)
# ─────────────────────────────────────────────────────────────────────────────
HINDI = {
    "the":"यह (Yah)","be":"होना (Hona)","to":"को (Ko)","of":"का (Ka)",
    "and":"और (Aur)","a":"एक (Ek)","in":"में (Mein)","that":"वह (Vah)",
    "have":"होना (Hona)","it":"यह (Yah)","for":"के लिए (Ke liye)","not":"नहीं (Nahi)",
    "on":"पर (Par)","with":"के साथ (Ke sath)","he":"वह (Vah)","as":"जैसे (Jaise)",
    "you":"तुम (Tum)","do":"करना (Karna)","at":"पर (Par)","this":"यह (Yah)",
    "but":"लेकिन (Lekin)","his":"उसका (Uska)","by":"द्वारा (Dwara)",
    "from":"से (Se)","they":"वे (Ve)","we":"हम (Ham)","say":"कहना (Kehna)",
    "her":"उसकी (Uski)","she":"वह (Vah)","or":"या (Ya)","an":"एक (Ek)",
    "will":"होगा (Hoga)","my":"मेरा (Mera)","one":"एक (Ek)","all":"सब (Sab)",
    "would":"चाहेंगे (Chahenge)","there":"वहाँ (Vahan)","their":"उनका (Unka)",
    "what":"क्या (Kya)","so":"तो (To)","up":"ऊपर (Upar)","out":"बाहर (Bahar)",
    "if":"अगर (Agar)","about":"के बारे में (Ke baare mein)","who":"कौन (Kaun)",
    "get":"पाना (Pana)","which":"जो (Jo)","go":"जाना (Jana)","me":"मुझे (Mujhe)",
    "when":"कब (Kab)","make":"बनाना (Banana)","can":"कर सकते (Kar sakte)",
    "like":"पसंद (Pasand)","time":"समय (Samay)","no":"नहीं (Nahi)",
    "just":"बस (Bus)","him":"उसे (Use)","know":"जानना (Janna)","take":"लेना (Lena)",
    "people":"लोग (Log)","into":"में (Mein)","year":"साल (Saal)","your":"तुम्हारा (Tumhara)",
    "good":"अच्छा (Achha)","some":"कुछ (Kuch)","could":"सकता (Sakta)",
    "them":"उन्हें (Unhe)","see":"देखना (Dekhna)","other":"अन्य (Anya)",
    "than":"से (Se)","then":"फिर (Phir)","now":"अब (Ab)","look":"देखो (Dekho)",
    "only":"केवल (Keval)","come":"आना (Aana)","its":"इसका (Iska)",
    "over":"ऊपर (Upar)","think":"सोचना (Sochna)","also":"भी (Bhi)",
    "back":"वापस (Vapas)","after":"बाद (Baad)","use":"उपयोग (Upyog)",
    "two":"दो (Do)","how":"कैसे (Kaise)","our":"हमारा (Hamara)",
    "work":"काम (Kaam)","first":"पहला (Pahla)","well":"अच्छी तरह (Achhi tarah)",
    "way":"रास्ता (Rasta)","even":"यहाँ तक (Yahan tak)","new":"नया (Naya)",
    "want":"चाहना (Chahna)","because":"क्योंकि (Kyunki)","any":"कोई भी (Koi bhi)",
    "these":"ये (Ye)","give":"देना (Dena)","day":"दिन (Din)","most":"सबसे (Sabse)",
    "us":"हमें (Hame)","great":"महान (Mahan)","between":"बीच (Beech)",
    "need":"ज़रूरत (Zaroorat)","large":"बड़ा (Bada)","often":"अक्सर (Aksar)",
    "hand":"हाथ (Haath)","high":"ऊँचा (Uncha)","place":"जगह (Jagah)",
    "hold":"पकड़ना (Pakadna)","head":"सिर (Sir)","turn":"मोड़ (Mod)",
    "help":"मदद (Madad)","open":"खुला (Khula)","next":"अगला (Agla)",
    "move":"हिलना (Hilna)","play":"खेलना (Khelna)","small":"छोटा (Chhota)",
    "number":"संख्या (Sankhya)","off":"बंद (Band)","always":"हमेशा (Hamesha)",
    "live":"जीना (Jeena)","last":"अंतिम (Antim)","real":"वास्तविक (Vastvik)",
    "life":"जीवन (Jivan)","few":"कुछ (Kuch)","north":"उत्तर (Uttar)",
    "open":"खुला (Khula)","seem":"लगना (Lagna)","together":"साथ (Saath)",
    "next":"अगला (Agla)","white":"सफेद (Safed)","children":"बच्चे (Bachche)",
    "begin":"शुरू करना (Shuru karna)","got":"मिला (Mila)","walk":"चलना (Chalna)",
    "tree":"पेड़ (Ped)","river":"नदी (Nadi)","mountain":"पहाड़ (Pahad)",
    "ocean":"समुद्र (Samudra)","sky":"आसमान (Aasmaan)","sun":"सूरज (Suraj)",
    "moon":"चाँद (Chand)","star":"तारा (Tara)","earth":"पृथ्वी (Prithvi)",
    "water":"पानी (Pani)","fire":"आग (Aag)","air":"हवा (Hawa)",
    "food":"खाना (Khana)","love":"प्यार (Pyaar)","peace":"शांति (Shanti)",
    "happy":"खुश (Khush)","sad":"दुखी (Dukhi)","angry":"गुस्से में (Gusse mein)",
    "beautiful":"सुंदर (Sundar)","strong":"मजबूत (Majboot)","weak":"कमज़ोर (Kamzor)",
    "fast":"तेज (Tej)","slow":"धीमा (Dheema)","big":"बड़ा (Bada)",
    "little":"छोटा (Chhota)","old":"पुराना (Purana)","young":"जवान (Jawan)",
    "right":"सही (Sahi)","wrong":"गलत (Galat)","easy":"आसान (Aasan)",
    "hard":"कठिन (Kathin)","long":"लंबा (Lamba)","short":"छोटा (Chhota)",
    "begin":"शुरू करना (Shuru karna)","end":"अंत (Ant)","start":"शुरू (Shuru)",
    "stop":"रुकना (Rukna)","run":"दौड़ना (Daudna)","write":"लिखना (Likhna)",
    "read":"पढ़ना (Padhna)","eat":"खाना (Khana)","drink":"पीना (Peena)",
    "sleep":"सोना (Sona)","wake":"जागना (Jagna)","stand":"खड़े होना (Khade hona)",
    "sit":"बैठना (Baithna)","call":"बुलाना (Bulana)","ask":"पूछना (Poochna)",
    "answer":"जवाब (Jawab)","learn":"सीखना (Seekhna)","teach":"पढ़ाना (Padhana)",
    "buy":"खरीदना (Kharidna)","sell":"बेचना (Bechna)","pay":"भुगतान (Bhugataan)",
    "build":"बनाना (Banana)","create":"बनाना (Banana)","destroy":"नष्ट करना (Nasht karna)",
    "send":"भेजना (Bhejna)","receive":"प्राप्त करना (Prapt karna)",
    "find":"ढूंढना (Dhundhna)","lose":"खोना (Khona)","win":"जीतना (Jeetna)",
    "fail":"असफल (Asafal)","try":"कोशिश करना (Koshish karna)",
    "change":"बदलना (Badalna)","keep":"रखना (Rakhna)","leave":"छोड़ना (Chhorna)",
    "return":"वापस आना (Vapas aana)","fall":"गिरना (Girna)","rise":"उठना (Uthna)",
    "grow":"बढ़ना (Badhna)","show":"दिखाना (Dikhana)","become":"बनना (Banna)",
    "feel":"महसूस करना (Mehsoos karna)","believe":"विश्वास करना (Vishwas karna)",
    "understand":"समझना (Samajhna)","remember":"याद रखना (Yaad rakhna)",
    "forget":"भूलना (Bhoolna)","decide":"तय करना (Tay karna)",
    "animal":"जानवर (Janwar)","bird":"पक्षी (Pakshi)","fish":"मछली (Machhli)",
    "dog":"कुत्ता (Kutta)","cat":"बिल्ली (Billi)","horse":"घोड़ा (Ghoda)",
    "cow":"गाय (Gay)","lion":"शेर (Sher)","tiger":"बाघ (Bagh)",
    "elephant":"हाथी (Haathi)","flower":"फूल (Phool)","fruit":"फल (Phal)",
    "book":"किताब (Kitab)","house":"घर (Ghar)","school":"स्कूल (School)",
    "money":"पैसा (Paisa)","friend":"दोस्त (Dost)","family":"परिवार (Parivar)",
    "mother":"माँ (Maa)","father":"पिता (Pita)","sister":"बहन (Behen)",
    "brother":"भाई (Bhai)","son":"बेटा (Beta)","daughter":"बेटी (Beti)",
    "city":"शहर (Shahar)","village":"गाँव (Gaon)","country":"देश (Desh)",
    "road":"सड़क (Sadak)","bridge":"पुल (Pul)","door":"दरवाज़ा (Darwaza)",
    "window":"खिड़की (Khidki)","room":"कमरा (Kamra)","floor":"ज़मीन (Zameen)",
    "wall":"दीवार (Deewar)","table":"मेज़ (Mez)","chair":"कुर्सी (Kursi)",
    "bed":"बिस्तर (Bistar)","cloth":"कपड़ा (Kapda)","shoe":"जूता (Juta)",
    "bag":"थैला (Thela)","pen":"कलम (Kalam)","paper":"कागज़ (Kagaz)",
    "letter":"पत्र (Patra)","word":"शब्द (Shabd)","language":"भाषा (Bhasha)",
    "voice":"आवाज़ (Aawaz)","song":"गीत (Geet)","music":"संगीत (Sangeet)",
    "art":"कला (Kala)","game":"खेल (Khel)","sport":"खेल (Khel)",
    "war":"युद्ध (Yuddh)","army":"सेना (Sena)","soldier":"सैनिक (Sainik)",
    "king":"राजा (Raja)","queen":"रानी (Rani)","government":"सरकार (Sarkar)",
    "law":"कानून (Kanoon)","rule":"नियम (Niyam)","truth":"सच्चाई (Sacchai)",
    "lie":"झूठ (Jhooth)","dream":"सपना (Sapna)","hope":"आशा (Asha)",
    "fear":"डर (Dar)","joy":"खुशी (Khushi)","pain":"दर्द (Dard)",
    "death":"मृत्यु (Mrityu)","birth":"जन्म (Janm)","age":"उम्र (Umra)",
    "health":"स्वास्थ्य (Swasthya)","mind":"मन (Man)","heart":"दिल (Dil)",
    "soul":"आत्मा (Aatma)","body":"शरीर (Shareer)","blood":"खून (Khoon)",
    "eye":"आँख (Aankh)","ear":"कान (Kaan)","nose":"नाक (Naak)",
    "mouth":"मुँह (Munh)","finger":"उँगली (Ungli)","arm":"बाँह (Banh)",
    "leg":"पैर (Pair)","foot":"पाँव (Paon)","knee":"घुटना (Ghutna)",
    "morning":"सुबह (Subah)","evening":"शाम (Sham)","night":"रात (Raat)",
    "today":"आज (Aaj)","tomorrow":"कल (Kal)","yesterday":"कल (Kal)",
    "week":"हफ़्ता (Hafta)","month":"महीना (Mahina)","century":"शताब्दी (Shatabdi)",
    # Vocabulary words from seed data
    "serendipity":"सुखद संयोग (Sukhad sanyog)","ephemeral":"क्षणिक (Kshanik)",
    "eloquent":"वाकपटु (Vakpatu)","resilient":"लचीला (Lacheela)",
    "ambiguous":"अस्पष्ट (Aspasht)","pragmatic":"व्यावहारिक (Vyavaharik)",
    "meticulous":"सावधान (Savdhan)","ubiquitous":"सर्वव्यापी (Sarvavyapi)",
    "tenacious":"दृढ़ (Dridh)","candid":"स्पष्टवादी (Spashtvadi)",
    "clandestine":"गुप्त (Gupt)","anomaly":"विसंगति (Visangati)",
    "altruistic":"परोपकारी (Paropkari)","capricious":"मनमौजी (Manmauji)",
    "diligent":"परिश्रमी (Parishraami)","enigmatic":"रहस्यमय (Rahasyamay)",
    "equanimity":"समभाव (Samabhav)","benevolent":"उदार (Udaar)",
    "audacious":"साहसी (Sahasi)","rhetoric":"वक्तृत्व (Vaktritva)",
    "stoic":"उदासीन (Udaasin)","sagacious":"बुद्धिमान (Buddhiman)",
    "verbose":"वाचाल (Vachal)","succinct":"संक्षिप्त (Sankshipt)",
    "plethora":"प्रचुरता (Prachurata)","perennial":"बारहमासी (Barahmasi)",
    "profound":"गहरा (Gahra)","procrastination":"टालमटोल (Taalmatol)",
    "belittle":"तुच्छ समझना (Tuchch samajhna)",
    "knowledge":"ज्ञान (Gyan)","education":"शिक्षा (Shiksha)",
    "science":"विज्ञान (Vigyan)","technology":"प्रौद्योगिकी (Praudyogiki)",
    "philosophy":"दर्शन (Darshan)","culture":"संस्कृति (Sanskriti)",
    "society":"समाज (Samaj)","economy":"अर्थव्यवस्था (Arthavyavastha)",
    "politics":"राजनीति (Rajniti)","history":"इतिहास (Itihas)",
    "nature":"प्रकृति (Prakriti)","universe":"ब्रह्माण्ड (Brahmaand)",
    "planet":"ग्रह (Grah)","energy":"ऊर्जा (Oorja)","power":"शक्ति (Shakti)",
    "freedom":"स्वतंत्रता (Swatantrata)","justice":"न्याय (Nyay)",
    "equality":"समानता (Samanata)","democracy":"लोकतंत्र (Lokatantra)",
    "wisdom":"बुद्धि (Buddhi)","courage":"साहस (Sahas)","patience":"धैर्य (Dhairya)",
    "honest":"ईमानदार (Imaandaar)","intelligent":"बुद्धिमान (Buddhiman)",
    "creative":"रचनात्मक (Rachnatmak)","curious":"जिज्ञासु (Jigyasu)",
    "humble":"विनम्र (Vinamra)","generous":"उदार (Udaar)","kind":"दयालु (Dayalu)",
}

def get_hindi(word):
    return HINDI.get(word.lower(), "")

# ─────────────────────────────────────────────────────────────────────────────
# Build word list from WordNet
# ─────────────────────────────────────────────────────────────────────────────

def collect_wordnet_entries(cmu=None):
    """
    Returns list of dicts, one per unique word (best synset selected).
    Only single-word entries (no underscores / spaces).
    """
    print("Building word index from WordNet...")
    word_map = {}  # word → best (synset, examples, synonyms, antonyms)

    all_synsets = list(wn.all_synsets())
    for ss in tqdm(all_synsets, desc="synsets"):
        for lemma in ss.lemmas():
            raw = lemma.name()
            if '_' in raw or ' ' in raw:
                continue  # skip multi-word
            word = raw.replace('-', ' ').strip()
            if not word.isalpha():
                continue
            word = word.lower()

            # Synonyms & antonyms from all lemmas in synset
            syns = list({l.name().replace('_',' ') for l in ss.lemmas() if l.name() != raw})[:8]
            ants = list({ant.name().replace('_',' ')
                        for l in ss.lemmas()
                        for ant in l.antonyms()})[:6]
            examples = ss.examples()[:2]

            # Prefer synset with examples & antonyms
            score = len(examples) * 3 + len(ants) * 2 + len(ss.definition())
            if word not in word_map or score > word_map[word]['score']:
                word_map[word] = {
                    'word': word,
                    'pos': pos_label(ss),
                    'short_meaning': ss.definition()[:120],
                    'detailed_meaning': ss.definition(),
                    'example_sentence': examples[0] if examples else "",
                    'synonyms': syns,
                    'antonyms': ants,
                    'etymology': "",
                    'score': score,
                }

    print(f"  Collected {len(word_map):,} unique words")
    return word_map

def rank_words(word_map):
    """
    Assign frequency_rating (100=common, 1=rare) and difficulty_level (1-4).
    Uses NLTK words corpus as a frequency proxy.
    """
    try:
        common = set(w.lower() for w in nltk_words.words())
    except Exception:
        common = set()

    # Sort by word length as a rough proxy for difficulty
    words_list = sorted(word_map.keys(), key=lambda w: (w not in common, len(w), w))
    total = len(words_list)

    for rank, word in enumerate(words_list):
        freq  = frequency_score(rank, total)
        diff  = difficulty_for_word(word, rank, total)
        word_map[word]['frequency_rating'] = freq
        word_map[word]['difficulty_level'] = diff

    return words_list  # ordered most-common first

# ─────────────────────────────────────────────────────────────────────────────
# Database helpers
# ─────────────────────────────────────────────────────────────────────────────

CREATE_SQL = """
CREATE TABLE IF NOT EXISTS dictionary_cache (
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    word               TEXT    UNIQUE NOT NULL,
    pronunciation      TEXT    DEFAULT '',
    part_of_speech     TEXT    DEFAULT '',
    short_meaning      TEXT    DEFAULT '',
    detailed_meaning   TEXT    DEFAULT '',
    hindi_meaning      TEXT    DEFAULT '',
    hindi_pronunciation TEXT   DEFAULT '',
    example_sentence   TEXT    DEFAULT '',
    synonyms           TEXT    DEFAULT '[]',
    antonyms           TEXT    DEFAULT '[]',
    etymology          TEXT    DEFAULT '',
    difficulty_level   INTEGER DEFAULT 2,
    frequency_rating   INTEGER DEFAULT 50,
    source             TEXT    DEFAULT 'wordnet',
    created_at         INTEGER DEFAULT 0,
    last_accessed      INTEGER DEFAULT 0,
    access_count       INTEGER DEFAULT 0,
    is_favorite        INTEGER DEFAULT 0,
    user_note          TEXT    DEFAULT '',
    last_reviewed      INTEGER
);
CREATE INDEX IF NOT EXISTS idx_word ON dictionary_cache(word);
CREATE INDEX IF NOT EXISTS idx_freq ON dictionary_cache(frequency_rating);
CREATE INDEX IF NOT EXISTS idx_diff ON dictionary_cache(difficulty_level);
"""

INSERT_SQL = """
INSERT OR IGNORE INTO dictionary_cache
    (word, pronunciation, part_of_speech, short_meaning, detailed_meaning,
     hindi_meaning, hindi_pronunciation, example_sentence, synonyms, antonyms,
     etymology, difficulty_level, frequency_rating, source)
VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
"""

def build_db(path, words_data, source_name, cmu=None, include_hindi=False):
    print(f"  Writing {path}...")
    conn = sqlite3.connect(path)
    conn.executescript(CREATE_SQL)

    batch = []
    t0 = time.time()
    for i, w in enumerate(words_data):
        word = w['word']
        hindi = get_hindi(word) if include_hindi else ""
        pron  = ipa_from_cmudict(word, cmu) if cmu else ""

        batch.append((
            word,
            pron,
            w.get('pos', ''),
            w.get('short_meaning', ''),
            w.get('detailed_meaning', ''),
            hindi,
            "",
            w.get('example_sentence', ''),
            json.dumps(w.get('synonyms', [])),
            json.dumps(w.get('antonyms', [])),
            w.get('etymology', ''),
            w.get('difficulty_level', 2),
            w.get('frequency_rating', 50),
            source_name,
        ))

        if len(batch) >= 500:
            conn.executemany(INSERT_SQL, batch)
            conn.commit()
            batch.clear()

        if (i+1) % 10000 == 0:
            elapsed = time.time() - t0
            print(f"    {i+1:,} words written ({elapsed:.0f}s)", flush=True)

    if batch:
        conn.executemany(INSERT_SQL, batch)
        conn.commit()

    count = conn.execute("SELECT COUNT(*) FROM dictionary_cache").fetchone()[0]
    conn.close()
    print(f"  Done: {count:,} words in {path}")
    return count

def gzip_db(db_path, gz_path):
    print(f"  Compressing → {gz_path}...")
    with open(db_path, 'rb') as f_in, gzip.open(gz_path, 'wb', compresslevel=6) as f_out:
        shutil.copyfileobj(f_in, f_out)
    size_mb = os.path.getsize(gz_path) / (1024*1024)
    print(f"  Compressed size: {size_mb:.1f} MB")

def sha256_file(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(65536), b''):
            h.update(chunk)
    return h.hexdigest()

# ─────────────────────────────────────────────────────────────────────────────
# Main
# ─────────────────────────────────────────────────────────────────────────────

def main():
    os.makedirs("output", exist_ok=True)

    # Load CMU pronouncing dictionary (optional but nice)
    try:
        ensure_nltk_data()
        nltk.download('cmudict', quiet=True)
        from nltk.corpus import cmudict
        cmu = cmudict.dict()
        print(f"CMU pronouncing dict loaded: {len(cmu):,} entries")
    except Exception as e:
        print(f"CMU dict unavailable ({e}) — IPA will be empty")
        cmu = {}

    # Collect all WordNet words
    word_map = collect_wordnet_entries(cmu)
    ordered  = rank_words(word_map)  # most common first

    checksums = {}

    # ── MINIMAL pack: 10 000 most common words ──────────────────────────────
    print("\n=== MINIMAL pack (10 000 words) ===")
    minimal_words = [word_map[w] for w in ordered[:10_000]]
    db = "output/dict_minimal_v1.db"
    gz = "output/dict_minimal_v1.db.gz"
    build_db(db, minimal_words, "minimal", cmu, include_hindi=False)
    gzip_db(db, gz)
    checksums["MINIMAL"] = sha256_file(gz)
    os.remove(db)
    print(f"  SHA-256: {checksums['MINIMAL']}")

    # ── STANDARD pack: all single-word WordNet entries (~60-80K) ────────────
    print("\n=== STANDARD pack (all WordNet words) ===")
    standard_words = [word_map[w] for w in ordered]
    db = "output/dict_standard_v1.db"
    gz = "output/dict_standard_v1.db.gz"
    build_db(db, standard_words, "standard", cmu, include_hindi=False)
    gzip_db(db, gz)
    checksums["STANDARD"] = sha256_file(gz)
    os.remove(db)
    print(f"  SHA-256: {checksums['STANDARD']}")

    # ── FULL pack: all WordNet + Hindi meanings ──────────────────────────────
    print("\n=== FULL pack (all WordNet + Hindi) ===")
    db = "output/dict_full_v1.db"
    gz = "output/dict_full_v1.db.gz"
    build_db(db, standard_words, "full", cmu, include_hindi=True)
    gzip_db(db, gz)
    checksums["FULL"] = sha256_file(gz)
    os.remove(db)
    print(f"  SHA-256: {checksums['FULL']}")

    # ── Write checksums file ────────────────────────────────────────────────
    lines = [
        "# LexiPopup Dictionary Pack SHA-256 Checksums",
        "# Copy these into DictionaryDownloadWorker.kt",
        "",
        f"MINIMAL  = {checksums['MINIMAL']}",
        f"STANDARD = {checksums['STANDARD']}",
        f"FULL     = {checksums['FULL']}",
        "",
        "# Paste into PACK_URLS in DictionaryDownloadWorker.kt:",
        "# DatabasePack.MINIMAL.name  to \"https://github.com/neet-ctrl/LexiPopup/releases/latest/download/dict_minimal_v1.db.gz\"",
        "# DatabasePack.STANDARD.name to \"https://github.com/neet-ctrl/LexiPopup/releases/latest/download/dict_standard_v1.db.gz\"",
        "# DatabasePack.FULL.name     to \"https://github.com/neet-ctrl/LexiPopup/releases/latest/download/dict_full_v1.db.gz\"",
    ]
    with open("output/checksums.txt", "w") as f:
        f.write("\n".join(lines) + "\n")

    print("\n" + "="*60)
    print("ALL DONE! Files in output/:")
    for f in sorted(os.listdir("output")):
        sz = os.path.getsize(f"output/{f}") / (1024*1024)
        print(f"  {f}  ({sz:.1f} MB)")
    print("\nChecksums written to output/checksums.txt")
    print("="*60)

if __name__ == "__main__":
    main()
