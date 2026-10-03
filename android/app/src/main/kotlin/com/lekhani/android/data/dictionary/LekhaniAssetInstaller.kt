package com.lekhani.android.data.dictionary

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * LekhaniAssetInstaller
 *
 * Automatically unpacks bundled offline dictionaries, language models, and
 * layouts from APK assets into internal application storage on first launch.
 *
 * Adheres strictly to offline-first principles (100% on-device offline storage, zero network).
 */
object LekhaniAssetInstaller {
    private const val TAG = "LekhaniAssetInstaller"

    private val BUNDLED_DICTIONARIES = listOf(
        // ── Core Bengali dictionary (binary primary, JSON fallback) ───────────
        "dictionary.bin",       // 5.0 MB  – PrefixTrie; primary path, always used
        "dictionary.json",      // 4.1 MB  – JSON source; fallback if .bin is missing on cold install
        // ── Language models ──────────────────────────────────────────────────
        "bengali_lm.bin",       // 5.1 MB  – N-gram LM; required for scoring
        "english_lm.bin",       //  40 KB  – English N-gram LM
        // ── Neural GRU predictor (v2 binary pair, preferred) ─────────────────
        "bengali_gru_v2.bin",   // 592 KB  – MicroGruModel weights v2
        "bengali_vocab_v2.json", //  68 KB  – BPE vocabulary paired with gru_v2
        // NOTE: v1 pair (bengali_gru.bin + bengali_vocab.bin) and dev JSON pair
        //       (neural_weights.json + neural_vocab.json) are intentionally omitted.
        //       The engine selects v2 first; v1/JSON are never reached when v2 is present.
        // ── Phonetic overrides (binary only) ─────────────────────────────────
        "phonetic_overrides.bin", // 916 KB – Supervised overrides; binary always present
        // NOTE: phonetic_overrides.json (1.8 MB) is omitted — it is only a fallback
        //       when the .bin is absent, which never happens in production.
        // ── Lightweight data files ────────────────────────────────────────────
        "autocorrect.json",     //  72 KB  – Autocorrect rules (also embedded via include_bytes!)
        "suffix.json",          //  24 KB  – Morphological suffixes (also embedded via include_bytes!)
        "rank_weights_v2.json", //   4 KB  – Perceptron rank weights
        // ── English dictionary ────────────────────────────────────────────────
        "english_dict.bin"      // 996 KB  – English PrefixTrie for QWERTY mode
    )

    private val BUNDLED_LAYOUTS = listOf(
        "avrophonetic.json",
        "National_Jatiya.json",
        "Probhat.json"
    )

    /**
     * Synchronously installs dictionaries and layouts to context.filesDir (and DPS) if not already present.
     */
    fun installAssetsIfNeeded(context: Context) {
        val targetRoots = mutableListOf<File>()
        targetRoots.add(context.filesDir)
        try {
            val dps = context.createDeviceProtectedStorageContext()
            if (dps.filesDir != null && dps.filesDir.absolutePath != context.filesDir.absolutePath) {
                targetRoots.add(dps.filesDir)
            }
        } catch (_: Exception) {}

        val manifest = mapOf(
            "dictionaries" to BUNDLED_DICTIONARIES,
            "layouts" to BUNDLED_LAYOUTS
        )

        for (rootDir in targetRoots) {
            for ((subDir, defaultFiles) in manifest) {
                val targetDir = File(rootDir, subDir)
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }

                // Strict whitelist: only install explicitly declared files
                val filesToUnpack = defaultFiles

                for (assetName in filesToUnpack) {
                    val targetFile = File(targetDir, assetName)
                    if (!targetFile.exists() || targetFile.length() == 0L) {
                        try {
                            context.assets.open("$subDir/$assetName").use { input ->
                                FileOutputStream(targetFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            Log.d(TAG, "Unpacked asset: $subDir/$assetName (${targetFile.length()} bytes) to ${targetFile.absolutePath}")
                        } catch (e: IOException) {
                            Log.w(TAG, "Could not open asset $subDir/$assetName: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    /**
     * Asynchronously install assets in the background to keep UI startup snappy.
     */
    suspend fun installAssetsAsync(context: Context) = withContext(Dispatchers.IO) {
        installAssetsIfNeeded(context)
    }
}
