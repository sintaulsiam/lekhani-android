use lekhani_core::phonetic::PhoneticDatabase;
use std::sync::{Arc, Mutex, OnceLock, RwLock};

static CORE_DB: RwLock<Option<Arc<PhoneticDatabase>>> = RwLock::new(None);
static CUSTOM_DICT_DIR: RwLock<Option<String>> = RwLock::new(None);
static CONTEXT_SCORER: RwLock<Option<Arc<lekhani_ai::ContextScorer>>> = RwLock::new(None);
static NEXT_WORD_PREDICTOR: RwLock<Option<Arc<lekhani_ai::NextWordPredictor>>> = RwLock::new(None);
static NEURAL_PREDICTOR: RwLock<Option<Arc<lekhani_neural::NeuralContextPredictor>>> =
    RwLock::new(None);

/// Returns the ordered list of candidate dictionary directories to probe.
///
/// Covers the standard Android data-partition paths for both release and debug
/// package IDs, the direct-boot device-protected variant, and relative test
/// paths used in unit tests. Centralised here so changes need only happen once.
fn candidate_dict_dirs() -> &'static [&'static std::path::Path] {
    use std::path::Path;
    static DIRS: OnceLock<Box<[&'static std::path::Path]>> = OnceLock::new();
    DIRS.get_or_init(|| {
        let v: Vec<&'static std::path::Path> = vec![
            Path::new("/data/data/com.lekhani.android/files/dictionaries"),
            Path::new("/data/data/com.lekhani.android.debug/files/dictionaries"),
            Path::new("/data/user/0/com.lekhani.android/files/dictionaries"),
            Path::new("/data/user/0/com.lekhani.android.debug/files/dictionaries"),
            Path::new("/data/user_de/0/com.lekhani.android/files/dictionaries"),
            Path::new("/data/user_de/0/com.lekhani.android.debug/files/dictionaries"),
            Path::new("./data/dictionaries"),
            Path::new("../data/dictionaries"),
            Path::new("../../data/dictionaries"),
        ];
        v.into_boxed_slice()
    })
}

/// Sets a custom dictionary directory dynamically from Android application context.
#[uniffi::export]
pub fn set_dictionary_directory(path: String) {
    let dir = std::path::PathBuf::from(&path);
    if let Ok(mut lock) = CUSTOM_DICT_DIR.write() {
        if lock.as_deref() == Some(path.as_str())
            && CORE_DB.read().ok().is_some_and(|g| g.is_some())
        {
            return;
        }
        *lock = Some(path.clone());
    }
    if dir.exists() {
        // Also eagerly load English dictionary & LM if present
        let eng_dict = dir.join("english_dict.bin");
        if eng_dict.is_file() {
            if let Some(p) = eng_dict.to_str() {
                crate::english::load_english_dictionary_from_path(p);
            }
        }
        let eng_lm = dir.join("english_lm.bin");
        if eng_lm.is_file() {
            if let Some(p) = eng_lm.to_str() {
                crate::english::load_english_lm_from_path(p);
            }
        }

        let mut db = PhoneticDatabase::new();
        if dir.join("dictionary.bin").is_file() {
            let _ = db.load_from_dir_with_dict(&dir, "dictionary.bin");
        } else {
            let _ = db.load_from_dir(&dir);
        }
        db.generation
            .fetch_add(1, std::sync::atomic::Ordering::Relaxed);
        let arc_db = Arc::new(db);
        if let Ok(mut lock) = CORE_DB.write() {
            *lock = Some(Arc::clone(&arc_db));
        }

        let mut lm = lekhani_ai::LanguageModel::new();
        let lm_path = dir.join("bengali_lm.bin");
        if lm_path.is_file() && lm.load_binary_file(&lm_path).is_ok() {
            let arc_scorer = Arc::new(lekhani_ai::ContextScorer::with_language_model(lm.clone()));
            if let Ok(mut lock) = CONTEXT_SCORER.write() {
                *lock = Some(Arc::clone(&arc_scorer));
            }
            let arc_pred = Arc::new(lekhani_ai::NextWordPredictor::with_language_model(lm));
            if let Ok(mut lock) = NEXT_WORD_PREDICTOR.write() {
                *lock = Some(Arc::clone(&arc_pred));
            }

            if let Some(sugg_mutex) = PHONETIC_SUGGESTION.get() {
                if let Ok(mut sugg) = sugg_mutex.lock() {
                    sugg.database = (*arc_db).clone();
                    sugg.ai_context = (*arc_scorer).clone();
                    sugg.ai_predictor = (*arc_pred).clone();
                    sugg.load_seq2seq_from_dir(&dir);
                    if let Some(parent) = dir.parent() {
                        let layout_path = parent.join("layouts/avrophonetic.json");
                        if layout_path.is_file() {
                            if let Ok(content) = std::fs::read_to_string(&layout_path) {
                                if let Ok(json) = serde_json::from_str(&content) {
                                    sugg.set_layout(&json);
                                }
                            }
                        }
                    }
                }
            }
        } else if let Some(sugg_mutex) = PHONETIC_SUGGESTION.get() {
            if let Ok(mut sugg) = sugg_mutex.lock() {
                sugg.database = (*arc_db).clone();
                sugg.load_seq2seq_from_dir(&dir);
            }
        }

        if let Ok(mut lock) = NEURAL_PREDICTOR.write() {
            *lock = None;
        }
    }
}

static LEARNER_AUTOSAVE_PATH: Mutex<Option<String>> = Mutex::new(None);

/// Sets an explicit file path for auto-saving learner data on finish/destroy.
#[uniffi::export]
pub fn set_learner_autosave_path(path: String) {
    if let Ok(mut lock) = LEARNER_AUTOSAVE_PATH.lock() {
        *lock = Some(path);
    }
}

/// Clear all candidate selection overrides and selection counts from persistent learner
#[uniffi::export]
pub fn clear_candidate_memory() {
    let db = get_core_database();
    if let Ok(mut learner) = db.learner.write() {
        learner.candidate_memory.clear();
        learner.candidate_selection_counts.clear();
        learner.input_error_map.clear();
        learner.input_error_counts.clear();
        learner.dirty = true;
    };
}

/// Phase 4 — Online Perceptron Feedback Loop.
///
/// Called by Kotlin's `onCandidateSelected()` when the user taps a non-top candidate
/// (i.e. `selectedIndex > 0`). Uses the `RankFeatures` stored from the most recent
/// `suggest_with_multi_context()` call to perform a single online perceptron weight
/// update: features of the chosen candidate are up-weighted, features of the top
/// (rejected) candidate are down-weighted.
///
/// After ~10–20 explicit corrections the ranking weights converge to the user's
/// personal homophone preferences — 100% on-device, persisted in `user_learned.bin`.
///
/// # Arguments
/// * `chosen_index`   — strip index the user actually tapped (>0 to have any effect)
/// * `rejected_index` — strip index of the candidate that was demoted (typically 0)
/// * `eta`            — learning rate; pass 0 to use the default safe rate (20.0)
#[uniffi::export]
pub fn update_ranking_from_selection(chosen_index: u32, rejected_index: u32, eta: f32) {
    if chosen_index == rejected_index {
        return;
    }
    let sugg_mutex = get_phonetic_suggestion();
    let features = {
        let sugg = match sugg_mutex.lock() {
            Ok(g) => g,
            Err(p) => p.into_inner(),
        };
        sugg.get_last_computed_features().to_vec()
    };
    let chosen = features.get(chosen_index as usize).copied();
    let rejected = features.get(rejected_index as usize).copied();
    if let (Some(c), Some(r)) = (chosen, rejected) {
        let db = get_core_database();
        if let Ok(mut learner) = db.learner.write() {
            let effective_eta = if eta <= 0.0 { 20.0 } else { eta };
            learner.rank_weights.update_online(&c, &r, effective_eta);
            learner.dirty = true;
        };
    }
}

pub fn get_custom_dict_dir() -> Option<String> {
    CUSTOM_DICT_DIR.read().ok().and_then(|lock| lock.clone())
}

pub fn get_core_database() -> Arc<PhoneticDatabase> {
    if let Ok(guard) = CORE_DB.read() {
        if let Some(db) = guard.as_ref() {
            return Arc::clone(db);
        }
    }
    let mut guard = CORE_DB.write().unwrap_or_else(|e| e.into_inner());
    if let Some(db) = guard.as_ref() {
        return Arc::clone(db);
    }
    let mut db = PhoneticDatabase::new();
    let mut loaded = false;
    if let Some(custom_dir) = get_custom_dict_dir() {
        let path = std::path::Path::new(&custom_dir);
        if path.exists() {
            if path.join("dictionary.bin").is_file() {
                let _ = db.load_from_dir_with_dict(path, "dictionary.bin");
            } else {
                let _ = db.load_from_dir(path);
            }
            loaded = true;
        }
    }
    if !loaded {
        for dir in candidate_dict_dirs() {
            if dir.exists() {
                if dir.join("dictionary.bin").is_file() {
                    let _ = db.load_from_dir_with_dict(dir, "dictionary.bin");
                } else {
                    let _ = db.load_from_dir(dir);
                }
                break;
            }
        }
    }
    let arc_db = Arc::new(db);
    *guard = Some(Arc::clone(&arc_db));
    arc_db
}

pub fn get_context_scorer() -> Arc<lekhani_ai::ContextScorer> {
    if let Ok(guard) = CONTEXT_SCORER.read() {
        if let Some(scorer) = guard.as_ref() {
            return Arc::clone(scorer);
        }
    }
    let mut guard = CONTEXT_SCORER.write().unwrap_or_else(|e| e.into_inner());
    if let Some(scorer) = guard.as_ref() {
        return Arc::clone(scorer);
    }
    let mut lm = lekhani_ai::LanguageModel::new();
    let mut loaded = false;
    if let Some(custom_dir) = get_custom_dict_dir() {
        let path = std::path::Path::new(&custom_dir).join("bengali_lm.bin");
        if path.is_file() && lm.load_binary_file(&path).is_ok() {
            loaded = true;
        }
    }
    if !loaded {
        for dir in candidate_dict_dirs() {
            let path = dir.join("bengali_lm.bin");
            if path.is_file() && lm.load_binary_file(&path).is_ok() {
                break;
            }
        }
    }
    let arc_scorer = Arc::new(lekhani_ai::ContextScorer::with_language_model(lm));
    *guard = Some(Arc::clone(&arc_scorer));
    arc_scorer
}

pub fn get_next_word_predictor() -> Arc<lekhani_ai::NextWordPredictor> {
    if let Ok(guard) = NEXT_WORD_PREDICTOR.read() {
        if let Some(pred) = guard.as_ref() {
            return Arc::clone(pred);
        }
    }
    let mut guard = NEXT_WORD_PREDICTOR
        .write()
        .unwrap_or_else(|e| e.into_inner());
    if let Some(pred) = guard.as_ref() {
        return Arc::clone(pred);
    }
    let scorer = get_context_scorer();
    let arc_pred = Arc::new(lekhani_ai::NextWordPredictor::with_language_model(
        scorer.lm().clone(),
    ));
    *guard = Some(Arc::clone(&arc_pred));
    arc_pred
}

/// Returns the GRU neural next-word predictor singleton.
///
/// Initializes a compact vocabulary seeded from the N-gram LM word list and
/// a fresh MicroGruModel. On device the weights file (`bengali_gru.bin`) would
/// replace the zero-initialized model; here we fall back gracefully so the
/// predictor is always available even without a trained weights file.
pub fn get_neural_predictor() -> Arc<lekhani_neural::NeuralContextPredictor> {
    if let Ok(guard) = NEURAL_PREDICTOR.read() {
        if let Some(pred) = guard.as_ref() {
            return Arc::clone(pred);
        }
    }
    let mut guard = NEURAL_PREDICTOR.write().unwrap_or_else(|e| e.into_inner());
    if let Some(pred) = guard.as_ref() {
        return Arc::clone(pred);
    }
    use lekhani_neural::{BpeVocabulary, MicroGruModel, NeuralContextPredictor};

    let mut vocab_opt = None;
    let mut model_opt = None;

    let mut search_dirs = Vec::new();
    if let Some(custom_dir) = get_custom_dict_dir() {
        search_dirs.push(std::path::PathBuf::from(custom_dir));
    }
    for dir in candidate_dict_dirs() {
        search_dirs.push(dir.to_path_buf());
    }

    for dir in &search_dirs {
        // 1. Check for v2 pair (bengali_gru_v2.bin + bengali_vocab_v2.json)
        let p_model_v2 = dir.join("bengali_gru_v2.bin");
        let p_vocab_v2 = dir.join("bengali_vocab_v2.json");
        if p_model_v2.exists() && p_vocab_v2.exists() {
            if let (Ok(m), Ok(v)) = (
                MicroGruModel::load_binary(&p_model_v2),
                BpeVocabulary::load_json(&p_vocab_v2),
            ) {
                if m.vocab_size() == v.len() {
                    model_opt = Some(Arc::new(m));
                    vocab_opt = Some(Arc::new(v));
                    break;
                }
            }
        }

        // 2. Check for v1 binary pair (bengali_gru.bin + bengali_vocab.bin)
        let p_model_v1 = dir.join("bengali_gru.bin");
        let p_vocab_v1 = dir.join("bengali_vocab.bin");
        if p_model_v1.exists() && p_vocab_v1.exists() {
            if let (Ok(m), Ok(v)) = (
                MicroGruModel::load_binary(&p_model_v1),
                BpeVocabulary::load_binary(&p_vocab_v1),
            ) {
                if m.vocab_size() == v.len() {
                    model_opt = Some(Arc::new(m));
                    vocab_opt = Some(Arc::new(v));
                    break;
                }
            }
        }

        // 3. Check for JSON development pair (neural_weights.json + neural_vocab.json)
        let p_model_json = dir.join("neural_weights.json");
        let p_vocab_json = dir.join("neural_vocab.json");
        if p_model_json.exists() && p_vocab_json.exists() {
            if let (Ok(m), Ok(v)) = (
                MicroGruModel::load_json(&p_model_json),
                BpeVocabulary::load_json(&p_vocab_json),
            ) {
                if m.vocab_size() == v.len() {
                    model_opt = Some(Arc::new(m));
                    vocab_opt = Some(Arc::new(v));
                    break;
                }
            }
        }
    }

    let vocab = vocab_opt.unwrap_or_else(|| {
        let db = get_core_database();
        let word_list: Vec<String> = db
            .trie
            .iter()
            .take(2048)
            .map(|(w, _)| w.to_string())
            .collect();
        Arc::new(if word_list.is_empty() {
            BpeVocabulary::new()
        } else {
            BpeVocabulary::from_tokens(word_list)
        })
    });

    let model = match model_opt {
        Some(m) if m.vocab_size() == vocab.len() => m,
        Some(m) => {
            eprintln!(
                "Lekhani AI: MicroGruModel vocab_size mismatch (model: {}, vocab: {}). Falling back to matched baseline.",
                m.vocab_size(),
                vocab.len()
            );
            Arc::new(MicroGruModel::new(vocab.len(), 64, 64))
        }
        None => Arc::new(MicroGruModel::new(vocab.len(), 64, 64)),
    };

    let predictor = Arc::new(
        NeuralContextPredictor::try_new(model.clone(), vocab.clone()).unwrap_or_else(|_| {
            let matched = Arc::new(MicroGruModel::new(vocab.len(), 64, 64));
            NeuralContextPredictor::new(matched, vocab)
        }),
    );
    *guard = Some(Arc::clone(&predictor));
    predictor
}

static PHONETIC_SUGGESTION: OnceLock<Mutex<lekhani_core::phonetic::PhoneticSuggestion>> =
    OnceLock::new();

pub fn get_phonetic_suggestion() -> &'static Mutex<lekhani_core::phonetic::PhoneticSuggestion> {
    PHONETIC_SUGGESTION.get_or_init(|| {
        let db = get_core_database();
        let scorer = get_context_scorer();
        let predictor = get_next_word_predictor();
        let mut sugg = lekhani_core::phonetic::PhoneticSuggestion::new();
        sugg.database = (*db).clone();
        sugg.ai_context = (*scorer).clone();
        sugg.ai_predictor = (*predictor).clone();
        sugg.config.enable_word_segmentation = true;

        let layout_candidates = [
            std::path::Path::new("/data/data/com.lekhani.android/files/layouts/avrophonetic.json"),
            std::path::Path::new(
                "/data/data/com.lekhani.android.debug/files/layouts/avrophonetic.json",
            ),
            std::path::Path::new(
                "/data/user/0/com.lekhani.android/files/layouts/avrophonetic.json",
            ),
            std::path::Path::new(
                "/data/user/0/com.lekhani.android.debug/files/layouts/avrophonetic.json",
            ),
            std::path::Path::new(
                "/data/user_de/0/com.lekhani.android/files/layouts/avrophonetic.json",
            ),
            std::path::Path::new(
                "/data/user_de/0/com.lekhani.android.debug/files/layouts/avrophonetic.json",
            ),
            std::path::Path::new("./data/layouts/avrophonetic.json"),
            std::path::Path::new("../data/layouts/avrophonetic.json"),
            std::path::Path::new("../../data/layouts/avrophonetic.json"),
        ];
        if let Some(custom_dir) = get_custom_dict_dir() {
            let dict_path = std::path::Path::new(&custom_dir);
            sugg.load_seq2seq_from_dir(dict_path);
            let parent = dict_path.parent();
            if let Some(p) = parent {
                let layout_path = p.join("layouts/avrophonetic.json");
                if layout_path.is_file() {
                    if let Ok(content) = std::fs::read_to_string(&layout_path) {
                        if let Ok(json) = serde_json::from_str(&content) {
                            sugg.set_layout(&json);
                        }
                    }
                }
            }
        }
        for path in layout_candidates {
            if path.is_file() {
                if let Ok(content) = std::fs::read_to_string(path) {
                    if let Ok(json) = serde_json::from_str(&content) {
                        sugg.set_layout(&json);
                        break;
                    }
                }
            }
        }
        Mutex::new(sugg)
    })
}

/// Unified facade encapsulating core shared engine singletons.
pub struct EngineEnvironment {
    pub db: Arc<PhoneticDatabase>,
    pub scorer: Arc<lekhani_ai::ContextScorer>,
    pub predictor: Arc<lekhani_ai::NextWordPredictor>,
    pub neural: Arc<lekhani_neural::NeuralContextPredictor>,
}

impl EngineEnvironment {
    /// Returns the global shared EngineEnvironment facade instance.
    pub fn get() -> Self {
        Self {
            db: get_core_database(),
            scorer: get_context_scorer(),
            predictor: get_next_word_predictor(),
            neural: get_neural_predictor(),
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Public data types exported to Kotlin via UniFFI
// ──────────────────────────────────────────────────────────────────────────────
