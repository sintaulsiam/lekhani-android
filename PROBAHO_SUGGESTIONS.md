# Lekhani প্রবাহ (Flow) — Ranked Improvement Suggestions

This document tracks and categorizes all proposed enhancements, architectural optimizations, and UX features for the **Lekhani প্রবাহ (Flow)** keyboard layout and IME engine, prioritized by impact, feasibility, and user benefit.

---

## 🏆 Priority Matrix Overview

| Tier | Priority Level | Description | Focus |
| :--- | :--- | :--- | :--- |
| **Tier 1** | **Critical / Immediate Impact** | High-frequency typing accelerators, zero cognitive friction, essential learnability. | Pholas, Geminates, Trainer, Touch Flicks. |
| **Tier 2** | **High Priority** | Smart linguistic prediction, typo resilience, and daily productivity boosters. | Grammar suffixes, Emoji prediction, Homophones. |
| **Tier 3** | **Medium Priority** | Ergonomic tailoring, form factor adaptations, visual refinement. | Thumb arc, Foldables, Dialects, Contrast. |
| **Tier 4** | **Future Polish & Deep Tech** | Low-level SIMD optimizations, power tools, niche utilities. | Zero-copy UniFFI, In-line math, ASR ring buffer. |

---

## 🔴 Tier 1: Critical & Immediate High-Impact (Game Changers)

### 1. Geminate Double-Tap (দ্বিত্ব ব্যঞ্জন শর্টকাট)
- **Problem**: Typing geminate consonants (`ক্ক`, `চ্চ`, `ত্ত`, `ন্ন`, `ব্ব`, `ম্ম`, `ল্ল`, `স্স`, `প্প`) in Bengali requires 3 distinct taps: `[Consonant] + [্] + [Consonant]`.
- **Solution**: Rapidly tapping the same consonant key twice within 220 ms automatically forms its geminate conjunct:
  - `ত` $\times 2 \rightarrow$ **`ত্ত`** (e.g. `উ` + `ত` + `ত` + `র` $\rightarrow$ **উত্তর**)
  - `ব` $\times 2 \rightarrow$ **`ব্ব`** (e.g. `আ` + `ব` + `ব` + `া` $\rightarrow$ **আব্বা**)
  - `প` $\times 2 \rightarrow$ **`প্প`** (e.g. `গ` + `প` + `প` $\rightarrow$ **গপ্প**)
  - `ল` $\times 2 \rightarrow$ **`ল্ল`** (e.g. `উ` + `ল` + `ল` + `া` + `স` $\rightarrow$ **উল্লাস**)
- **Impact**: Cuts keystrokes by 33% for hundreds of common Bengali words.

### 2. Extended Phola Quick-Picks on Hasanta (্ব, ্ম, ্ল)
- **Problem**: Hasanta (`্`) now surfaces R-phola (`্র`) and Ya-phola (`্য`), but Ba-phola (`্ব`), Ma-phola (`্ম`), and La-phola (`্ল`) still require manual chaining.
- **Solution**: In `crates/lekhani-android/src/probaho.rs`, dynamically synthesize all valid Bengali pholas at the front of the candidate strip:
  - `শ` + `্` $\rightarrow$ `[ শ্র, শ্য, শ্ব, শ্ল ]` (for `শ্বাস`, `ঈশ্বর`)
  - `দ` + `্` $\rightarrow$ `[ দ্র, দ্য, দ্ব, দ্ধ ]` (for `দ্বিতীয়`, `দ্বার`)
  - `ত` + `্` $\rightarrow$ `[ ত্র, ত্য, ত্ব, ত্ম ]` (for `আত্মা`, `মহত্ত্ব`)
  - `হ` + `্` $\rightarrow$ `[ হৃ, হ্র, হ্য, হ্ব, হ্ন, হ্ম ]` (for `হৃদয়`, `চিহ্ন`, `ব্রাহ্মণ`)

### 3. Interactive "60-Second Probaho Sandbox" (ইন্টারেক্টিভ ট্রেইনার)
- **Problem**: Users are hesitant to try a new layout without immediate confidence.
- **Solution**: A gamified 60-second micro-trainer embedded in Settings (`PreferencesTabContent.kt`):
  - Shows animated glowing target keys for 5 high-frequency words: `আমি`, `বাংলাদেশ`, `তুমি`, `প্রথম`, `ধন্যবাদ`.
  - Proves the Left (Vowel) $\leftrightarrow$ Right (Consonant) alternation in under 1 minute.
  - Generates instant confidence and viral word-of-mouth adoption.

### 4. Vowel Inversion Auto-Correction (পড়ার ক্রম স্বরবর্ণ সংশোধন)
- **Problem**: In Bengali handwriting and reading, short-I (`ি`) and E-kar (`ে`) appear visually *before* the consonant, so fast typists often inadvertently tap `ি` then `ক` instead of `ক` then `ি`.
- **Solution**: If a Kar is typed immediately preceding a consonant (`ি + ক`), the engine automatically transposes them to valid Unicode canonical order (`ক + ি` $\rightarrow$ `কি`).

### 5. Multi-Directional 4-Way Flick Gestures
- **Problem**: Currently only Swipe-Up (Shift) is enabled.
- **Solution**:
  - **Flick Up**: Shift / Aspirated partner (`ক` $\rightarrow$ `খ`, `প` $\rightarrow$ `ফ`).
  - **Flick Down**: Numeric digit or diacritic (`1–0` or `ং`, `ঃ`, `ঁ`).
  - **Flick Left**: Delete preceding character.
  - **Flick Right**: Commit top candidate / insert space.

### 6. TalkBack Phonetic Bengali Accessibility Disambiguation
- **Problem**: Screen readers announce homophonous letters identically ("ন" and "ণ", "শ" and "ষ" and "স").
- **Solution**: Enrich `contentDesc` with standard Bengali colloquial identifiers:
  - `ন` $\rightarrow$ "দন্ত্য-ন (নদী)"
  - `ণ` $\rightarrow$ "মূর্ধন্য-ণ (বাণী)"
  - `শ` $\rightarrow$ "তালব্য-শ (শাপলা)"
  - `স` $\rightarrow$ "দন্ত্য-স (সূর্য)"
  - `ষ` $\rightarrow$ "পেট-কাটা মূর্ধন্য-ষ (ষাঁড়)"
  - `ড়` $\rightarrow$ "বিন্দুযুক্ত ড়"
  - `ঢ়` $\rightarrow$ "ঢ-এ বিন্দু ঢ়"

---

## 🟡 Tier 2: High Priority (Productivity & Speed Multipliers)

### 7. Agglutinative Suffix Predictions (বিভক্তি ও প্রত্যয় ইঞ্জিন)
- **Problem**: Bengali is heavily agglutinative. After typing a base noun, typists repeatedly type suffixes.
- **Solution**: Immediately after committing a word (e.g. `মানুষ`, `দেশ`, `বই`), surface common inflectional suffixes in the candidate strip:
  - `মানুষ` $\rightarrow$ `[ মানুষের, মানুষকে, মানুষগুলো, মানুষটির ]`
  - `দেশ` $\rightarrow$ `[ দেশের, দেশে, দেশকে, দেশপ্রেম ]`

### 8. Contextual Emoji & Kaomoji Prediction in Candidate Strip
- **Problem**: Switching to the emoji palette breaks typing rhythm.
- **Solution**: Map high-frequency Bengali emotion and reaction tokens to emojis in the candidate strip:
  - `ধন্যবাদ` $\rightarrow$ 🙏
  - `ভালোবাসা` $\rightarrow$ ❤️
  - `হাসি` $\rightarrow$ 😂
  - `বৃষ্টি` $\rightarrow$ 🌧️
  - `চা` $\rightarrow$ ☕
  - `বাংলাদেশ` $\rightarrow$ 🇧🇩

### 9. Contextual Homophone Disambiguation for Probaho
- **Problem**: Even on fixed layouts, words with identical sounds need contextual disambiguation (`কী` vs `কি`, `হলো` vs `হল`, `বেশ` vs `বিষ`, `যাওয়া` vs `যাওয়া`).
- **Solution**: Route completed tokens through `lekhani-ai` tri-gram scorer to automatically boost the contextually appropriate form.

### 10. Spacebar Swipe-Up for Instant Alphanumeric Toggle
- **Problem**: Typing a quick English word, code identifier, or URL while typing Bengali requires tapping the globe key twice.
- **Solution**: Flicking upward on the spacebar provides an instant transient English QWERTY keyboard that automatically returns to Probaho upon pressing space or punctuation.

### 11. Backspace Swipe Velocity & Undo Delete Floating Pill
- **Problem**: Dragging backspace can accidentally erase too many words without an easy recovery mechanism.
- **Solution**: Add a 3.5-second transient "পূর্বাবস্থায় ফেরান (Undo)" pill on the candidate strip whenever 3+ words are deleted in a single gesture.

### 12. Outer Key Fat-Finger Hitbox Calibration
- **Problem**: Keys on the outer perimeter (`য`, `ল`, `⇧`, `⌫`) suffer from modern bezel edge-rejection algorithms on Android devices with curved edges or gesture navigation.
- **Solution**: Extend the invisible touch bounding boxes by 4dp into the horizontal margins in `KeyboardCanvasView.computeKeyBounds()`.

### 13. ZWNJ / ZWJ Quick-Access on Hasanta Long-Press
- **Problem**: Typing standalone pholas (`‍্য`), Vedic mantras, or preventing unwanted conjunct formation in loanwords (e.g. `কর্পোরেশন` vs `কর্পোরেশন`).
- **Solution**: Long-pressing Hasanta (`্`) reveals Zero-Width Non-Joiner (`\u200C`) and Zero-Width Joiner (`\u200D`).

---

## 🟢 Tier 3: Medium Priority (Ergonomics, Polish & Form Factors)

### 14. Dynamic Ergonomic Radial Thumb Arc (বাঁকানো থাম্ব আর্চ)
- **Concept**: Thumbs rotate radially from the base of the hand. Flat rows require stretching the thumb joint.
- **Implementation**: Provide an optional layout setting where key columns curve upward toward the left and right edges, mirroring natural thumb biomechanics.

### 15. Split / Ergo-Gap Mode for Foldables & Tablets
- **Concept**: On wide displays (>400dp), reaching the center column strains thumbs.
- **Implementation**: Separate the left and right realms with an expandable central utility channel containing clipboard shortcuts, arrow keys, and punctuation.

### 16. Smart OTP & Bengali Number Recognition in Clipboard Bar
- **Concept**: Copied SMS OTPs and phone numbers often arrive in mixed English or Bengali digits.
- **Implementation**: Detect OTP patterns and offer a single-tap paste button that optionally auto-converts digits to Bengali (`০-৯`) or English (`0-9`) based on user preference.

### 17. WCAG 2.1 Dynamic Subscript Contrast Auto-Tuning
- **Concept**: Subscript shift hints must remain legible across all 40+ built-in themes.
- **Implementation**: Calculate color luminance contrast in `KeyboardCanvasView.applyTheme()`; if contrast falls below 3.0:1, automatically step up tint brightness.

### 18. Fluid M3 Spring-Physics Popup Menu Animations
- **Concept**: Secondary long-press popups currently appear instantaneously.
- **Implementation**: Animate popup expansion with Material 3 expressive spring physics (0.88 $\rightarrow$ 1.00 scale) without memory allocations in `onDraw()`.

### 19. Dialect & Regional Register Packs (কথ্য ও আঞ্চলিক ভাষা)
- **Concept**: Daily chat uses regional colloquialisms (`খাইসি`, `গেসি`, `করসি`, `কই গেলা`).
- **Implementation**: Engine vocabulary toggles in `lekhani-ai` for formal (সাধু/চলিত) vs colloquial messaging registers.

### 20. Text Expander & Custom Snippets Engine
- **Concept**: Repetitive typing of addresses, emails, or greetings.
- **Implementation**: Allow users to register custom shortcuts (e.g., `!thx` $\rightarrow$ `আপনাকে অনেক ধন্যবাদ`, `!mail` $\rightarrow$ user email).

### 21. Auto-Demotion of Incompatible Double Kars
- **Concept**: Typing `া` followed by `ি` by mistake creates invalid stacked diacritics.
- **Implementation**: Engine automatically replaces the previous Kar if an incompatible Kar is typed on the same base consonant.

---

## 🔵 Tier 4: Future Polish, Deep Tech & Architecture

### 22. UniFFI Direct Primitive Memory Buffers for Pre-edit
- **Optimization**: Eliminate JNI String object allocations when communicating pre-edit shadow buffers between Rust and Kotlin.
- **Implementation**: Use pre-allocated direct `ByteBuffer` slices.

### 23. SIMD-Accelerated Levenshtein / Damerau Distance
- **Optimization**: Accelerate candidate ranking across large vocabulary dictionaries.
- **Implementation**: Use ARM NEON vector intrinsics for string distance calculations.

### 24. Zero-Copy Streaming Audio Record for Offline Voice ASR
- **Optimization**: Ensure offline Sherpa/Vosk speech recognition strictly respects the < 95 MB peak memory budget.
- **Implementation**: Circular memory-mapped buffer with SIMD silence truncation.

### 25. In-Line Math & Currency Converter
- **Feature**: Typing mathematical expressions (e.g., `২৫০ * ৪ =`) or currency queries (e.g., `USD to BDT`) surfaces the calculated answer directly on the candidate strip.

### 26. Mirrored Left-Handed One-Thumb Mode
- **Feature**: Swap the Left (Vowel) and Right (Consonant) realms for typists who type predominantly with their left thumb.

---

## 📌 Implementation Checklist & Tracking

- [x] Vertical phonic column alignment (Vowels paired vertically in Row 1 & 2)
- [x] 'ম' moved to Row 1 Right for optimal thumb alternation on 'আমি', 'তুমি'
- [x] R-phola (`্র`) and Ya-phola (`্য`) quick-picks on Hasanta
- [x] Subscript shift/flick hints on keycaps with settings toggle
- [ ] **Next up**: Geminate double-tap (`ত` $\times 2 \rightarrow$ `ত্ত`)
- [ ] **Next up**: Extended pholas (`্ব`, `্ম`, `্ল`)
- [ ] **Next up**: Interactive 60-Second Onboarding Sandbox in Settings
