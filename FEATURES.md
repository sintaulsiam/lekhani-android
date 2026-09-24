# Lekhani Android: Complete Feature Specification

## 1. Input Layouts
- **Avro Phonetic (Classic Muscle Memory)**: 100% faithful transliteration (`ami` -> `আমি`, `shikkhok` -> `শিক্ষক`, `brriShTi` -> `বৃষ্টি`).
- **Fixed National (জাতীয়)**: Official standard Bangladeshi layout with illuminated Shift & AltGr key states.
- **Fixed Probhat (प्रभात)**: Popular phonetic fixed layout with dead-key combinations.
- **Dedicated Number & Punctuation Row**: Bengali numerals (০-৯) and special Bengali punctuation (Dari `।`, Taka `৳`, double dari `॥`).

## 2. Intelligence & Candidate Strip
- **Sub-Millisecond Candidate Generation**: Instant 6-candidate ranking strip.
- **Contextual Homophone Disambiguation**: Resolves *পড়া* vs *পরা*, *খাব* vs *যাব*, *বাংলা* vs *বাঙলা* based on preceding words.
- **One-Tap Spacebar Commitment**: Center-pinned primary candidate committed instantly with Spacebar tap.
- **Next-Word Continuations**: Multi-token predictions displayed immediately after space.
- **Colloquial Spoken Bengali Suffix Peeling**: Seamlessly handles modern dialects (`kortesi`, `jaitasi`, `khaitam`).

## 3. Ergonomics & Touch Controls
- **Swipe-to-Delete**: Slide left on the Backspace key to erase whole words.
- **Spacebar Cursor Control**: Slide left/right on Spacebar to position cursor precisely between letters.
- **One-Handed Mode**: Pin keyboard to left or right screen edge with quick-toggle arrow.
- **Adjustable Keyboard Height**: Custom slider (Short, Normal, Tall, Extra Tall).
- **Haptic Click Physics**: Subtle tactile haptic response tuned for low latency via Android `VibratorManager`.

## 4. Privacy & Offline Guarantee
- **Zero Internet Permission (`android.permission.INTERNET` omitted from manifest)**.
- **Zero Telemetry**: No analytics, no crash reporters phoning home, no keystroke recording.
- **100% On-Device Personalization**: User dictionary and frequency adjustments stay encrypted on local flash storage.
- **Incognito Mode Support**: Automatically disables learning in private browser tabs and password fields.

## 5. Themes & Personalization
- **Material You Dynamic Color**: Keyboard dynamically takes accents from the user's Android wallpaper.
- **Deep OLED Pure Black**: Conserves battery on modern AMOLED screens.
- **Classic Avro Blue**: The iconic nostalgic blue theme loved by millions.
- **Cyber Indigo & Neon**: Modern vibrant dark aesthetic.
