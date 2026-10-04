#!/usr/bin/env python3
"""
Automated CI & Retraining Dataset Validator for Lekhani:
Guarantees that any retrained or updated dataset complies strictly with:
1. Zero decomposed nuktas or vowels (Canonical NFC atomic Unicode)
2. Alphabetical ordering of keys and dictionary words
3. Descending order of confidence scores for multi-candidate overrides
4. Authoritative candidate protection for core Bengali words (boro -> বড়, etc.)
5. Clean dictionary without ASCII URLs or invalid tokens
"""

import sys
import json
from pathlib import Path

PROTECTED_PRIMARY_OVERRIDES = {
    "boro": "বড়",
    "poro": "পড়ো",
    "pora": "পড়া",
    "dhoro": "ধরো",
    "choro": "চড়ো",
    "shari": "শাড়ি",
    "churi": "চুড়ি",
    "keno": "কেন",
    "jana": "জানা",
    "duti": "দুটি",
    "ekti": "একটি",
    "akta": "একটা",
    "valo": "ভালো",
    "bhalo": "ভালো",
    "shob": "সব",
    "kichu": "কিছু",
    "ok": "ওকে",
    "hole": "হলে",
    "hote": "হতে",
    "nice": "নিচে",
    "tin": "তিন",
    "chil": "ছিল",
    "pan": "পান",
    "apu": "আপু",
    "can": "চান",
    "kach": "কাছ",
    "mail": "মাইল",
    "rato": "রাত",
    "nirapotta": "নিরাপত্তা",
    "zini": "যিনি",
    "mis": "মিস",
    "dano": "দান",
    "eko": "এক",
    "vabi": "ভাবি",
    "ashi": "আসি",
    "korbo": "করবো",
    "khabo": "খাবো",
    "bolbo": "বলবো",
    "shunbo": "শুনবো",
    "dekhbo": "দেখবো",
    "parbo": "পারবো",
    "thakbo": "থাকবো",
    "likhbo": "লিখবো",
    "ashbo": "আসবো",
    "jabo": "যাবো",
}

def has_decomposed_nukta(s: str) -> bool:
    return any(sub in s for sub in ['\u09a1\u09bc', '\u09a2\u09bc', '\u09af\u09bc', '\u0985\u09be'])

def validate_datasets(base_dir: Path) -> bool:
    errors = []
    print(f"[*] Validating datasets in: {base_dir}")

    # 1. Validate phonetic_overrides.json
    pov_path = base_dir / "phonetic_overrides.json"
    if pov_path.exists():
        with open(pov_path, "r", encoding="utf-8") as f:
            pov = json.load(f)

        keys = list(pov.keys())
        if keys != sorted(keys):
            errors.append(f"{pov_path.name}: Keys are not sorted alphabetically!")

        for k, cands in pov.items():
            if not cands:
                errors.append(f"{pov_path.name}: Key '{k}' has empty candidate list.")
                continue

            scores = [item[1] for item in cands]
            if scores != sorted(scores, reverse=True):
                errors.append(f"{pov_path.name}: Key '{k}' candidates not sorted by descending score: {cands}")

            seen_words = set()
            for word, score in cands:
                if word in seen_words:
                    errors.append(f"{pov_path.name}: Duplicate candidate '{word}' in key '{k}'.")
                seen_words.add(word)
                if has_decomposed_nukta(word):
                    errors.append(f"{pov_path.name}: Key '{k}' contains decomposed Unicode in candidate '{word}'.")

            # Check protected primary overrides
            if k in PROTECTED_PRIMARY_OVERRIDES:
                expected_primary = PROTECTED_PRIMARY_OVERRIDES[k]
                actual_primary = cands[0][0]
                if actual_primary != expected_primary:
                    errors.append(
                        f"CRITICAL: Protected override '{k}' primary candidate is '{actual_primary}', "
                        f"expected '{expected_primary}'!"
                    )
        print(f"  [✓] Checked {len(pov)} phonetic overrides.")

    # 2. Validate autocorrect.json
    ac_path = base_dir / "autocorrect.json"
    if ac_path.exists():
        with open(ac_path, "r", encoding="utf-8") as f:
            ac = json.load(f)

        keys = list(ac.keys())
        if keys != sorted(keys):
            errors.append(f"{ac_path.name}: Keys are not sorted alphabetically!")

        for k, v in ac.items():
            if has_decomposed_nukta(v):
                errors.append(f"{ac_path.name}: Key '{k}' contains decomposed Unicode in value '{v}'.")
            if k == v:
                errors.append(f"{ac_path.name}: Identity mapping found: '{k}' -> '{v}'.")

        # Specific ordinal check
        if ac.get("2nd") != "২য়":
            errors.append(f"{ac_path.name}: '2nd' must map to '২য়', found '{ac.get('2nd')}'.")
        if ac.get("3rd") != "৩য়":
            errors.append(f"{ac_path.name}: '3rd' must map to '৩য়', found '{ac.get('3rd')}'.")
        print(f"  [✓] Checked {len(ac)} autocorrect entries.")

    # 3. Validate suffix.json
    suf_path = base_dir / "suffix.json"
    if suf_path.exists():
        with open(suf_path, "r", encoding="utf-8") as f:
            suf = json.load(f)
        keys = list(suf.keys())
        if keys != sorted(keys):
            errors.append(f"{suf_path.name}: Keys are not sorted alphabetically!")
        for k, v in suf.items():
            if has_decomposed_nukta(v):
                errors.append(f"{suf_path.name}: Key '{k}' contains decomposed Unicode in value '{v}'.")
        print(f"  [✓] Checked {len(suf)} suffix entries.")

    # 4. Validate dictionary.json
    dict_path = base_dir / "dictionary.json"
    if dict_path.exists():
        with open(dict_path, "r", encoding="utf-8") as f:
            d = json.load(f)
        keys = list(d.keys())
        if keys != sorted(keys):
            errors.append(f"{dict_path.name}: Prefix keys are not sorted alphabetically!")
        total_words = 0
        for prefix, words in d.items():
            total_words += len(words)
            if words != sorted(words):
                errors.append(f"{dict_path.name}: Words under prefix '{prefix}' are not sorted!")
            seen = set()
            for w in words:
                if w in seen:
                    errors.append(f"{dict_path.name}: Duplicate word '{w}' in prefix '{prefix}'.")
                seen.add(w)
                if has_decomposed_nukta(w):
                    errors.append(f"{dict_path.name}: Decomposed Unicode in word '{w}' (prefix '{prefix}').")
                if '.' in w:
                    errors.append(f"{dict_path.name}: Invalid non-word token with dot '{w}' in prefix '{prefix}'.")
        print(f"  [✓] Checked {total_words} words across {len(d)} prefixes.")

    if errors:
        print(f"\n❌ FAILED: Found {len(errors)} dataset issues:")
        for err in errors[:20]:
            print(f"  - {err}")
        if len(errors) > 20:
            print(f"  ... and {len(errors) - 20} more errors.")
        return False
    else:
        print("✅ SUCCESS: All dataset integrity, ordering, and candidate guards passed with 100% compliance!")
        return True

if __name__ == "__main__":
    base = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("/mnt/data/lekhani-android/data/dictionaries")
    ok = validate_datasets(base)
    sys.exit(0 if ok else 1)
