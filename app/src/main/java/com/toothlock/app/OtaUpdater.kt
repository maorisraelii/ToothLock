package com.toothlock.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val tagName: String,
    val name: String,
    val body: String,
    val downloadUrl: String
)

object OtaUpdater {

    private const val GITHUB_REPO = "maorisraelii/ToothLock"
    private const val API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

    /**
     * Checks GitHub Releases API for the latest published release.
     */
    suspend fun checkLatestRelease(): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL(API_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "ToothLock-OTA")
                connectTimeout = 8000
                readTimeout = 8000
            }

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val tagName = json.getString("tag_name")
                val name = json.optString("name", tagName)
                val body = json.optString("body", "")

                val assets = json.getJSONArray("assets")
                var downloadUrl = ""

                // 1. Specifically match the Phone APK: ToothLock.apk
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val assetName = asset.getString("name")
                    if (assetName.equals("ToothLock.apk", ignoreCase = true)) {
                        downloadUrl = asset.getString("browser_download_url")
                        break
                    }
                }

                // 2. Fallback: match any APK that is NOT the Watch APK
                if (downloadUrl.isEmpty()) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val assetName = asset.getString("name")
                        if (assetName.endsWith(".apk", ignoreCase = true) && !assetName.contains("watch", ignoreCase = true)) {
                            downloadUrl = asset.getString("browser_download_url")
                            break
                        }
                    }
                }

                if (downloadUrl.isNotEmpty()) {
                    return@withContext ReleaseInfo(tagName, name, body, downloadUrl)
                }
            }
        } catch (_: Exception) {
            // Network failure or offline
        }
        null
    }

    /**
     * Compares version tags (e.g. "v1.0.5" vs "v1.0.6")
     */
    fun isNewerVersion(currentVersionName: String, releaseTagName: String): Boolean {
        val currentClean = currentVersionName.removePrefix("v").trim()
        val releaseClean = releaseTagName.removePrefix("v").trim()
        if (currentClean == releaseClean) return false

        val currentParts = currentClean.split(".").mapNotNull { it.toIntOrNull() }
        val releaseParts = releaseClean.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(currentParts.size, releaseParts.size)
        for (i in 0 until maxLen) {
            val cur = currentParts.getOrElse(i) { 0 }
            val rel = releaseParts.getOrElse(i) { 0 }
            if (rel > cur) return true
            if (rel < cur) return false
        }
        return false
    }

    /**
     * Downloads the APK file to cache directory with progress reporting.
     */
    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        onProgress: (percent: Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val url = URL(downloadUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 10000
                readTimeout = 15000
            }

            // Handle HTTP redirects (GitHub Releases redirect to AWS S3)
            var actualConn = conn
            var responseCode = actualConn.responseCode
            var redirects = 0
            while ((responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                        responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                        responseCode == HttpURLConnection.HTTP_SEE_OTHER) && redirects < 5
            ) {
                val newUrl = actualConn.getHeaderField("Location")
                actualConn = (URL(newUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 15000
                }
                responseCode = actualConn.responseCode
                redirects++
            }

            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext null
            }

            val fileLength = actualConn.contentLength
            val cacheDir = context.externalCacheDir ?: context.cacheDir
            val apkFile = File(cacheDir, "ToothLock_update.apk")
            if (apkFile.exists()) apkFile.delete()

            actualConn.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var total: Long = 0
                    var count: Int
                    while (input.read(buffer).also { count = it } != -1) {
                        total += count.toLong()
                        if (fileLength > 0) {
                            val percent = ((total * 100) / fileLength).toInt()
                            withContext(Dispatchers.Main) {
                                onProgress(percent)
                            }
                        }
                        output.write(buffer, 0, count)
                    }
                    output.flush()
                }
            }

            return@withContext apkFile
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Launches the native Android package installer for the downloaded APK.
     */
    fun installApk(activity: Activity, apkFile: File) {
        if (!apkFile.exists()) return

        // On Android 8.0+, check if app has permission to request package installs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.packageManager.canRequestPackageInstalls()) {
                val manageIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivity(manageIntent)
                Toast.makeText(
                    activity,
                    "אנא אשר ל-ToothLock להתקין עדכונים, ולאחר מכן לחץ שוב לעדכן",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        }

        val apkUri: Uri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        activity.startActivity(installIntent)
    }
}
