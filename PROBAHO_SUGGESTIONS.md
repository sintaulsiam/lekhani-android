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

### 1. Geminate Double-Tap (দ্বিত্ব ব্যঞ্জন শর্টকাট) — `[COMPLETED]`
- **Problem**: Typing geminate consonants (`ক্ক`, `চ্চ`, `ত্ত`, `ন্ন`, `ব্ব`, `ম্ম`, `ল্ল`, `স্স`, `প্প`) in Bengali requires 3 distinct taps: `[Consonant] + [্] + [Consonant]`.
- **Solution**: Rapidly tapping the same consonant key twice automatically forms its geminate conjunct:
  - `ত` $\times 2 \rightarrow$ **`ত্ত`** (e.g. `উ` + `ত` + `ত` + `র` $\rightarrow$ **উত্তর**)
  - `ব` $\times 2 \rightarrow$ **`ব্ব`** (e.g. `আ` + `ব` + `ব` + `া` $\rightarrow$ **আব্বা**)
  - `প` $\times 2 \rightarrow$ **`প্প`** (e.g. `গ` + `প` + `প` $\rightarrow$ **গপ্প**)
  - `ল` $\times 2 \rightarrow$ **`ল্ল`** (e.g. `উ` + `ল` + `ল` + `া` + `স` $\rightarrow$ **উল্লাস**)
- **Impact**: Cuts keystrokes by 33% for hundreds of common Bengali words.

### 2. Extended Phola Quick-Picks on Hasanta (্ব, ্ম, ্ল) — `[COMPLETED]`
- **Problem**: Hasanta (`্`) now surfaces R-phola (`্র`) and Ya-phola (`্য`), but Ba-phola (`্ব`), Ma-phola (`্ম`), and La-phola (`্ল`) still require manual chaining.
- **Solution**: In `crates/lekhani-android/src/probaho.rs`, dynamically synthesize all valid Bengali pholas and primary conjuncts at the front of the candidate strip:
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

### 5. Multi-Directional Flick Gestures (Flick Down for Subscript Digits/Symbols) — `[COMPLETED]`
- **Solution**:
  - **Flick Up**: Shift / Aspirated partner (`ক` $\rightarrow$ `খ`, `প` $\rightarrow$ `ফ`).
  - **Flick Down**: Direct input of subscript numeric digit or secondary symbol (`1–0` or `ং`, `ঃ`, `ঁ`) without switching to `?123` panel.
  - **Outer Bezel Calibration**: Clamped and extended touch coordinates preventing edge key drops on curved displays.

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

### 27. Radial Flick Snap for Instant Conjuncts (হসন্ত জেশ্চার হুইল)
- **Concept**: Single 40ms directional flick on consonants directly produces complex conjuncts without hitting Hasanta (`ক` flick right $\rightarrow$ `ক্ষ`, `প` flick down $\rightarrow$ `প্র`, `ত` flick right $\rightarrow$ `ত্র`, `জ` flick down $\rightarrow$ `জ্ঞ`).
- **Implementation**: Directional drag thresholds in `KeyboardCanvasView.kt` mapped to a static lookup table in `lekhani-core`.

### 28. HD Haptic Grammatical Signatures (হ্যাপটিক ব্যাকরণ স্পর্শ)
- **Concept**: Unique tactile pulses for consonant taps, vowel promotions, and conjunct snaps using Android X-axis linear actuator.
- **Implementation**: Extend `LekhaniFeedbackManager.kt` with distinct vibration waveform patterns.

### 29. Dynamic Contextual Action Strip (রূপান্তরশীল ক্যান্ডিডেট স্ট্রিপ)
- **Concept**: Transform idle strip into word-level cursor scrubbers, text-selection tools, inline math evaluation, and contextual emojis.
- **Implementation**: State machine in `CandidateStripView` switching between Composing, Idle Navigation, and Math evaluation.

### 30. Elastic Scrub-to-Rewind Backspace Timeline
- **Concept**: Dragging left from backspace highlights words with mechanical ratchet clicks; sliding right restores them before thumb release, followed by an Undo pill.
- **Implementation**: Bidirectional gesture scrubber with cached pre-edit history in `KeyboardCanvasView.kt`.

---

## 📌 Implementation Checklist & Tracking

- [x] Vertical phonic column alignment (Vowels paired vertically in Row 1 & 2)
- [x] 'ম' moved to Row 1 Right for optimal thumb alternation on 'আমি', 'তুমি'
- [x] R-phola (`্র`) and Ya-phola (`্য`) quick-picks on Hasanta
- [x] Subscript shift/flick hints on keycaps with settings toggle
- [x] Preferences Tab Modernization (Live Search, Category Pills, Dedicated Layout Dialogs)
- [ ] **Next up (Probaho)**: Geminate double-tap (`ত` $\times 2 \rightarrow$ `ত্ত`, `ব` $\times 2 \rightarrow$ `ব্ব`)
- [ ] **Next up (Probaho)**: Radial Flick Snap for top conjuncts (`ক্ষ`, `জ্ঞ`, `ত্র`, `স্থ`)
- [ ] **Next up (Probaho)**: Extended pholas on Hasanta (`্ব`, `্ম`, `্ল`)
- [ ] **Next up (Probaho)**: Interactive 60-Second Onboarding Sandbox in Settings
- [ ] **Next up (Probaho)**: HD Haptic Grammatical Signatures
- [ ] **Next up (Probaho)**: Elastic Scrub-to-Rewind Backspace Timeline with Undo Pill

---

## 🏛️ Probhat (प्रभात) Software & IME Optimization Plan (Zero Layout Alterations)

### Strict Invariant
**NOT A SINGLE KEY POSITION OR SHIFT STATE WILL BE ALTERED**. Typists rely on decades of established muscle memory. All enhancements operate strictly through the IME candidate engine, pre-edit state machine, haptic drivers, and Material 3 settings chrome.

### Prioritized Probhat Superpowers

| Priority | Feature / Optimization | Description & User Experience | Status |
| :--- | :--- | :--- | :--- |
| **Tier P1** | **In-Flight Conjunct Substitution** | `শিক্` + candidate `ক্ষ` replaces `ক + ্` in the composing buffer $\rightarrow$ `শিক্ষ`, enabling the user to immediately type `া` for `শিক্ষা` without word-break interruption. | Planned / Engine |
| **Tier P1** | **Hasanta Dynamic Conjunct Quick-Picks** | Hitting `্` on Probhat presents top conjuncts (`ক্র, ক্য, ক্ত, ক্ষ, ক্ব, শ্র, দ্র, ত্র, দ্ব`) directly on the candidate strip. | Planned / Engine |
| **Tier P1** | **Dedicated Probhat Configuration Sheet** | `ProbhatSettingsDialog` accessible from the Layouts tab (`⚙️`) with toggles for conjunct quick-picks, geminate double-taps, Kar promotion, and hint visibility. | Planned / UI |
| **Tier P2** | **Smart Word-Initial Kar Auto-Promotion** | Automatically transforms lone Kars typed at the start of a word into canonical independent vowels (`া` $\rightarrow$ `আ`, `ি` $\rightarrow$ `ই`, `ু` $\rightarrow$ `উ`). Demotes full vowels preceded by a consonant (`ক` + `আ` $\rightarrow$ `কা`). | Planned / Engine |
| **Tier P2** | **Consonant Geminate Double-Tap** | Double-tapping identical consonants within 220 ms generates geminates (`ত` $\times 2 \rightarrow$ `ত্ত`, `ব` $\times 2 \rightarrow$ `ব্ব`, `ল` $\times 2 \rightarrow$ `ল্ল`, `প` $\times 2 \rightarrow$ `প্প`). | Planned / Engine |
| **Tier P3** | **Keycap Subscript Shift Hints** | Displays shifted glyphs (`ধ, ঊ, ঈ, ড়, ঠ, ঐ...`) in subtle micro-typography on the top-right corner of keycaps. Controlled by `showKeyHints`. | Available / Ready |
| **Tier P3** | **Hasanta / Dead-Key Haptic Pulse** | Crisp linear actuator tick when Hasanta is pressed, providing tactile confirmation of conjunct composition mode. | Planned / Haptics |
| **Tier P4** | **Spacebar Cursor Glide & Swipe Delete** | Spacebar scrub (`◀   প্রভাত   ▶`) and swipe-to-delete with undo delete floating badge. | Available / Ready |

