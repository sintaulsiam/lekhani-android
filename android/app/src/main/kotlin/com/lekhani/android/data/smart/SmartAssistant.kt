package com.lekhani.android.data.smart

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * SmartAssistant
 * On-device intelligence engine providing zero-network, sub-microsecond smart
 * assistance for Lekhani:
 *  - Inline arithmetic solver (e.g. "500+250=" → "750", "১২০*৫=" → "৬০০")
 *  - Dynamic Date & Time suggestions ("তারিখ", "সময়", "date", "time")
 *  - Code & token detection (URLs, mentions, camelCase, CLI flags)
 *  - Smart clipboard heuristic parsing (OTP codes, URLs, Emails, Phone numbers)
 *  - Bilingual numeral transliteration (English ↔ Bengali digits)
 */
object SmartAssistant {

    enum class QuickChipType {
        OTP,
        URL,
        EMAIL,
        PHONE,
        RECENT,
    }

    data class QuickChipInfo(
        val label: String,
        val icon: String,
        val fullText: String,
        val type: QuickChipType,
    )

    private val BENGALI_DIGITS = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
    private val BENGALI_MONTHS = arrayOf(
        "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
        "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর"
    )

    private val CODE_KEYWORDS: Set<String> = hashSetOf(
        "apt", "async", "auto", "await", "awk", "bash", "bench", "bool", "branch", "break",
        "brew", "build", "bun", "byte", "cargo", "case", "cat", "catch", "char", "checkout",
        "clang", "class", "clone", "cmake", "code", "commit", "const", "continue",
        "cpp", "crate", "curl", "debug", "def", "deno", "deploy", "diff", "dnf", "docker",
        "double", "elif", "else", "enum", "except", "export", "extern", "false", "fetch",
        "finally", "find", "float", "fn", "for", "from", "func", "function", "gcc", "git",
        "github", "gitlab", "goto", "grep", "if", "impl", "import", "include", "init", "install",
        "int", "interface", "let", "long", "loop", "make", "match", "merge",
        "module", "namespace", "nano", "nil", "node", "none", "npm", "null", "nvim", "package",
        "pip", "pnpm", "private", "protected", "pub", "public", "pull", "push",
        "python", "raise", "rebase", "release", "remote", "require", "reset", "return", "run",
        "rustc", "scp", "sed", "self", "short", "sizeof", "ssh", "stash", "status",
        "str", "string", "struct", "sudo", "super", "switch", "test", "this", "throw", "tmux",
        "trait", "true", "try", "type", "typeof", "undefined", "use", "val", "var",
        "vim", "void", "wget", "while", "yarn", "yield", "zsh"
    )

    // ── 1. Numeral Conversions ────────────────────────────────────────────────

    fun toBengaliDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (c in input) {
            if (c in '0'..'9') {
                sb.append(BENGALI_DIGITS[c - '0'])
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    fun toEnglishDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (c in input) {
            val idx = BENGALI_DIGITS.indexOf(c)
            if (idx >= 0) {
                sb.append(('0' + idx))
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    // ── 2. Inline Arithmetic Calculation ──────────────────────────────────────

    /**
     * Evaluates a math expression if present in the raw input buffer.
     * Supports both English and Bengali digits, and operators +, -, *, x, /, ÷, %.
     * Returns the formatted result, or null if not a valid math expression.
     */
    fun evaluateMath(rawInput: String): String? {
        val trimmed = rawInput.trim()
        if (trimmed.length < 3) return null

        val hasBengaliDigits = trimmed.any { it in '০'..'৯' }
        val asciiExpr = toEnglishDigits(trimmed).replace("=", "").trim()

        // Match simple arithmetic: e.g. "500+250", "120*5", "100/4", "75-25"
        val match = Regex("""^(\d+(?:\.\d+)?)\s*([+*x/÷%-])\s*(\d+(?:\.\d+)?)$""").find(asciiExpr)
            ?: return null

        val left = match.groupValues[1].toDoubleOrNull() ?: return null
        val op = match.groupValues[2]
        val right = match.groupValues[3].toDoubleOrNull() ?: return null

        val result = when (op) {
            "+" -> left + right
            "-" -> left - right
            "*", "x" -> left * right
            "/", "÷" -> if (right != 0.0) left / right else return null
            "%" -> (left * right) / 100.0
            else -> return null
        }

        // Format cleanly: avoid unnecessary trailing zeroes (.0)
        val formatted = if (result % 1.0 == 0.0 && result >= Long.MIN_VALUE && result <= Long.MAX_VALUE) {
            result.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", result).trimEnd('0').trimEnd('.')
        }

        return if (hasBengaliDigits) toBengaliDigits(formatted) else formatted
    }

    // ── 3. Dynamic Date & Time ────────────────────────────────────────────────

    fun getFormattedDate(isBengali: Boolean): String {
        val cal = Calendar.getInstance()
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val monthIdx = cal.get(Calendar.MONTH)
        val year = cal.get(Calendar.YEAR)

        return if (isBengali) {
            val bDay = toBengaliDigits(day.toString())
            val bMonth = BENGALI_MONTHS.getOrElse(monthIdx) { "" }
            val bYear = toBengaliDigits(year.toString())
            "$bDay $bMonth, $bYear"
        } else {
            val sdf = SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH)
            sdf.format(cal.time)
        }
    }

    fun getFormattedTime(isBengali: Boolean): String {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR)
        val displayHour = if (hour == 0) 12 else hour
        val min = cal.get(Calendar.MINUTE)
        val amPm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"

        val timeStr = String.format(Locale.US, "%02d:%02d %s", displayHour, min, amPm)
        return if (isBengali) toBengaliDigits(timeStr) else timeStr
    }

    fun isDateQuery(query: String): Boolean {
        val q = query.trim().lowercase()
        return q == "tarikh" || q == "তারিখ" || q == "date"
    }

    fun isTimeQuery(query: String): Boolean {
        val q = query.trim().lowercase()
        return q == "somoy" || q == "সময়" || q == "time" || q == "ghori"
    }

    // ── 4. Code & Token Shield ────────────────────────────────────────────────

    /**
     * Identifies code identifiers, CLI flags, URLs, handles and tags that
     * should bypass phonetic transliteration to prevent corrupted conjuncts.
     */
    fun isCodeToken(token: String): Boolean {
        val trimmed = token.trim()
        if (trimmed.length < 2) return false

        // 1. Mentions (@username) and Hashtags (#tag)
        if (trimmed.startsWith('@') || trimmed.startsWith('#')) {
            return true
        }

        // 2. CLI flags (--help, -rf, -v)
        if (trimmed.startsWith("--") && trimmed.length >= 3) return true
        if (trimmed.startsWith('-') && trimmed.length >= 2 && trimmed[1].isLetter()) return true

        // 3. URLs, Web Addresses & Protocols
        if (trimmed.contains("://") || trimmed.startsWith("www.") || trimmed.startsWith("localhost:")) {
            return true
        }
        val lower = trimmed.lowercase()
        if ((lower.endsWith(".com") || lower.endsWith(".org") || lower.endsWith(".net") ||
                    lower.endsWith(".io") || lower.endsWith(".dev") || lower.endsWith(".app") ||
                    lower.endsWith(".bd")) && lower.contains('.')
        ) {
            return true
        }

        // 4. Programming keywords (O(1) HashSet lookup)
        if (CODE_KEYWORDS.contains(lower)) {
            return true
        }

        // 5. snake_case / kebab-case identifiers
        if ((trimmed.contains('_') || trimmed.contains('-')) &&
            trimmed.all { it.isLetterOrDigit() || it == '_' || it == '-' } &&
            !trimmed.startsWith('_') && !trimmed.endsWith('_') &&
            !trimmed.startsWith('-') && !trimmed.endsWith('-')
        ) {
            return true
        }

        // 6. camelCase or PascalCase identifiers (e.g. "onClick", "getUser", "MyComponent")
        if (trimmed.length >= 4 && trimmed.all { it.isLetterOrDigit() }) {
            var hasLower = false
            var hasUpperAfterLower = false
            for (c in trimmed) {
                if (c.isLowerCase()) {
                    hasLower = true
                } else if (c.isUpperCase() && hasLower) {
                    hasUpperAfterLower = true
                    break
                }
            }
            if (hasUpperAfterLower) return true
        }

        return false
    }

    // ── 5. Smart Clipboard Parsing ────────────────────────────────────────────

    /**
     * Analyzes clipboard content and generates an intelligent action chip.
     */
    fun inspectClipboard(rawText: String, isEnglish: Boolean = false): QuickChipInfo? {
        val text = rawText.trim()
        if (text.isEmpty() || text.length > 500) return null

        // 1. OTP detection: 4 to 8 digits
        val otpMatch = Regex("""\b(\d{4,8})\b""").find(text)
        if (text.length <= 16 && otpMatch != null) {
            val code = otpMatch.groupValues[1]
            return QuickChipInfo(
                label = if (isEnglish) "Paste $code" else "ওটিপি $code পেস্ট",
                icon = "",
                fullText = code,
                type = QuickChipType.OTP,
            )
        }

        // 2. URL detection
        if (text.startsWith("http://") || text.startsWith("https://") || text.startsWith("www.")) {
            return QuickChipInfo(
                label = if (isEnglish) "Paste Link" else "লিংক পেস্ট করুন",
                icon = "",
                fullText = text,
                type = QuickChipType.URL,
            )
        }

        // 3. Email detection
        if (Regex("""^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$""").matches(text)) {
            return QuickChipInfo(
                label = if (isEnglish) "Paste Email" else "ইমেইল পেস্ট করুন",
                icon = "",
                fullText = text,
                type = QuickChipType.EMAIL,
            )
        }

        // 4. Phone Number detection (BD mobile e.g. 017..., 018..., +8801..., or generic)
        if (Regex("""^(\+?88)?01[3-9]\d{8}$""").matches(text) ||
            (text.startsWith("+") && text.length in 10..15 && text.drop(1).all { it.isDigit() })
        ) {
            return QuickChipInfo(
                label = if (isEnglish) "Paste Number" else "নম্বর পেস্ট করুন",
                icon = "",
                fullText = text,
                type = QuickChipType.PHONE,
            )
        }

        // 5. Short recent snippet (<= 30 characters)
        if (text.length in 1..30 && !text.contains('\n')) {
            val snippet = if (text.length > 16) "${text.take(14)}…" else text
            return QuickChipInfo(
                label = if (isEnglish) "Paste \"$snippet\"" else "পেস্ট: \"$snippet\"",
                icon = "",
                fullText = text,
                type = QuickChipType.RECENT,
            )
        }

        return null
    }
}
