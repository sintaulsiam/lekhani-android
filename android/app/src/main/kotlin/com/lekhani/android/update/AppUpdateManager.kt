package com.lekhani.android.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.lekhani.android.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * Metadata for a GitHub Release APK.
 */
data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val title: String,
    val notes: String,
    val publishedAt: String,
    val downloadUrl: String,
    val fileName: String,
    val fileSize: Long,
    val htmlUrl: String
)

/**
 * Observable UI state for the in-app updater.
 */
sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class Available(val info: ReleaseInfo, val isNewer: Boolean) : UpdateState()
    data class Downloading(
        val info: ReleaseInfo,
        val progressPercent: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : UpdateState()
    data class ReadyToInstall(val info: ReleaseInfo, val apkFile: File) : UpdateState()
    data class Error(val message: String, val isNetworkError: Boolean = true) : UpdateState()
}

/**
 * AppUpdateManager
 *
 * Provides 100% on-demand, user-initiated update checks and downloads
 * directly from GitHub Releases (sintaulsiam/lekhani-android).
 *
 * Inviolable Privacy Policy:
 * - NEVER sends telemetry, device IDs, typing frequencies, or personal data.
 * - Network access is strictly user-initiated when tapping "Check for Updates" or "Download".
 */
object AppUpdateManager {

    private const val GITHUB_REPO_API = "https://api.github.com/repos/sintaulsiam/lekhani-android/releases/latest"
    private const val USER_AGENT = "Lekhani-Android-Updater"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000

    /**
     * Checks GitHub API for the latest release on Dispatchers.IO.
     */
    suspend fun fetchLatestRelease(): Result<ReleaseInfo> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(GITHUB_REPO_API)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                instanceFollowRedirects = true
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(
                    IllegalStateException("GitHub API returned HTTP $responseCode")
                )
            }

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseBody)

            val tagName = json.optString("tag_name", "").trim()
            val versionName = tagName.removePrefix("v").removePrefix("V").trim()
            val title = json.optString("name", tagName)
            val notes = json.optString("body", "")
            val publishedAt = json.optString("published_at", "")
            val htmlUrl = json.optString("html_url", "https://github.com/sintaulsiam/lekhani-android/releases")

            val assetsArray = json.optJSONArray("assets")
            if (assetsArray == null || assetsArray.length() == 0) {
                return@withContext Result.failure(
                    IllegalStateException("No downloadable APK assets found in release $tagName")
                )
            }

            // Inspect available assets and find the best APK matching device architecture
            val supportedAbis = Build.SUPPORTED_ABIS ?: emptyArray()
            var chosenUrl: String? = null
            var chosenName: String? = null
            var chosenSize: Long = 0L

            data class CandidateAsset(val name: String, val url: String, val size: Long)
            val apkAssets = mutableListOf<CandidateAsset>()

            for (i in 0 until assetsArray.length()) {
                val assetObj = assetsArray.getJSONObject(i)
                val name = assetObj.optString("name", "")
                val url = assetObj.optString("browser_download_url", "")
                val size = assetObj.optLong("size", 0L)
                if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                    apkAssets.add(CandidateAsset(name, url, size))
                }
            }

            if (apkAssets.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("No .apk files found in release $tagName assets")
                )
            }

            // Priority 1: Match device primary ABI (e.g. app-arm64-v8a-release.apk)
            for (abi in supportedAbis) {
                val match = apkAssets.firstOrNull { it.name.contains(abi, ignoreCase = true) }
                if (match != null) {
                    chosenName = match.name
                    chosenUrl = match.url
                    chosenSize = match.size
                    break
                }
            }

            // Priority 2: Universal APK or latest named APK
            if (chosenUrl == null) {
                val universalMatch = apkAssets.firstOrNull {
                    it.name.contains("universal", ignoreCase = true) ||
                    it.name.contains("latest", ignoreCase = true)
                }
                if (universalMatch != null) {
                    chosenName = universalMatch.name
                    chosenUrl = universalMatch.url
                    chosenSize = universalMatch.size
                }
            }

            // Priority 3: First available APK
            if (chosenUrl == null) {
                val firstApk = apkAssets.first()
                chosenName = firstApk.name
                chosenUrl = firstApk.url
                chosenSize = firstApk.size
            }

            Result.success(
                ReleaseInfo(
                    tagName = tagName,
                    versionName = versionName,
                    title = title,
                    notes = notes,
                    publishedAt = publishedAt,
                    downloadUrl = chosenUrl!!,
                    fileName = chosenName ?: "Lekhani-Keyboard.apk",
                    fileSize = chosenSize,
                    htmlUrl = htmlUrl
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Determines whether [remoteVersion] is newer than [currentVersion].
     * Parses numeric semver segments (e.g. "0.3.1" > "0.3.0").
     */
    fun isVersionNewer(
        remoteVersion: String,
        currentVersion: String = BuildConfig.VERSION_NAME
    ): Boolean {
        val remoteParts = parseVersionParts(remoteVersion)
        val currentParts = parseVersionParts(currentVersion)
        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    private fun parseVersionParts(versionStr: String): List<Int> {
        val cleaned = versionStr.trim()
            .removePrefix("v").removePrefix("V")
            .substringBefore("-").substringBefore("+")
        return cleaned.split(".").mapNotNull { it.toIntOrNull() }
    }

    /**
     * Streams APK download to cacheDir/updates/ atomically, reporting progress.
     */
    suspend fun downloadApk(
        context: Context,
        info: ReleaseInfo,
        onProgress: (progressPercent: Int, bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val tempFile = File(updateDir, "update_download.tmp")
        val targetFile = File(updateDir, "Lekhani-${info.versionName}.apk")

        if (tempFile.exists()) {
            tempFile.delete()
        }

        var currentUrl = info.downloadUrl
        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            var redirects = 0
            while (redirects < 5) {
                coroutineContext.ensureActive()
                val url = URL(currentUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    setRequestProperty("User-Agent", USER_AGENT)
                }

                val status = connection.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_PERM ||
                    status == HttpURLConnection.HTTP_MOVED_TEMP ||
                    status == HttpURLConnection.HTTP_SEE_OTHER ||
                    status == 307 || status == 308
                ) {
                    val location = connection.getHeaderField("Location") ?: break
                    currentUrl = if (location.startsWith("http")) location else URL(url, location).toString()
                    connection.disconnect()
                    redirects++
                } else {
                    break
                }
            }

            val finalConn = connection ?: return@withContext Result.failure(
                IllegalStateException("Failed to establish download connection")
            )

            val contentLength = finalConn.contentLengthLong.takeIf { it > 0 } ?: info.fileSize
            inputStream = finalConn.inputStream
            outputStream = FileOutputStream(tempFile)

            val buffer = ByteArray(32 * 1024)
            var totalRead = 0L
            var lastProgressTime = 0L

            while (true) {
                coroutineContext.ensureActive()
                val read = inputStream.read(buffer)
                if (read == -1) break
                outputStream.write(buffer, 0, read)
                totalRead += read

                val now = System.currentTimeMillis()
                if (now - lastProgressTime >= 100 || totalRead == contentLength) {
                    lastProgressTime = now
                    val percent = if (contentLength > 0) {
                        ((totalRead * 100) / contentLength).toInt().coerceIn(0, 100)
                    } else 0
                    onProgress(percent, totalRead, contentLength)
                }
            }

            outputStream.flush()
            outputStream.close()
            outputStream = null

            // Atomically move temp file to target file
            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            // Verify downloaded APK integrity
            val pm = context.packageManager
            val archiveInfo = pm.getPackageArchiveInfo(targetFile.absolutePath, 0)
            if (archiveInfo == null) {
                targetFile.delete()
                return@withContext Result.failure(
                    IllegalStateException("Downloaded APK file is corrupt or invalid.")
                )
            }

            if (archiveInfo.packageName != context.packageName) {
                targetFile.delete()
                return@withContext Result.failure(
                    SecurityException("Security verification failed: package name ${archiveInfo.packageName} does not match ${context.packageName}")
                )
            }

            Result.success(targetFile)
        } catch (e: Exception) {
            tempFile.delete()
            Result.failure(e)
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
            try { outputStream?.close() } catch (_: Exception) {}
            connection?.disconnect()
        }
    }

    /**
     * Checks if the app currently has permission to install unknown APKs.
     */
    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Launches the system settings screen for "Install Unknown Apps".
     */
    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /**
     * Triggers the Android PackageInstaller to update Lekhani.
     */
    fun triggerInstall(context: Context, apkFile: File): Result<Unit> {
        return try {
            if (!apkFile.exists()) {
                return Result.failure(IllegalStateException("APK file not found at ${apkFile.absolutePath}"))
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !canInstallPackages(context)) {
                openInstallPermissionSettings(context)
                return Result.failure(
                    SecurityException("Permission to install unknown packages is required. Please grant it in Settings.")
                )
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
