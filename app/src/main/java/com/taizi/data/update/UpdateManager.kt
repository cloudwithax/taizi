package com.taizi.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.taizi.BuildConfig
import com.taizi.MainActivity
import com.taizi.data.network.GitHubService
import com.taizi.domain.model.SemanticVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

data class UpdateCheckResult(
    val isUpdateAvailable: Boolean,
    val currentVersion: SemanticVersion,
    val latestVersion: SemanticVersion?,
    val releaseNotes: String,
    val downloadUrl: String,
    val error: String? = null
)

sealed class UpdateDownloadState {
    object Idle : UpdateDownloadState()
    object Checking : UpdateDownloadState()
    data class Downloading(val progress: Int) : UpdateDownloadState()
    data class Ready(val file: File) : UpdateDownloadState()
    data class Error(val message: String) : UpdateDownloadState()
}

class UpdateManager(
    private val context: Context,
    private val githubService: GitHubService,
    private val repoOwner: String,
    private val repoName: String
) {
    private val TAG = "UpdateManager"

    companion object {
        fun findApkDownloadUrl(assets: List<com.taizi.data.network.AssetData>): String {
            return assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }?.downloadUrl ?: ""
        }
    }

    fun canRequestInstallPackages(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun getInstallPermissionIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            Intent()
        }
    }

    suspend fun checkForUpdates(currentVersion: SemanticVersion = BuildConfig.VERSION_NAME.let {
        SemanticVersion.fromString(it)
    }): UpdateCheckResult {
        return try {
            val release = githubService.getLatestRelease(repoOwner, repoName)
            if (release == null) {
                return UpdateCheckResult(false, currentVersion, null, "", "", "No release found")
            }
            val latestVersion = githubService.extractVersionFromTag(release.tagName)
            val isUpdate = latestVersion.compareTo(currentVersion) > 0
            UpdateCheckResult(
                isUpdate,
                currentVersion,
                latestVersion,
                release.body ?: "",
                findApkDownloadUrl(release.assets),
                null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed", e)
            UpdateCheckResult(false, currentVersion, null, "", "", e.message)
        }
    }

    suspend fun downloadUpdate(url: String, onProgress: (Int) -> Unit): File? {
        return try {
            withContext(Dispatchers.IO) {
                val client = OkHttpClient.Builder().build()
                val request = Request.Builder().url(url).build()
                val resp = client.newCall(request).execute()
                if (!resp.isSuccessful) return@withContext null

                val contentLength = resp.body?.contentLength() ?: -1L
                val input = resp.body?.byteStream() ?: return@withContext null

                val updatesDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "updates")
                updatesDir.mkdirs()

                // Clean up old APKs before downloading new one
                updatesDir.listFiles { f -> f.name.endsWith(".apk", ignoreCase = true) }?.forEach { it.delete() }

                val file = File(updatesDir, url.substringAfterLast("/"))
                FileOutputStream(file).use { out ->
                    val buf = ByteArray(8192)
                    var n: Int
                    var totalRead = 0L
                    while (input.read(buf).also { n = it } != -1) {
                        out.write(buf, 0, n)
                        totalRead += n
                        if (contentLength > 0) {
                            val progress = ((totalRead * 100) / contentLength).toInt()
                            onProgress(progress)
                        }
                    }
                }
                file
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            null
        }
    }

    fun installApk(file: File): Boolean {
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Install failed", e)
            false
        }
    }

    fun createRelaunchPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
