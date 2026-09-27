package com.example.healthcare.data.appupdate

const val APP_UPDATE_CHECK_INTERVAL_MILLIS: Long = 24L * 60L * 60L * 1000L

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
    fun shouldCheckAutomatically(lastSuccessfulCheckAt: Long, nowMillis: Long): Boolean =
        lastSuccessfulCheckAt <= 0L || nowMillis - lastSuccessfulCheckAt >= APP_UPDATE_CHECK_INTERVAL_MILLIS

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
        if (!manual && !AppUpdatePolicy.shouldCheckAutomatically(preferences.lastSuccessfulCheckAt, now)) {
            return AppUpdateCheckResult.Throttled
        }
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
