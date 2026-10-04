#!/usr/bin/env python3
"""
Comprehensive cleaner, canonicalizer, and binary builder for Lekhani datasets:
1. Cleans decomposed nuktas and vowel corruptions (ড়, ঢ়, য়, আ)
2. Fixes all poisoned, inverted, or misordered phonetic overrides (boro -> বড়, hole -> হলে, hote -> হতে, nice -> নিচে, etc.)
3. Corrects ordinal typos in autocorrect (2nd -> ২য়, 3rd -> ৩য়)
4. Alphabetically sorts keys and elements across all datasets (phonetic_overrides, autocorrect, suffix, dictionary)
5. Prunes invalid / garbage entries from dictionary
6. Synchronizes clean JSONs across lekhani-android, lekhani, and lekhani-engine
7. Recompiles phonetic_overrides.bin and dictionary.bin
"""

import json
import struct
import subprocess
from pathlib import Path

def sanitize_bengali_string(text: str) -> str:
    text = text.replace('\u09a1\u09bc', '\u09dc') # ড়
    text = text.replace('\u09a2\u09bc', '\u09dd') # ঢ়
    text = text.replace('\u09af\u09bc', '\u09df') # য়
    text = text.replace('\u0985\u09be', '\u0986') # অ + া -> আ
    return text

CURATED_OVERRIDES = {
    # Pronouns, greetings, core verbs, conversational roots
    "boro": [["বড়", 0.95], ["বোরো", 0.05]],
    "poro": [["পড়ো", 0.70], ["পরো", 0.30]],
    "pora": [["পড়া", 0.70], ["পরা", 0.30]],
    "dhoro": [["ধরো", 0.85], ["ধড়", 0.15]],
    "choro": [["চড়ো", 0.80], ["চরো", 0.20]],
    "jana": [["জানা", 0.95], ["জন", 0.05]],
    "keno": [["কেন", 0.85], ["কেনো", 0.15]],
    "shari": [["শাড়ি", 0.95], ["শারি", 0.05]],
    "churi": [["চুড়ি", 0.70], ["ছুরি", 0.30]],
    "aar": [["আর", 1.0]],
    "aalam": [["আলম", 1.0]],
    "dolo": [["দলো", 0.60], ["দলও", 0.40]],
    "jhagra": [["ঝগড়া", 1.0]],
    "duti": [["দুটি", 0.95], ["দুতি", 0.05]],
    "ekti": [["একটি", 1.0]],
    "akti": [["একটি", 1.0]],
    "akta": [["একটা", 1.0]],
    "ekta": [["একটা", 1.0]],
    "khawa": [["খাওয়া", 1.0]],
    "hawa": [["হাওয়া", 1.0]],
    "dawa": [["দাওয়া", 1.0]],
    "jaowa": [["যাওয়া", 1.0]],
    "paowa": [["পাওয়া", 1.0]],
    "kichu": [["কিছু", 1.0]],
    "kisu": [["কিছু", 1.0]],
    "shob": [["সব", 1.0]],
    "valo": [["ভালো", 1.0]],
    "bhalo": [["ভালো", 1.0]],
    "karon": [["কারণ", 1.0]],
    "karun": [["কারণ", 1.0]],
    "aschi": [["আসছি", 1.0]],
    "jacchi": [["যাচ্ছি", 1.0]],
    "ghori": [["ঘড়ি", 1.0]],
    "bari": [["বাড়ি", 1.0]],
    "gari": [["গাড়ি", 1.0]],
    "choto": [["ছোট", 1.0]],
    "koto": [["কত", 0.70], ["কতো", 0.30]],
    "joto": [["যত", 0.70], ["যতো", 0.30]],
    "toto": [["তত", 0.70], ["ততো", 0.30]],
    "taka": [["টাকা", 1.0]],
    "daktar": [["ডাক্তার", 1.0]],
    "mora": [["মরা", 0.80], ["মোরা", 0.20]],
    "hospital": [["হাসপাতাল", 1.0]],
    "hospitale": [["হাসপাতালে", 1.0]],
    "hospitaler": [["হাসপাতালের", 0.95], ["হসপিটালের", 0.05]],

    # Fixes for poisoned English transliterations & common texting ambiguities
    "ok": [["ওকে", 1.0], ["ঠিক আছে", 0.95]],
    "hole": [["হলে", 1.0], ["হোল", 0.30]],
    "hote": [["হতে", 1.0], ["হটে", 0.20]],
    "nice": [["নিচে", 1.0], ["নাইস", 0.30]],
    "tin": [["তিন", 1.0], ["টিন", 0.40]],
    "chil": [["ছিল", 1.0], ["চিল", 0.30]],
    "pan": [["পান", 1.0], ["প্যান", 0.30]],
    "apu": [["আপু", 1.0], ["অপু", 0.20]],
    "can": [["চান", 1.0], ["ক্যান", 0.50]],
    "kach": [["কাছ", 1.0], ["কাচ", 0.70]],
    "mail": [["মাইল", 1.0], ["মেল", 0.60]],
    "rato": [["রাত", 1.0], ["রাতো", 0.70], ["রত", 0.10]],
    "nirapotta": [["নিরাপত্তা", 1.0]],
    "onkito": [["অঙ্কিত", 1.0], ["অংকিত", 0.80]],
    "joripe": [["জরিপে", 1.0], ["জরীপে", 0.40]],
    "zini": [["যিনি", 1.0], ["জিনি", 0.10]],
    "mis": [["মিস", 1.0]],
    "dano": [["দান", 1.0], ["দানো", 0.30]],
    "eko": [["এক", 1.0], ["একও", 0.50]],
    "vabi": [["ভাবি", 1.0], ["ভাবী", 0.70]],
    "ashi": [["আসি", 1.0], ["আশি", 0.80]],

    # Core future tense verb pairs (Colloquial with 'o' -> Standard Modern)
    "korbo": [["করবো", 1.0], ["করব", 0.95]],
    "khabo": [["খাবো", 1.0], ["খাব", 0.95]],
    "bolbo": [["বলবো", 1.0], ["বলব", 0.95]],
    "shunbo": [["শুনবো", 1.0], ["শুনব", 0.95]],
    "dekhbo": [["দেখবো", 1.0], ["দেখব", 0.95]],
    "parbo": [["পারবো", 1.0], ["পারব", 0.95]],
    "thakbo": [["থাকবো", 1.0], ["থাকব", 0.95]],
    "likhbo": [["লিখবো", 1.0], ["লিখব", 0.95]],
    "ashbo": [["আসবো", 1.0], ["আসব", 0.95]],
    "jabo": [["যাবো", 1.0], ["যাব", 0.95]],

    # Conversational 2nd-person present continuous verbs
    "korcho": [["করছো", 1.0]],
    "dekhcho": [["দেখছো", 1.0]],
    "likhcho": [["লিখছো", 1.0]],
    "shuncho": [["শুনছো", 1.0]],
    "thakcho": [["থাকছো", 1.0]],
    "parcho": [["পারছো", 1.0]],
}

def clean_phonetic_overrides(src_path: Path):
    with open(src_path, 'r', encoding='utf-8') as f:
        data = json.load(f)

    cleaned = {}
    corrupted_fixed = 0

    for latin, cands in data.items():
        latin_clean = latin.strip().lower()
        new_cands = []
        for item in cands:
            bn, score = item[0], float(item[1])
            bn_clean = sanitize_bengali_string(bn)
            if bn_clean != bn:
                corrupted_fixed += 1
            new_cands.append([bn_clean, score])
        cleaned[latin_clean] = new_cands

    # Apply curated overrides
    curated_applied = 0
    for latin, cands in CURATED_OVERRIDES.items():
        cleaned[latin] = cands
        curated_applied += 1

    # Sort each candidate list descending by score and ensure deduplication
    for latin, cands in cleaned.items():
        seen = set()
        deduped = []
        for bn, score in cands:
            if bn not in seen:
                seen.add(bn)
                deduped.append([bn, score])
        deduped.sort(key=lambda x: -x[1])
        cleaned[latin] = deduped

    # Sort dictionary keys alphabetically for deterministic binary compilation
    sorted_cleaned = {k: cleaned[k] for k in sorted(cleaned.keys())}
    print(f"✅ Processed {len(sorted_cleaned)} overrides. Fixed {corrupted_fixed} corruptions, applied {curated_applied} curated entries.")
    return sorted_cleaned

def clean_autocorrect(src_path: Path):
    with open(src_path, 'r', encoding='utf-8') as f:
        data = json.load(f)

    cleaned = {}
    fixed = 0
    for k, v in data.items():
        k_clean = k.strip()
        v_clean = sanitize_bengali_string(v.strip())
        
        # Correct known ordinal transliteration typos
        if k_clean == "2nd" and v_clean == "২ইয়":
            v_clean = "২য়"
            fixed += 1
        elif k_clean == "3rd" and v_clean == "৩ইয়":
            v_clean = "৩য়"
            fixed += 1

        if v_clean != v:
            fixed += 1
        if k_clean != v_clean:
            cleaned[k_clean] = v_clean

    # Sort keys alphabetically
    sorted_cleaned = {k: cleaned[k] for k in sorted(cleaned.keys())}
    print(f"✅ Cleaned autocorrect. Fixed {fixed} corruptions/typos, sorted {len(sorted_cleaned)} entries.")
    return sorted_cleaned

def clean_suffix(src_path: Path):
    with open(src_path, 'r', encoding='utf-8') as f:
        data = json.load(f)

    cleaned = {}
    fixed = 0
    for k, v in data.items():
        k_clean = k.strip().lower()
        v_clean = sanitize_bengali_string(v.strip())
        if v_clean != v:
            fixed += 1
        cleaned[k_clean] = v_clean

    sorted_cleaned = {k: cleaned[k] for k in sorted(cleaned.keys())}
    print(f"✅ Cleaned suffix. Fixed {fixed} corruptions, sorted {len(sorted_cleaned)} entries.")
    return sorted_cleaned

def clean_dictionary(src_path: Path):
    with open(src_path, 'r', encoding='utf-8') as f:
        data = json.load(f)

    cleaned = {}
    fixed = 0
    total = 0
    pruned = 0

    for prefix in sorted(data.keys()):
        words = data[prefix]
        new_words = []
        seen = set()
        for w in words:
            total += 1
            w_clean = sanitize_bengali_string(w.strip())
            if w_clean != w:
                fixed += 1
            # Filter non-words (URLs, abbreviations with ASCII dot, empty)
            if '.' in w_clean or not w_clean:
                pruned += 1
                continue
            if w_clean not in seen:
                seen.add(w_clean)
                new_words.append(w_clean)
        # Sort words alphabetically for canonical dictionary ordering
        new_words.sort()
        cleaned[prefix] = new_words

    print(f"✅ Cleaned dictionary. Fixed {fixed} corruptions, pruned {pruned} invalid entries out of {total} words across {len(cleaned)} phonetic prefixes.")
    return cleaned

def compile_overrides_to_binary(data: dict, bin_path: Path):
    out = bytearray()
    out.extend(b"POVR")
    out.extend(struct.pack("<I", 1)) # Version 1
    out.extend(struct.pack("<I", len(data)))

    for latin, candidates in data.items():
        latin_bytes = latin.encode('utf-8')
        out.extend(struct.pack("<H", len(latin_bytes)))
        out.extend(latin_bytes)

        out.extend(struct.pack("<H", len(candidates)))
        for item in candidates:
            bengali, conf = item[0], float(item[1])
            bengali_bytes = bengali.encode('utf-8')
            out.extend(struct.pack("<H", len(bengali_bytes)))
            out.extend(bengali_bytes)
            out.extend(struct.pack("<f", conf))

    with open(bin_path, 'wb') as f:
        f.write(out)
    print(f"✅ Compiled {len(data)} overrides -> {bin_path} ({len(out)} bytes)")

def main():
    base_android = Path("/mnt/data/lekhani-android/data/dictionaries")
    assets_android = Path("/mnt/data/lekhani-android/android/app/src/main/assets/dictionaries")
    base_linux = Path("/mnt/data/lekhani/data/dictionaries")
    base_engine = Path("/mnt/data/lekhani-engine/data/dictionaries")

    # 1. Clean phonetic_overrides
    pov_data = clean_phonetic_overrides(base_android / "phonetic_overrides.json")
    
    # 2. Clean autocorrect
    ac_data = clean_autocorrect(base_android / "autocorrect.json")

    # 3. Clean suffix
    suf_data = clean_suffix(base_android / "suffix.json")

    # 4. Clean dictionary
    dict_data = clean_dictionary(base_android / "dictionary.json")

    # Write cleaned and sorted JSON files to all locations
    for dir_path in [base_android, assets_android, base_linux, base_engine]:
        if dir_path.exists():
            with open(dir_path / "phonetic_overrides.json", 'w', encoding='utf-8') as f:
                json.dump(pov_data, f, ensure_ascii=False, indent=2)
            with open(dir_path / "autocorrect.json", 'w', encoding='utf-8') as f:
                json.dump(ac_data, f, ensure_ascii=False, indent=2)
            with open(dir_path / "suffix.json", 'w', encoding='utf-8') as f:
                json.dump(suf_data, f, ensure_ascii=False, indent=2)
            with open(dir_path / "dictionary.json", 'w', encoding='utf-8') as f:
                json.dump(dict_data, f, ensure_ascii=False)

    # Compile and sync binary overrides
    for bin_path in [
        base_android / "phonetic_overrides.bin",
        assets_android / "phonetic_overrides.bin",
        base_linux / "phonetic_overrides.bin",
        base_engine / "phonetic_overrides.bin",
    ]:
        if bin_path.parent.exists():
            compile_overrides_to_binary(pov_data, bin_path)

    # Compile and sync dictionary.bin using Rust PrefixTrie builder
    print("🚀 Rebuilding dictionary.bin across all workspaces...")
    subprocess.run(
        ["cargo", "run", "--release", "-p", "lekhani-core", "--example", "build_bengali_dict"],
        cwd="/mnt/data/lekhani-engine",
        check=True
    )

    # Run automated dataset validation
    print("🔍 Running automated integrity & ordering validator...")
    from validate_datasets import validate_datasets
    for d in [base_android, base_engine, base_linux]:
        if d.exists() and not validate_datasets(d):
            raise RuntimeError(f"Dataset validation failed for {d}!")

    print("✨ All datasets cleaned, canonically ordered, rebuilt, and validated successfully!")

if __name__ == "__main__":
    main()

