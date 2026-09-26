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
 * ══════════════════════════════════════════════════════════════════════════════
 * Automatically unpacks bundled offline dictionaries, language models, and
 * layouts from APK assets into internal application storage on first launch.
 *
 * Adheres strictly to AGENTS.md §1 (100% on-device offline storage, zero network).
 */
object LekhaniAssetInstaller {
    private const val TAG = "LekhaniAssetInstaller"

    private val BUNDLED_DICTIONARIES = listOf(
        "english_dict.bin",
        "dictionary.bin",
        "dictionary.json",
        "bengali_lm.bin",
        "autocorrect.json",
        "regex.json",
        "suffix.json"
    )

    private val BUNDLED_LAYOUTS = listOf(
        "Avro_Easy.json",
        "avrophonetic.json",
        "Borno.json",
        "Munir_Optima.json",
        "National_Jatiya.json",
        "Probhat.json",
        "Unijoy.json"
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

                // Combine dynamic list (if available) with explicit bundled file list
                val filesToUnpack = LinkedHashSet<String>()
                try {
                    context.assets.list(subDir)?.let { filesToUnpack.addAll(it) }
                } catch (_: IOException) {}
                filesToUnpack.addAll(defaultFiles)

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
