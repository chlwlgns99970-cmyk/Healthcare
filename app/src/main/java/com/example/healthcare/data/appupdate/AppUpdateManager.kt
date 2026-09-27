package com.example.healthcare.data.appupdate

import android.app.Activity
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class AppUpdateManager(
    private val context: Context,
    repository: AppUpdateRepository,
    preferences: AppUpdatePreferenceStore,
    private val downloader: UpdateApkDownloader,
    private val identityReader: ApkIdentityReader,
    private val verifier: ApkUpdateVerifier,
    private val installer: SystemAppInstaller,
    private val scope: CoroutineScope,
    private val currentVersionCode: Int,
    private val expectedPackageName: String,
    private val installEnabled: Boolean
) {
    private val checker = AppUpdateChecker(repository, preferences, currentVersionCode)
    private val operationMutex = Mutex()
    private val _state = MutableStateFlow(AppUpdateUiState())
    val state: StateFlow<AppUpdateUiState> = _state.asStateFlow()
    private var verifiedApk: File? = null

    fun checkAutomatically() = check(manual = false)

    fun checkManually() = check(manual = true)

    private fun check(manual: Boolean) {
        scope.launch {
            operationMutex.withLock {
                _state.value = AppUpdateUiState(
                    phase = AppUpdatePhase.CHECKING,
                    message = if (manual) "업데이트 확인 중…" else null
                )
                when (val result = checker.check(manual)) {
                    is AppUpdateCheckResult.Available -> _state.value = AppUpdateUiState(
                        phase = AppUpdatePhase.AVAILABLE,
                        release = result.release
                    )
                    AppUpdateCheckResult.UpToDate -> _state.value = if (manual) {
                        AppUpdateUiState(
                            phase = AppUpdatePhase.UP_TO_DATE,
                            message = "현재 최신 버전을 사용 중이에요."
                        )
                    } else {
                        AppUpdateUiState()
                    }
                    AppUpdateCheckResult.Failed -> _state.value = if (manual) {
                        AppUpdateUiState(
                            phase = AppUpdatePhase.FAILED,
                            message = "업데이트 정보를 확인하지 못했어요."
                        )
                    } else {
                        AppUpdateUiState()
                    }
                    AppUpdateCheckResult.Throttled,
                    AppUpdateCheckResult.DismissedForSession -> _state.value = AppUpdateUiState()
                }
            }
        }
    }

    fun dismissAvailableUpdate() {
        state.value.release?.let { checker.dismissForSession(it.versionCode) }
        _state.value = AppUpdateUiState()
    }

    fun startDownload() {
        val release = state.value.release ?: return
        if (!installEnabled || context.packageName != expectedPackageName) {
            _state.value = AppUpdateUiState(
                phase = AppUpdatePhase.FAILED,
                message = "이 빌드에서는 제품 업데이트를 설치할 수 없어요."
            )
            return
        }
        scope.launch {
            operationMutex.withLock {
                var downloaded: File? = null
                try {
                    _state.value = AppUpdateUiState(
                        phase = AppUpdatePhase.DOWNLOADING,
                        release = release,
                        downloadProgress = 0,
                        message = "업데이트 다운로드 중"
                    )
                    downloaded = downloader.download(release) { progress ->
                        _state.value = _state.value.copy(downloadProgress = progress)
                    }
                    _state.value = AppUpdateUiState(
                        phase = AppUpdatePhase.VERIFYING,
                        release = release,
                        downloadProgress = 100,
                        message = "업데이트 파일 확인 중…"
                    )
                    val installed = identityReader.readInstalled(expectedPackageName)
                        ?: error("Installed product identity unavailable")
                    val verification = verifier.verify(
                        apkFile = downloaded,
                        metadata = release,
                        currentVersionCode = currentVersionCode.toLong(),
                        installedSignerSha256 = installed.signerSha256
                    )
                    if (verification !is ApkVerificationResult.Valid) {
                        val failure = (verification as ApkVerificationResult.Invalid).failure
                        Log.w(TAG, "Rejected downloaded update: $failure")
                        downloader.delete(downloaded)
                        _state.value = AppUpdateUiState(
                            phase = AppUpdatePhase.FAILED,
                            message = "업데이트 파일을 확인할 수 없어 설치하지 않았어요."
                        )
                        return@withLock
                    }
                    verifiedApk = downloaded
                    _state.value = AppUpdateUiState(
                        phase = AppUpdatePhase.READY_TO_INSTALL,
                        release = release,
                        downloadProgress = 100,
                        message = "업데이트를 설치할 준비가 되었어요."
                    )
                } catch (error: Throwable) {
                    Log.w(TAG, "Update download failed", error)
                    downloader.delete(downloaded)
                    _state.value = AppUpdateUiState(
                        phase = AppUpdatePhase.FAILED,
                        message = "다운로드에 실패했어요. 다시 시도해주세요."
                    )
                }
            }
        }
    }

    fun continueInstallation(activity: Activity) {
        if (state.value.phase != AppUpdatePhase.READY_TO_INSTALL) return
        val apk = verifiedApk ?: return failInstallation()
        if (!installEnabled || activity.packageName != expectedPackageName) return failInstallation()
        if (!installer.canRequestPackageInstalls(activity)) {
            _state.value = _state.value.copy(phase = AppUpdatePhase.INSTALL_PERMISSION_REQUIRED)
            return
        }
        launchInstaller(activity, apk)
    }

    fun openInstallPermissionSettings(activity: Activity) {
        if (state.value.phase != AppUpdatePhase.INSTALL_PERMISSION_REQUIRED) return
        runCatching { installer.openInstallPermissionSettings(activity) }
            .onFailure { failInstallation() }
    }

    fun onHostResumed(activity: Activity) {
        if (state.value.phase == AppUpdatePhase.INSTALL_PERMISSION_REQUIRED &&
            installer.canRequestPackageInstalls(activity)
        ) {
            verifiedApk?.let { launchInstaller(activity, it) } ?: failInstallation()
        }
    }

    fun dismissStatus() {
        if (state.value.phase == AppUpdatePhase.INSTALL_PERMISSION_REQUIRED ||
            state.value.phase == AppUpdatePhase.FAILED
        ) {
            downloader.delete(verifiedApk)
            verifiedApk = null
        }
        _state.value = AppUpdateUiState()
    }

    private fun launchInstaller(activity: Activity, apk: File) {
        _state.value = _state.value.copy(phase = AppUpdatePhase.INSTALLER_OPENED)
        runCatching { installer.openPackageInstaller(activity, apk) }
            .onFailure {
                Log.w(TAG, "Unable to open package installer", it)
                failInstallation()
            }
    }

    private fun failInstallation() {
        downloader.delete(verifiedApk)
        verifiedApk = null
        _state.value = AppUpdateUiState(
            phase = AppUpdatePhase.FAILED,
            message = "설치 화면을 열지 못했어요."
        )
    }

    private companion object {
        const val TAG = "AppUpdateManager"
    }
}
