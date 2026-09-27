package com.example.healthcare.data.appupdate

import com.squareup.moshi.JsonClass
import java.net.URI

@JsonClass(generateAdapter = true)
data class AppReleaseMetadata(
    val versionName: String = "",
    val versionCode: Int = 0,
    val apkUrl: String = "",
    val fileName: String = "",
    val fileSizeBytes: Long = 0L,
    val sha256: String = "",
    val releaseNotes: List<String> = emptyList(),
    val releasedAt: String = ""
) {
    fun normalized(): AppReleaseMetadata = copy(
        versionName = versionName.trim(),
        apkUrl = apkUrl.trim(),
        fileName = fileName.trim(),
        sha256 = sha256.trim().uppercase(),
        releaseNotes = releaseNotes.map(String::trim).filter(String::isNotEmpty),
        releasedAt = releasedAt.trim()
    )
}

object AppReleaseMetadataValidator {
    fun isValid(metadata: AppReleaseMetadata): Boolean {
        val value = metadata.normalized()
        val httpsUrl = runCatching {
            val uri = URI(value.apkUrl)
            uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() &&
                uri.userInfo == null
        }.getOrDefault(false)
        val safeFileName = value.fileName.isNotBlank() &&
            value.fileName.endsWith(".apk", ignoreCase = true) &&
            !value.fileName.contains('/') && !value.fileName.contains('\\') &&
            value.fileName != "." && value.fileName != ".."
        return value.versionName.isNotBlank() && value.versionCode > 0 && httpsUrl &&
            safeFileName && value.fileSizeBytes > 0L &&
            value.sha256.matches(Regex("^[A-F0-9]{64}$"))
    }
}

enum class AppUpdatePhase {
    IDLE,
    CHECKING,
    AVAILABLE,
    UP_TO_DATE,
    DOWNLOADING,
    VERIFYING,
    READY_TO_INSTALL,
    INSTALL_PERMISSION_REQUIRED,
    INSTALLER_OPENED,
    FAILED
}

data class AppUpdateUiState(
    val phase: AppUpdatePhase = AppUpdatePhase.IDLE,
    val release: AppReleaseMetadata? = null,
    val downloadProgress: Int = 0,
    val message: String? = null
)

sealed interface AppUpdateCheckResult {
    data class Available(val release: AppReleaseMetadata) : AppUpdateCheckResult
    data object UpToDate : AppUpdateCheckResult
    data object Throttled : AppUpdateCheckResult
    data object DismissedForSession : AppUpdateCheckResult
    data object Failed : AppUpdateCheckResult
}
