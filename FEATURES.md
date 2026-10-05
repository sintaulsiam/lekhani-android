# Lekhani Android: Complete Feature Specification

## 1. Input Layouts & Switching
- **Lekhani প্রবাহ (Flow) 2.0**: Scientifically engineered custom layout for ultra-fast two-thumb mobile typing. Left-thumb vowel zone with complete independent vowel pairing on Shift (`ই`, `উ`, `এ`, `ও`, `আ`), right-thumb consonant engine with Golden 5 home row (`র`, `ত`, `ন`, `স`, `ক`), zero-allocation swipe-up flick gestures for instant shifted character entry without pressing Shift, real-time in-flight conjunct substitution on Hasanta (`্`), bilateral thumb aura visual guidance, smart word-start Kar auto-promotion with micro-haptics, and instant spacebar-row punctuation (`?` on `,` and `!` on `।`).
- **Avro Phonetic (Classic Muscle Memory)**: 100% faithful transliteration (`ami` -> `আমি`, `shikkhok` -> `শিক্ষক`, `brriShTi` -> `বৃষ্টি`).
- **Fixed National (জাতীয়)**: Official standard Bangladeshi layout with illuminated Shift & AltGr key states.
- **Fixed Probhat (प्रभात)**: Popular phonetic fixed layout with dead-key combinations.
- **Fixed Gboard-style Layout**: Standard Google Gboard Bengali layout with full modifier coverage (Hasanta `্`, Anusvara `ং`, Visarga `ঃ`, Chandrabindu `ঁ`) and dynamic Kar/Phala morphing.
- **English (QWERTY)**: Clean bilingual typing experience.
- **Dedicated Number & Symbol Panels**:
  - **Adaptive Number & PIN Pad (`TYPE_CLASS_NUMBER` / `TYPE_CLASS_DATETIME`)**: Automatic 3×4 large-target keypad with decimal point, comma, minus, slash, and instant `ABC` / `১২৩` toggles for bank OTPs, amounts, and dates.
  - **Adaptive Phone Dialpad (`TYPE_CLASS_PHONE`)**: Ergonomic 3×4 phone dialpad with Latin letters, `*`, `0/+`, `#`, pause/wait punctuation, and direct backspace.
  - **?123 Numeric Layer**: Full 1..0 row with native Bengali numeral hints (`১..০`), Bengali currency (`৳`), math operators, and common punctuation.
  - **=\\< More Symbols Layer**: Extended brackets, mathematical signs, currency (`৳`, `€`, `¥`, `£`, `₹`), and typographical glyphs.
  - **Quick Digit Toggle (`১২৩` / `123`)**: Seamless 1-tap toggle between Bengali numerals and Western Arabic digits across all number layers.
- **Layout Toggle & Dedicated Layout Flow Screen**:
  - Individual toggle switches in Settings to enable/disable any layout, with minimum layout protection.
  - Dedicated **Layout Flow** screen (`LayoutFlowScreen.kt`) featuring a forward-curved cylindrical 3D carousel with active `Current layout` speech bubble pill and pagination dots.
  - De-cluttered layout ordering list using intuitive relative directional offset badges (`Home`, `1 ▸`, `◂ 1`, `2 ▸`) reflecting the true horizontal loop mental model.
  - Drag-to-reorder layout priority with spring animations, tactile haptic feedback, and one-tap default reset.
- **Seamless Switching Controls**: Quick toggle via Globe key (🌐), spacebar horizontal swipe with subtle chevron cues (`‹   English   ›`), or long-press spacebar layout menu.
- **External Hardware Keyboard**: Intercepts USB & Bluetooth keyboards on tablets and Android desktop (DeX), mapping them directly to Avro or National typing.

## 2. Intelligence & Candidate Strip
- **Sub-Millisecond Candidate Generation**: Instant 6-candidate ranking strip.
- **Contextual Homophone Disambiguation**: Resolves *পড়া* vs *পরা*, *খাব* vs *যাব*, *বাংলা* vs *বাঙলা* based on preceding words.
- **One-Tap Spacebar Commitment**: Center-pinned primary candidate committed instantly with Spacebar tap.
- **Next-Word Continuations**: Multi-token predictions displayed immediately after space.
- **Colloquial Spoken Bengali Suffix Peeling**: Seamlessly handles modern dialects (`kortesi`, `jaitasi`, `khaitam`).
- **Candidate Blacklisting**: Long-press any candidate in the strip to remove unwanted typos or suggestions from memory.
- **100% Offline English Suggestions & Autocorrect**:
  - Compact ~60,000-word binary PrefixTrie (< 1 MB on disk) with sub-millisecond lookups.
  - QWERTY adjacency fat-finger correction (distance 1 proximity + letter transpositions).
  - Casing preservation (handles lowercase, Titlecase, and ALL-CAPS acronyms).
  - **Option B Conservative Spacebar**: Spacebar commits typed characters verbatim without force-swapping words unless a candidate pill is explicitly tapped.
  - Conversational next-word bigram predictive pairs.

## 3. Bengali Script Precision & Complex Text
- **Dedicated ZWJ (`\u200D`) and ZWNJ (`\u200C`) Keys**: Clean, accessible control over explicit Hasanta, Khanda-Ta (`ৎ`), and Ya-phala (`্য`).
- **Conjunct-Aware Backspace**: Deletes complex conjuncts (e.g. `ক্ষ`, `জ্ঞ`) cleanly as a grapheme unit or stepwise based on preference.
- **Unicode NFC Normalization**: Ensures every committed character is canonical Unicode before reaching the target app.
- **Surrounding Text Awareness**: Inspects text before the cursor to provide relevant predictions even when editing in the middle of a sentence.

## 4. Emoji, Kaomoji, Symbols & Clipboard
- **Unicode 15.1+ Emoji Suite**: Full categorization (Smileys, People, Nature, Food, Travel, Activities, Objects, Symbols, Flags).
- **Overhauled 4-Row High-Density Palette**:
  - Expanded viewport to 184 dp displaying 4 full rows of 40 dp emoji cells (+137% density increase over legacy 2-row layouts).
  - Slim 32 dp category header with compact pills.
  - On-demand search: search input field and bilingual recommendation chips toggle via `[ 🔍 ]` search button.
  - Ergonomic 44 dp bottom navigation bar: `[ ⌨ ABC ]`, on-canvas search toggle, wide `[ ── Space ── ]`, and repeating hold `[ ⌫ ]`.
- **Instant Live Bilingual Search**: Real-time search in both Bengali (e.g., "হাসি", "ভালোবাসা", "আগুন") and English ("laugh", "love", "fire") with quick recommendation chips (`🔥 আগুন`, `❤️ প্রেম`, `😂 হাসি`, etc.) and instant clear button.
- **Recents & Favorites Shelf**: Quick access to frequently used emojis with local persistence.
- **Persistent Skin Tone & Diverse Modifiers**: Long-press on person/hand emojis for skin-tone and gender selection; selected default skin tone is saved in Device Protected Storage and automatically applied across all grids and search queries.
- **Kaomoji & Emoticons Picker**: Expressive text emoticons (`(◕‿◕)`, `¯\_(ツ)_/¯`, etc.).
- **Smart Local Clipboard & Vault**:
  - Dedicated primary navigation tab in settings and quick-access hero card on Home.
  - Keyboard clipboard sheet with inline clip editor dialog, "+ New Clip" modal, and direct "Editor" jump button.
  - Clip pinning, duplicate key protection, and auto-purging of sensitive passwords.

## 5. 100% Local / On-Device Voice Typing
- **Zero-Cloud Offline Speech Recognition**: Bengali and English voice typing powered entirely on-device (via embedded local ASR like `sherpa-onnx` / `vosk-android`).
- **Punctuation Auto-Restoration**: Automatically injects Bengali punctuation (`।`, `,`, `?`) based on speech pauses.
- **Low-Latency Streaming**: Visual sound-wave feedback with instant token streaming directly to `InputConnection`.
- **Absolute Privacy**: Does not require or use internet access. Audio never leaves the phone.
- **Voice Activity Detection (VAD)**: Automatic silence detection halts recording after 1.5 seconds of quiet.

## 6. Glide & Gesture Typing (Swipe-to-Type)
- **Continuous Gesture Input**: Trace across keys to compose words fluently without lifting a finger.
- **Spatial Path Decoder**: High-accuracy path recognition across both Bengali and English layouts.
- **Smooth Accent Trail**: Elegant visual trace line with theme-adaptive color glow.

## 7. Ergonomics, Touch Controls & UI/UX
- **Material 3 Expressive UI**: 120 FPS hardware canvas touch grid with responsive tactile feedback.
- **Dedicated Text & Cursor Editor Sheet**:
  - Full 4-way D-Pad (Up, Down, Left, Right) for precise single-character or line navigation.
  - Home and End quick jump controls (`|◀`, `▶|`).
  - Selection mode toggle for highlighting text ranges via arrow keys.
  - Select All, Cut, Copy, Paste, and Backspace toolbar integration.
  - Ensures complete cursor navigation capability even when the spacebar is set to Layout Switch mode.
- **Selection-Aware Backspace**: Pressing Backspace when text is highlighted immediately deletes the entire active selection across all apps.
- **Continuous Backspace Repeat**: Holding Backspace initiates rapid, continuous deletion without needing repetitive taps.
- **Multi-Touch Thumb Tracking**: Multi-pointer aware touch engine (`event.actionIndex`) guarantees zero dropped keystrokes during rapid two-thumb alternating typing.
- **Character Key Long-Press Hints**: Long-pressing any character key instantly triggers its hint (digits `1..0`, alternate consonants like `ষ`, `ঢ়`, and punctuation).
- **Swipe-to-Delete**: Slide left on the Backspace key to erase whole words with preview highlight.
- **Spacebar Cursor Control**: Slide left/right on Spacebar to position cursor precisely between letters when in Cursor Slide mode.
- **One-Handed Mode**: Quick-dock keyboard to left or right screen edge with quick-toggle arrows.
- **Split & Floating Modes**: Optimized split keyboard for foldables/tablets and freely movable floating window.
- **Dedicated Top Number Row**: Optional permanent 10-key row (`১..০` / `1..0`) displayed above the keyboard for instant digit entry without switching layers.
- **National (জাতীয়) Layout BBS Dead-Key Linker**: Official BBS typewriter standard supporting `[্ + Kar]` to produce independent vowels (`অ`, `ই`, `ঈ`, `উ`, `ঊ`, `ঋ`, `এ`, `ঐ`, `ও`, `ঔ`), plus instant long-press vowel shortcuts.
- **Extended Bengali Typographical Glyphs**: Native Double Dari (`॥`), curly quotation marks (`“`, `”`), ellipsis (`…`), and currency numerator (`৲`).
- **Haptic Click Physics**: Subtle tactile haptic response tuned for low latency via Android `VibratorManager`.

## 8. Deep Customization Suite
- **Dimension Controls**: Granular sliders for keyboard height, row spacing, horizontal key margins, and bottom gesture chin adjustment.
- **Key Visuals**: Toggle key borders, elevation drop-shadows, and customize key font size.
- **Font Selection**: Choose preferred Bengali font rendering (Kalpurush, SolaimanLipi, System default).
- **Sound Packs**: Choose between Classic Keypress, Modern Bubble, Mechanical Click, Typewriter, Soft Woodblock, or Mute with dedicated volume slider.
- **Haptic Strength**: Fine-tune vibration duration and amplitude curve to match personal tactile preference.
- **Tool Vault & Interactive Customization**:
  - Clean, uncluttered toolbar defaults showcasing primary tools (Settings, Theme, Clipboard, Emoji) plus the Tool Vault entry icon (`GridView`).
  - Secondary/unimportant tools (Local Voice Mic, Height Resize, Text Editor, One-Handed, Floating, Split) neatly tucked inside the **Tool Vault** drawer.
  - Interactive Customize Mode inside the vault: tap `+` to bring tools to the toolbar, `-` to return them to the vault, and reorder positions live with Left/Right and Up/Down controls.
  - StateFlow reactive sync updating the active toolbar instantaneously upon customization.

## 9. Themes & Personalization
- **Decoupled App vs Keyboard Theme**: Control the settings app appearance (`System Default`, `Force Light`, `Force Dark`, `Dynamic Material You`, or `Match Keyboard Theme`) independently from the active keyboard layout theme.
- **33 Curated Aesthetic & Dynamic Keyboard Presets**:
  - **11 Dynamic RGB Chroma (120 FPS Real-time Flow)**:
    - *RGB Chroma Flow* (Continuous 360° chromatic wave)
    - *Aurora Borealis* (Emerald teal -> cyan -> polar violet wave)
    - *Sunset Horizon* (Golden amber -> fiery coral -> dusk magenta wave)
    - *Cosmic Nebula* (Deep indigo -> laser violet -> hot pink wave)
    - *Matrix Pulse* (Phosphor lime -> cyber aqua wave)
    - *Ocean Abyss* (Bioluminescent deep sapphire -> cyan -> aquamarine wave)
    - *Vaporwave Dream* (Electric cyan -> laser violet -> hot magenta wave)
    - *Sakura Flow* (Delicate pastel cherry blossom -> rose quartz -> peach shimmer)
    - *Magma Ember* (Volcanic molten ruby -> fiery blood orange -> gold ember)
    - *Celestial Amethyst* (Mystic orchid -> shimmering lilac -> starlight rose)
    - *Enchanted Jade* (Phosphorescent firefly lime -> lush woodland jade)
  - **10 Aesthetic & Pastel Presets**:
    - *Sakura Blossom*, *Lavender Haze*, *Matcha Mint*, *Peach Sorbet*, *Nordic Frost*, *Mocha Latte*, *Dusk Rose*, *Midnight Botanical*, *Champagne Luxury*, *Ice Crystal*
  - **6 Neon & Cyber Presets**:
    - *Cyberpunk Neon*, *Matrix Terminal*, *Tokyo Midnight*, *Solar Flare*, *Synthwave '84*, *Deep Violet*
  - **4 Classic & Modern Presets**:
    - *Flow Teal*, *OLED Pure Black*, *Classic Blue*, *Daylight Paper*
  - **2 High Contrast & Nature Presets**:
    - *High Contrast Yellow*, *Forest Emerald*
  - **Material You Dynamic Color**: Android 12+ wallpaper-adaptive system color extraction.
- **Custom Theme Studio & Chroma Engine**:
  - Full creation, duplication, editing, and deletion workflow for user-defined themes.
  - Interactive mini-keyboard preview canvas dynamically rendering live 120 FPS Chroma animations as options are adjusted.
  - Custom RGB Dynamic Chroma builder: toggle dynamic Chroma on any user-created theme and pick from any of the 11 chromatic wave patterns.
  - Dynamic candidate strip illumination: synchronized chromatic border and candidate highlights.
  - Precise hex and curated palette swatches for Background, Key Background, Text, Accent, Candidate Strip, and Key Borders.
  - Built-in WCAG 2.1 AA luminance contrast checker warning users when foreground and background combinations drop below 4.5:1 ratio.
  - Zero-cloud local persistence via JSON in Device Protected Storage.
- **Custom Wallpaper Themes**: Set custom background pictures or gradients with customizable blur and opacity overlays.

## 10. User Data Freedom, Backup & Migration
- **One-Click Import**: Easily import custom dictionaries and learned words from Ridmik Keyboard (`.txt` / backup), desktop Avro, and CSV wordlists.
- **Unified On-Device Backup & Restore System**:
  - Full-screen Material 3 bottom-sheet interface (`BackupRestoreSheet`) accessible via Preferences and Settings.
  - 100% offline, zero-network architecture adhering strictly to local Device Protected Storage.
  - Granular multi-component selection:
    - **Vocabulary & Bigram Memory**: All custom added words, frequency graph, and learned N-gram associations.
    - **Text Expansion Shortcuts**: Custom autocorrect trigger-to-replacement rules.
    - **Preferences**: Layout assignments, display scales, sound/haptic settings, and active themes.
    - **Pinned Clipboard Vault**: Starred snippets and immutable clipboard clippings.
  - **Storage Access Framework (SAF) & Intent Sharing**:
    - One-tap export to any local folder or SD card via `CreateDocument("application/json")`.
    - Direct share via `Intent.ACTION_SEND` to send backup files securely to other devices or cloud vaults.
  - **Inspection Preview & Safe Merge Restore**:
    - Instant schema inspection before committing changes, showing exact word counts, rules, and export timestamps.
    - Non-destructive merging: imports and blends new vocabulary and bigrams into existing typing memory without wiping existing data.
- **User Dictionary Editor**: Direct in-app interface to browse, add, edit, or purge learned words and autocorrect rules.

## 11. Security, Direct Boot & System Integration
- **Direct Boot Ready (`directBootAware="true"`)**: Full functionality on the device lockscreen immediately after reboot before decryption.
- **Password & Incognito Auto-Switch**: Instantly drops to English QWERTY on password fields; halts all learning and clipboard snooping.
- **Non-Intrusive Landscape View**: Prevents full-screen extract takeover (`onEvaluateFullscreenMode() -> false`), keeping content visible.
- **Zero Internet Permission**: Manifest completely omits `android.permission.INTERNET`.

## 12. Onboarding & Accessibility
- **Friendly 3-Step Setup Wizard**: Clean, non-intimidating setup flow — (1) enable Lekhani in System Settings, (2) set as default keyboard, (3) interactive typing playground — all deep-linked for zero friction.
- **TalkBack & Screen Reader Accessibility**: Meets WCAG 2.1 accessibility standards with phonetic Bengali pronunciation readouts.
