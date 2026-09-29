//! 100% Offline English Typing, Suggestion, and QWERTY Proximity Autocorrect Engine
//!
//! Features:
//! - Sub-millisecond prefix completions from compact binary PrefixTrie (~60k words)
//! - QWERTY adjacency-aware typo auto-correction (fat-finger distance 1)
//! - Casing preservation (lowercase, Titlecase, UPPERCASE)
//! - Conversational next-word predictive bigrams

use std::sync::OnceLock;
use lekhani_core::trie::PrefixTrie;

static ENGLISH_TRIE: OnceLock<PrefixTrie> = OnceLock::new();
static ENGLISH_LM: OnceLock<lekhani_ai::LanguageModel> = OnceLock::new();

/// Explicitly loads an English PrefixTrie from a binary dictionary file path.
pub fn load_english_dictionary_from_path(path: &str) -> bool {
    if ENGLISH_TRIE.get().is_some() {
        return true;
    }
    if let Ok(bytes) = std::fs::read(path) {
        if let Ok(trie) = PrefixTrie::from_binary(&bytes) {
            let _ = ENGLISH_TRIE.set(trie);
            return true;
        }
    }
    false
}

/// Explicitly loads an English Language Model from a binary file path.
pub fn load_english_lm_from_path(path: &str) -> bool {
    if ENGLISH_LM.get().is_some() {
        return true;
    }
    let mut lm = lekhani_ai::LanguageModel::new();
    if lm.load_binary_file(std::path::Path::new(path)).is_ok() {
        let _ = ENGLISH_LM.set(lm);
        return true;
    }
    false
}

/// Returns a reference to the global memory-resident English Language Model, if loaded.
pub fn get_english_lm() -> Option<&'static lekhani_ai::LanguageModel> {
    if let Some(lm) = ENGLISH_LM.get() {
        return Some(lm);
    }
    let candidate_dirs = [
        std::path::Path::new("/data/user_de/0/com.lekhani.android/files/dictionaries"),
        std::path::Path::new("/data/user_de/0/com.lekhani.android.debug/files/dictionaries"),
        std::path::Path::new("/data/user/0/com.lekhani.android/files/dictionaries"),
        std::path::Path::new("/data/user/0/com.lekhani.android.debug/files/dictionaries"),
        std::path::Path::new("/data/data/com.lekhani.android/files/dictionaries"),
        std::path::Path::new("/data/data/com.lekhani.android.debug/files/dictionaries"),
        std::path::Path::new("./data/dictionaries"),
        std::path::Path::new("../data/dictionaries"),
        std::path::Path::new("../../data/dictionaries"),
    ];
    for dir in candidate_dirs {
        let bin_path = dir.join("english_lm.bin");
        if bin_path.exists() {
            let mut lm = lekhani_ai::LanguageModel::new();
            if lm.load_binary_file(&bin_path).is_ok() {
                let _ = ENGLISH_LM.set(lm);
                return ENGLISH_LM.get();
            }
        }
    }
    None
}

/// Returns a reference to the global memory-resident English PrefixTrie, if loaded.
pub fn get_english_trie() -> Option<&'static PrefixTrie> {
    if let Some(trie) = ENGLISH_TRIE.get() {
        return Some(trie);
    }
    let candidate_dirs = [
        std::path::Path::new("/data/user_de/0/com.lekhani.android/files/dictionaries"),
        std::path::Path::new("/data/user/0/com.lekhani.android/files/dictionaries"),
        std::path::Path::new("/data/data/com.lekhani.android/files/dictionaries"),
        std::path::Path::new("./data/dictionaries"),
        std::path::Path::new("../data/dictionaries"),
        std::path::Path::new("../../data/dictionaries"),
    ];
    for dir in candidate_dirs {
        let bin_path = dir.join("english_dict.bin");
        if bin_path.exists() {
            if let Ok(bytes) = std::fs::read(&bin_path) {
                if let Ok(trie) = PrefixTrie::from_binary(&bytes) {
                    let _ = ENGLISH_TRIE.set(trie);
                    return ENGLISH_TRIE.get();
                }
            }
        }
    }
    None
}

/// Allows injecting an English trie instance (e.g. for isolated testing).
#[cfg(test)]
pub fn set_test_english_trie(trie: PrefixTrie) {
    let _ = ENGLISH_TRIE.set(trie);
}

/// Adjacent keys on standard QWERTY layout for fast proximity distance calculation.
pub fn get_qwerty_adjacent_keys(c: char) -> &'static [char] {
    match c.to_ascii_lowercase() {
        'q' => &['w', 'a', 's'],
        'w' => &['q', 'e', 'a', 's', 'd'],
        'e' => &['w', 'r', 's', 'd', 'f'],
        'r' => &['e', 't', 'd', 'f', 'g'],
        't' => &['r', 'y', 'f', 'g', 'h'],
        'y' => &['t', 'u', 'g', 'h', 'j'],
        'u' => &['y', 'i', 'h', 'j', 'k'],
        'i' => &['u', 'o', 'j', 'k', 'l'],
        'o' => &['i', 'p', 'k', 'l'],
        'p' => &['o', 'l'],
        'a' => &['q', 'w', 's', 'z'],
        's' => &['w', 'e', 'a', 'd', 'z', 'x'],
        'd' => &['e', 'r', 's', 'f', 'x', 'c'],
        'f' => &['r', 't', 'd', 'g', 'c', 'v'],
        'g' => &['t', 'y', 'f', 'h', 'v', 'b'],
        'h' => &['y', 'u', 'g', 'j', 'b', 'n'],
        'j' => &['u', 'i', 'h', 'k', 'n', 'm'],
        'k' => &['i', 'o', 'j', 'l', 'm'],
        'l' => &['o', 'p', 'k'],
        'z' => &['a', 's', 'x'],
        'x' => &['z', 's', 'd', 'c'],
        'c' => &['x', 'd', 'f', 'v'],
        'v' => &['c', 'f', 'g', 'b'],
        'b' => &['v', 'g', 'h', 'n'],
        'n' => &['b', 'h', 'j', 'm'],
        'm' => &['n', 'j', 'k'],
        _ => &[],
    }
}

/// Matches the casing pattern of `source` and applies it to `target`.
pub fn match_casing(source: &str, target: &str) -> String {
    if source.is_empty() || target.is_empty() {
        return target.to_string();
    }
    let is_all_upper = source.chars().all(|c| !c.is_alphabetic() || c.is_uppercase());
    if is_all_upper && source.len() > 1 {
        return target.to_uppercase();
    }
    let starts_upper = source.chars().next().is_some_and(|c| c.is_uppercase());
    if starts_upper {
        let mut chars = target.chars();
        match chars.next() {
            None => String::new(),
            Some(first) => first.to_uppercase().collect::<String>() + chars.as_str(),
        }
    } else {
        target.to_lowercase()
    }
}

pub const FALLBACK_ENGLISH_WORDS: &[&str] = &[
    "the", "be", "to", "of", "and", "a", "in", "that", "have", "i", "it", "for", "not", "on", "with",
    "he", "as", "you", "do", "at", "this", "but", "his", "by", "from", "they", "we", "say", "her",
    "she", "or", "an", "will", "my", "one", "all", "would", "there", "their", "what", "so", "up",
    "out", "if", "about", "who", "get", "which", "go", "me", "when", "make", "can", "like", "time",
    "no", "just", "him", "know", "take", "people", "into", "year", "your", "good", "some", "could",
    "them", "see", "other", "than", "then", "now", "look", "only", "come", "its", "over", "think",
    "also", "back", "after", "use", "two", "how", "our", "work", "first", "well", "way", "even",
    "new", "want", "because", "any", "these", "give", "day", "most", "us", "hello", "help", "here",
    "home", "house", "hand", "high", "hold", "hope", "hard", "head", "hear", "heart", "happy", "great",
    "world", "where", "while", "water", "word", "write", "without", "before", "right", "still", "small",
    "should", "number", "system", "tell", "same", "place", "point", "program", "play", "please", "part",
    "problem", "question", "power", "person", "phone", "post", "page", "put", "public", "present",
    "read", "really", "reason", "run", "remember", "result", "school", "state", "study", "student",
    "something", "start", "set", "show", "side", "seem", "service", "stand", "story", "sure", "talk",
    "today", "together", "try", "thing", "think", "turn", "under", "understand", "until", "value",
    "very", "view", "voice", "wait", "walk", "watch", "week", "woman", "work", "yes", "young",
];

/// Generates candidate words for the typed English buffer:
/// Returns candidate words for an active English composing buffer.
/// Ranking strategy:
/// 1. Verbatim buffer (at index 0)
/// 2. Prefix completions from PrefixTrie
/// 3. Typo auto-corrections via QWERTY proximity if buffer has few completions
pub fn get_english_candidates(buffer: &str, limit: usize) -> Vec<String> {
    get_english_candidates_with_trie(buffer, get_english_trie(), limit)
}

/// Computes candidates using an explicitly provided PrefixTrie reference.
pub fn get_english_candidates_with_trie(buffer: &str, trie: Option<&PrefixTrie>, limit: usize) -> Vec<String> {
    if buffer.is_empty() {
        return Vec::new();
    }

    let mut results: Vec<String> = Vec::with_capacity(limit + 2);
    // Verbatim word is always candidate 0 (supports Option B conservative commit)
    results.push(buffer.to_string());

    let lower_buffer = buffer.to_lowercase();

    if let Some(trie) = trie {
        // 1. Prefix completions
        let prefix_entries = trie.find_prefix_entries(&lower_buffer, limit + 2);
        for (cand, _) in prefix_entries {
            let formatted = match_casing(buffer, cand);
            if !results.iter().any(|r| r.eq_ignore_ascii_case(&formatted)) {
                results.push(formatted);
            }
            if results.len() >= limit {
                break;
            }
        }

        // 2. Proximity auto-correction if few prefix matches were found and word is >= 3 chars
        if results.len() <= 2 && lower_buffer.len() >= 3 {
            let corrections = generate_qwerty_corrections(&lower_buffer, trie);
            for corr in corrections {
                let formatted = match_casing(buffer, &corr);
                if !results.iter().any(|r| r.eq_ignore_ascii_case(&formatted)) {
                    results.push(formatted);
                }
                if results.len() >= limit {
                    break;
                }
            }
        }
    } else {
        // Instant static fallback if trie is still loading into memory
        for &word in FALLBACK_ENGLISH_WORDS {
            if word.starts_with(&lower_buffer) {
                let formatted = match_casing(buffer, word);
                if !results.iter().any(|r| r.eq_ignore_ascii_case(&formatted)) {
                    results.push(formatted);
                }
                if results.len() >= limit {
                    break;
                }
            }
        }
    }

    results.into_iter().take(limit).collect()
}

/// Generates single-edit distance corrections using keyboard adjacency.
fn generate_qwerty_corrections(word: &str, trie: &PrefixTrie) -> Vec<String> {
    let chars: Vec<char> = word.chars().collect();
    let len = chars.len();
    let mut candidates: Vec<(String, u32)> = Vec::new();

    // 1. Transposition of adjacent characters (e.g. "thgat" -> "tgat", "taht" -> "that")
    for i in 0..len.saturating_sub(1) {
        let mut swapped = chars.clone();
        swapped.swap(i, i + 1);
        let cand: String = swapped.into_iter().collect();
        if let Some((_, freq)) = trie.get_exact(&cand) {
            candidates.push((cand, freq));
        }
    }

    // 2. Single-letter deletion (e.g. "thgat" with accidental 'g' -> "that")
    if len >= 4 {
        for i in 0..len {
            let mut del = chars.clone();
            del.remove(i);
            let cand: String = del.into_iter().collect();
            if let Some((_, freq)) = trie.get_exact(&cand) {
                candidates.push((cand, freq));
            }
        }
    }

    // 3. Substitution of QWERTY adjacent characters (e.g. "hellp" with 'p' next to 'o' -> "hello")
    for (i, &ch) in chars.iter().enumerate() {
        for &adj in get_qwerty_adjacent_keys(ch) {
            let mut sub = chars.clone();
            sub[i] = adj;
            let cand: String = sub.into_iter().collect();
            if let Some((_, freq)) = trie.get_exact(&cand) {
                candidates.push((cand, freq));
            }
        }
    }

    // Sort by frequency descending
    candidates.sort_unstable_by_key(|a| std::cmp::Reverse(a.1));
    candidates.dedup_by(|a, b| a.0 == b.0);
    candidates.into_iter().map(|(w, _)| w).take(4).collect()
}

/// Curated conversational English bigram phrases for immediate next-word suggestions.
pub const ENGLISH_PREDICTIVE_PAIRS: &[(&[&str], &[&str])] = &[
    (&["how"], &["are", "is", "about", "can", "do"]),
    (&["how", "are"], &["you", "things", "you doing"]),
    (&["thank"], &["you", "you so much"]),
    (&["thanks"], &["for", "a lot", "everyone"]),
    (&["what"], &["is", "are", "do", "about", "happened"]),
    (&["what", "is"], &["your", "the", "that", "this"]),
    (&["i"], &["am", "will", "have", "would", "think", "can", "want"]),
    (&["i", "am"], &["fine", "doing", "working", "happy", "sorry"]),
    (&["i", "have"], &["been", "a", "no", "to", "done"]),
    (&["you"], &["are", "can", "have", "will", "know", "need"]),
    (&["let"], &["me", "us", "it", "them"]),
    (&["nice"], &["to", "meeting", "day", "work"]),
    (&["good"], &["morning", "afternoon", "evening", "night", "luck", "job"]),
    (&["see"], &["you", "later", "soon"]),
    (&["take"], &["care", "time", "your time"]),
    (&["looking"], &["forward", "for", "at"]),
    (&["please"], &["let", "find", "check", "help", "let me know"]),
    (&["have"], &["a", "been", "to", "you"]),
    (&["do"], &["not", "you", "it"]),
    (&["it"], &["is", "was", "will", "would"]),
    (&["there"], &["is", "are", "was", "were"]),
    (&["where"], &["are", "is", "were"]),
    (&["who"], &["is", "are", "was"]),
    (&["why"], &["is", "are", "did", "not"]),
];

/// Returns next-word predictions given preceding sentence tokens.
pub fn get_english_next_words(context: &[&str], limit: usize) -> Vec<String> {
    if context.is_empty() {
        return vec!["I", "The", "How", "Thank", "What"]
            .into_iter()
            .take(limit)
            .map(|s| s.to_string())
            .collect();
    }

    let mut matches: Vec<String> = Vec::with_capacity(limit);

    // Normalize context to lowercase for pattern matching
    let lower_context: Vec<String> = context.iter().map(|s| s.to_lowercase()).collect();
    let lower_slices: Vec<&str> = lower_context.iter().map(|s| s.as_str()).collect();

    // 1. High-confidence conversational idioms/pairs
    for &(pattern, continuations) in ENGLISH_PREDICTIVE_PAIRS {
        if lower_slices.len() >= pattern.len() {
            let tail = &lower_slices[lower_slices.len() - pattern.len()..];
            if tail == pattern {
                for &cont in continuations {
                    if !matches.iter().any(|m| m.eq_ignore_ascii_case(cont)) {
                        matches.push(cont.to_string());
                    }
                }
            }
        }
    }

    // 2. Query English Language Model (LLM3 N-gram)
    if let Some(lm) = get_english_lm() {
        if lower_slices.len() >= 2 {
            let prev2 = lower_slices[lower_slices.len() - 2];
            let prev1 = lower_slices[lower_slices.len() - 1];
            for w in lm.get_next_words_trigram(prev2, prev1, limit) {
                if !matches.iter().any(|m| m.eq_ignore_ascii_case(&w)) {
                    matches.push(w);
                }
            }
            for w in lm.get_next_words(prev1, limit) {
                if !matches.iter().any(|m| m.eq_ignore_ascii_case(&w)) {
                    matches.push(w);
                }
            }
        } else if !lower_slices.is_empty() {
            let prev1 = lower_slices[lower_slices.len() - 1];
            for w in lm.get_next_words(prev1, limit) {
                if !matches.iter().any(|m| m.eq_ignore_ascii_case(&w)) {
                    matches.push(w);
                }
            }
        }
    }

    if matches.is_empty() {
        matches = vec!["the", "and", "to", "of", "a"]
            .into_iter()
            .map(|s| s.to_string())
            .collect();
    }

    matches.into_iter().take(limit).collect()
}

/// Decodes a swipe gesture (sequence of visited keys) into high-probability English dictionary words.
/// Uses start/end key bounding, QWERTY proximity tolerance, in-order subsequence verification,
/// and trie frequency weighting.
pub fn decode_english_glide(keys: &[String], limit: usize) -> Vec<String> {
    decode_english_glide_with_trie(keys, get_english_trie(), limit)
}

pub fn decode_english_glide_with_trie(
    keys: &[String],
    trie: Option<&PrefixTrie>,
    limit: usize,
) -> Vec<String> {
    if keys.len() < 2 {
        return Vec::new();
    }

    let path_chars: Vec<char> = keys
        .iter()
        .filter_map(|k| k.chars().next().map(|c| c.to_ascii_lowercase()))
        .collect();

    if path_chars.len() < 2 {
        return Vec::new();
    }

    let start_char = path_chars[0];
    let end_char = *path_chars.last().unwrap();

    let mut start_keys = vec![start_char];
    for &adj in get_qwerty_adjacent_keys(start_char) {
        if !start_keys.contains(&adj) {
            start_keys.push(adj);
        }
    }

    let mut end_keys = vec![end_char];
    for &adj in get_qwerty_adjacent_keys(end_char) {
        if !end_keys.contains(&adj) {
            end_keys.push(adj);
        }
    }

    let mut scored_candidates: Vec<(String, i64)> = Vec::new();

    if let Some(trie) = trie {
        for &sk in &start_keys {
            let prefix = sk.to_string();
            let entries = trie.find_prefix_entries(&prefix, 400);
            for (word, freq) in entries {
                if word.len() < 2 || word.len() > path_chars.len() + 3 {
                    continue;
                }
                let last_c = match word.chars().last() {
                    Some(c) => c.to_ascii_lowercase(),
                    None => continue,
                };
                if !end_keys.contains(&last_c) {
                    continue;
                }
                if matches_glide_subsequence(word, &path_chars) {
                    let mut score = freq as i64;
                    if word.starts_with(start_char) {
                        score += 5000;
                    }
                    if last_c == end_char {
                        score += 5000;
                    }
                    let len_diff = (word.len() as isize - path_chars.len() as isize).abs();
                    score -= (len_diff as i64) * 40;

                    if !scored_candidates.iter().any(|(w, _)| w == word) {
                        scored_candidates.push((word.to_string(), score));
                    }
                }
            }
        }
    }

    scored_candidates.sort_unstable_by_key(|a| std::cmp::Reverse(a.1));
    scored_candidates.into_iter().take(limit).map(|(w, _)| w).collect()
}

/// Checks whether `word` can be traced as an in-order subsequence along `path_chars`,
/// accommodating consecutive repeated characters (e.g., 'll' in "hello").
fn matches_glide_subsequence(word: &str, path_chars: &[char]) -> bool {
    let mut path_idx = 0;
    let mut prev_char = '\0';
    for ch in word.chars() {
        let lower = ch.to_ascii_lowercase();
        if lower == prev_char {
            continue;
        }
        prev_char = lower;
        let mut matched = false;
        while path_idx < path_chars.len() {
            if path_chars[path_idx] == lower {
                path_idx += 1;
                matched = true;
                break;
            }
            path_idx += 1;
        }
        if !matched {
            return false;
        }
    }
    true
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sample_trie() -> PrefixTrie {
        let mut trie = PrefixTrie::new();
        trie.insert_bulk_weighted(vec![
            ("the".to_string(), 100000),
            ("that".to_string(), 90000),
            ("than".to_string(), 85000),
            ("thanks".to_string(), 80000),
            ("thank".to_string(), 75000),
            ("hello".to_string(), 70000),
            ("help".to_string(), 65000),
            ("held".to_string(), 60000),
            ("world".to_string(), 55000),
            ("word".to_string(), 50000),
        ]);
        trie.ensure_sorted();
        trie
    }

    #[test]
    fn test_casing_preservation() {
        assert_eq!(match_casing("hello", "world"), "world");
        assert_eq!(match_casing("Hello", "world"), "World");
        assert_eq!(match_casing("HELLO", "world"), "WORLD");
    }

    #[test]
    fn test_prefix_candidates() {
        let trie = sample_trie();
        let cands = get_english_candidates_with_trie("th", Some(&trie), 5);
        assert_eq!(cands[0], "th"); // verbatim buffer
        assert!(cands.contains(&"the".to_string()));
        assert!(cands.contains(&"that".to_string()));
    }

    #[test]
    fn test_qwerty_proximity_autocorrect() {
        let trie = sample_trie();
        // Typo: "hellp" instead of "hello" ('p' adjacent to 'o')
        let cands = get_english_candidates_with_trie("hellp", Some(&trie), 5);
        assert_eq!(cands[0], "hellp"); // verbatim
        assert!(cands.contains(&"hello".to_string())); // corrected!
    }

    #[test]
    fn test_transposition_autocorrect() {
        let trie = sample_trie();
        // Typo: "taht" instead of "that"
        let cands = get_english_candidates_with_trie("taht", Some(&trie), 5);
        assert_eq!(cands[0], "taht");
        assert!(cands.contains(&"that".to_string()));
    }

    #[test]
    fn test_next_word_predictions() {
        let next1 = get_english_next_words(&["how", "are"], 3);
        assert!(next1.contains(&"you".to_string()));

        let next2 = get_english_next_words(&["thank"], 3);
        assert!(next2.contains(&"you".to_string()));
    }

    #[test]
    fn test_glide_decoding_subsequence() {
        let trie = sample_trie();
        // User swiped across: h -> j -> u -> i -> e -> r -> l -> o (for "hello")
        let gesture = vec![
            "h".to_string(), "j".to_string(), "e".to_string(),
            "r".to_string(), "l".to_string(), "o".to_string(),
        ];
        let cands = decode_english_glide_with_trie(&gesture, Some(&trie), 5);
        assert!(!cands.is_empty());
        assert_eq!(cands[0], "hello");

        // Gesture: w -> e -> r -> t -> y -> u -> i -> o -> r -> l -> d (for "world")
        let gesture_world = vec![
            "w".to_string(), "e".to_string(), "o".to_string(),
            "r".to_string(), "l".to_string(), "d".to_string(),
        ];
        let cands_world = decode_english_glide_with_trie(&gesture_world, Some(&trie), 5);
        assert!(!cands_world.is_empty());
        assert_eq!(cands_world[0], "world");
    }

    #[test]
    fn test_english_lm_predictions() {
        if let Some(lm) = get_english_lm() {
            println!("Loaded English LM unigrams: {}, bigrams: {}, trigrams: {}", lm.unigram_count(), lm.bigram_count(), lm.trigram_count());
            let next = get_english_next_words(&["how", "are"], 5);
            println!("ENGLISH LM 'how are' -> {:?}", next);
            assert!(next.contains(&"you".to_string()));
        }
    }
}
