# UI Iconography & Typography Standards

## Zero Emojis in UI Chrome (Strict Vector Icons Only)

When designing, updating, or generating any user interface (Compose sheets, dialogues, tabs, category chips, candidate strip notices, toolbars, settings, activity screens):

1. **Never use unicode emojis as icons, pseudo-icons, or decorative ornaments in UI chrome**:
   - ❌ WRONG: `"Chroma 🌈"`, `"Neon ⚡"`, `"Aesthetic 🌸"`, `"Classic 🏛️"`, `"Nature 🌿"`, `"Custom 🛠️"`
   - ❌ WRONG: `showNotice("🎨 $themeName", "✨")`, `showNotice(msg, "🎙️")`
   - ✅ CORRECT: Clean text labels (`"Chroma"`, `"নিওন"`) paired with official Material 3 `ImageVector` leading icons (`Icons.Filled.Animation`, `Icons.Filled.Bolt`, `Icons.Filled.Palette`, `Icons.Filled.Mic`).

2. **Why this rule is mandatory**:
   - **Cross-Device Rendering Consistency**: Emojis render drastically differently across Android OS versions and manufacturer font sets (Samsung One UI, Xiaomi HyperOS, Google Noto Color Emoji, Vivo OriginOS).
   - **Theme & Color Adaptability**: Unicode emojis cannot dynamically inherit Material 3 container colors, alpha opacities, or theme accents (`accentColor`, `onPrimaryContainer`, `colorScheme.onSurfaceVariant`).
   - **Professional Polish**: Appending emojis to UI buttons and chips looks cheap, cluttered, and amateurish compared to clean Material 3 typography and crisp vector iconography.

3. **Permitted Emoji Usage**:
   - Unicode emojis are strictly reserved for actual user typing functionality:
     - The user-facing emoji picker (`EmojiPickerView`).
     - Emoji search dictionary and categories (`EmojiData`).
     - Kaomoji palette (`KaomojiData`).
   - They must never leak into the application chrome, settings, or notification views.
