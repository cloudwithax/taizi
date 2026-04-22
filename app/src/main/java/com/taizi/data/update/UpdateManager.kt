package com.taizi.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
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

    suspend fun checkForUpdates(currentVersion: SemanticVersion): UpdateCheckResult {
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
                val resp = OkHttpClient.Builder().build().newCall(Request.Builder().url(url).build()).execute()
                if (!resp.isSuccessful) return@withContext null
                val input = resp.body?.byteStream() ?: return@withContext null
                val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "updates/${url.substringAfterLast("/")}")
                file.parentFile?.mkdirs()
                FileOutputStream(file).use { out ->
                    val buf = ByteArray(8192)
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) out.write(buf, 0, n)
                }
                file
            }
        } catch (e: Exception) { null }
    }

    fun installApk(file: File): Uri? {
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
            uri
        } catch (e: Exception) { null }
    }
}