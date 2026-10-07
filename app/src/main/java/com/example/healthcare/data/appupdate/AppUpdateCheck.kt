package com.example.healthcare.data.appupdate

interface AppUpdatePreferenceStore {
    val lastSuccessfulCheckAt: Long
    val lastPromptedVersionCode: Int
    fun recordSuccessfulCheck(atMillis: Long)
    fun recordPromptedVersion(versionCode: Int)
}

interface AppUpdateRepository {
    suspend fun getLatestRelease(): AppReleaseMetadata
}

object AppUpdatePolicy {
    fun isUpdateAvailable(serverVersionCode: Int, currentVersionCode: Int): Boolean =
        serverVersionCode > currentVersionCode
}

class AppUpdateChecker(
    private val repository: AppUpdateRepository,
    private val preferences: AppUpdatePreferenceStore,
    private val currentVersionCode: Int,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private val dismissedVersionsThisSession = mutableSetOf<Int>()

    suspend fun check(manual: Boolean): AppUpdateCheckResult {
        val now = nowMillis()
        return runCatching {
            val release = repository.getLatestRelease().normalized()
            if (!AppReleaseMetadataValidator.isValid(release)) {
                return@runCatching AppUpdateCheckResult.Failed
            }
            preferences.recordSuccessfulCheck(now)
            if (!AppUpdatePolicy.isUpdateAvailable(release.versionCode, currentVersionCode)) {
                AppUpdateCheckResult.UpToDate
            } else if (!manual && release.versionCode in dismissedVersionsThisSession) {
                AppUpdateCheckResult.DismissedForSession
            } else {
                preferences.recordPromptedVersion(release.versionCode)
                AppUpdateCheckResult.Available(release)
            }
        }.getOrDefault(AppUpdateCheckResult.Failed)
    }

    fun dismissForSession(versionCode: Int) {
        dismissedVersionsThisSession += versionCode
        preferences.recordPromptedVersion(versionCode)
    }
}
