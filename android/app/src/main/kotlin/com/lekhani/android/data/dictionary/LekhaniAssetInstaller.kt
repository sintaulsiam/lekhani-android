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

    /**
     * Synchronously installs dictionaries and layouts to context.filesDir if not already present.
     */
    fun installAssetsIfNeeded(context: Context) {
        val rootDir = context.filesDir
        val subDirs = listOf("dictionaries", "layouts")

        for (subDir in subDirs) {
            val targetDir = File(rootDir, subDir)
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }

            try {
                val assetList = context.assets.list(subDir) ?: continue
                for (assetName in assetList) {
                    val targetFile = File(targetDir, assetName)
                    // If target doesn't exist or is empty, copy from APK assets
                    if (!targetFile.exists() || targetFile.length() == 0L) {
                        try {
                            context.assets.open("$subDir/$assetName").use { input ->
                                FileOutputStream(targetFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            Log.d(TAG, "Unpacked asset: $subDir/$assetName (${targetFile.length()} bytes)")
                        } catch (e: IOException) {
                            Log.e(TAG, "Failed unpacking asset $subDir/$assetName: ${e.message}")
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error listing asset folder '$subDir': ${e.message}")
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
