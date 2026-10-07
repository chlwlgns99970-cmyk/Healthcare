package com.example.healthcare.data.appupdate

import android.app.Activity
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class AppUpdateManager(
    repository: AppUpdateRepository,
    preferences: AppUpdatePreferenceStore,
    private val downloader: UpdateApkDownloader,
    private val verifier: ApkUpdateVerifier,
    private val installer: SystemAppInstaller,
    private val scope: CoroutineScope,
    private val currentVersionCode: Int,
    private val currentPackageName: String,
    private val expectedPackageName: String,
    private val installEnabled: Boolean,
    private val showTechnicalFailureReason: Boolean,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
    private val logger: (String, Throwable?) -> Unit = { message, error ->
        if (error == null) Log.w(TAG, message) else Log.w(TAG, message, error)
    }
) {
    private val checker = AppUpdateChecker(repository, preferences, currentVersionCode)
    private val operationMutex = Mutex()
    private val _state = MutableStateFlow(AppUpdateUiState())
    val state: StateFlow<AppUpdateUiState> = _state.asStateFlow()
    private val coldStartCheckStarted = AtomicBoolean(false)
    private var verifiedApk: File? = null

    /** Runs once for the lifetime of this app process, regardless of activity recreation. */
    fun checkOnColdStart() {
        if (coldStartCheckStarted.compareAndSet(false, true)) {
            check(manual = false)
        }
    }

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
        startDownload(state.value.release)
    }

    fun retryDownload() {
        if (state.value.phase != AppUpdatePhase.FAILED) return
        val release = state.value.release ?: return
        cleanupDownloadedFiles()
        startDownload(release)
    }

    private fun startDownload(release: AppReleaseMetadata?) {
        release ?: return
        if (!installEnabled || currentPackageName != expectedPackageName) {
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
                    when (val verification = verifier.verify(
                        apkFile = downloaded,
                        metadata = release,
                        currentVersionCode = currentVersionCode.toLong()
                    )) {
                        is ApkVerificationResult.Failure -> {
                            logVerificationFailure(verification)
                            cleanupDownloadedFiles(downloaded)
                            showFailure(release, verification.reason, userMessage(verification.reason))
                        }
                        is ApkVerificationResult.Success -> {
                            verifiedApk = downloaded
                            _state.value = AppUpdateUiState(
                                phase = AppUpdatePhase.READY_TO_INSTALL,
                                release = release,
                                downloadProgress = 100,
                                message = "업데이트를 설치할 준비가 되었어요."
                            )
                        }
                    }
                } catch (error: UpdateDownloadException) {
                    val diagnostics = ApkVerificationDiagnostics(
                        expectedSize = error.expectedSize ?: release.fileSizeBytes,
                        actualSize = error.actualSize,
                        expectedSha256 = release.sha256,
                        expectedPackage = expectedPackageName,
                        currentVersion = currentVersionCode.toLong(),
                        expectedVersion = release.versionCode.toLong(),
                        detail = error.javaClass.simpleName
                    )
                    logVerificationFailure(ApkVerificationResult.Failure(error.failure, diagnostics))
                    cleanupDownloadedFiles(downloaded)
                    showFailure(release, error.failure, userMessage(error.failure))
                } catch (error: Throwable) {
                    logger(
                        "UpdateDownload: reason=${ApkVerificationFailure.UNKNOWN} sdk=$sdkInt " +
                            "error=${error.javaClass.simpleName}",
                        error
                    )
                    cleanupDownloadedFiles(downloaded)
                    showFailure(
                        release,
                        ApkVerificationFailure.UNKNOWN,
                        "다운로드에 실패했어요. 다시 시도해주세요."
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
            cleanupDownloadedFiles()
        }
        _state.value = AppUpdateUiState()
    }

    private fun launchInstaller(activity: Activity, apk: File) {
        _state.value = _state.value.copy(phase = AppUpdatePhase.INSTALLER_OPENED)
        runCatching { installer.openPackageInstaller(activity, apk) }
            .onFailure {
                logger("UpdateInstaller: sdk=$sdkInt error=${it.javaClass.simpleName}", it)
                failInstallation()
            }
    }

    private fun failInstallation() {
        val release = state.value.release
        cleanupDownloadedFiles()
        _state.value = AppUpdateUiState(
            phase = AppUpdatePhase.FAILED,
            release = release,
            message = "설치 화면을 열지 못했어요.",
            failureReason = ApkVerificationFailure.UNKNOWN
        )
    }

    private fun cleanupDownloadedFiles(file: File? = verifiedApk) {
        downloader.delete(file)
        downloader.clear()
        verifiedApk = null
    }

    private fun showFailure(
        release: AppReleaseMetadata,
        reason: ApkVerificationFailure,
        userMessage: String
    ) {
        val message = if (showTechnicalFailureReason) {
            "$userMessage\n\n업데이트 검증 실패: ${reason.name}"
        } else {
            userMessage
        }
        _state.value = AppUpdateUiState(
            phase = AppUpdatePhase.FAILED,
            release = release,
            message = message,
            failureReason = reason
        )
    }

    private fun logVerificationFailure(failure: ApkVerificationResult.Failure) {
        val diagnostic = failure.diagnostics
        logger(
            "UpdateVerification: reason=${failure.reason} sdk=$sdkInt " +
                "expectedSize=${diagnostic.expectedSize} actualSize=${diagnostic.actualSize} " +
                "expectedSha=${diagnostic.expectedSha256} actualSha=${diagnostic.actualSha256} " +
                "expectedPackage=${diagnostic.expectedPackage} actualPackage=${diagnostic.actualPackage} " +
                "currentVersion=${diagnostic.currentVersion} expectedVersion=${diagnostic.expectedVersion} " +
                "actualVersion=${diagnostic.actualVersion} " +
                "archiveSignerRead=${diagnostic.archiveSignerSha256.isNotEmpty()} " +
                "installedSignerRead=${diagnostic.installedSignerSha256.isNotEmpty()} " +
                "detail=${diagnostic.detail}",
            null
        )
    }

    private fun userMessage(reason: ApkVerificationFailure): String = when (reason) {
        ApkVerificationFailure.FILE_SIZE_MISMATCH,
        ApkVerificationFailure.DOWNLOAD_INCOMPLETE ->
            "업데이트 파일이 완전히 내려받아지지 않았어요. 다시 시도해주세요."
        ApkVerificationFailure.HASH_CALCULATION_FAILED,
        ApkVerificationFailure.HASH_MISMATCH ->
            "업데이트 파일을 확인하지 못했어요. 다시 다운로드해주세요."
        ApkVerificationFailure.APK_PARSE_FAILED,
        ApkVerificationFailure.SIGNER_READ_FAILED ->
            "이 기기에서 업데이트 파일 정보를 확인하지 못했어요. 다시 시도해주세요."
        ApkVerificationFailure.PACKAGE_MISMATCH,
        ApkVerificationFailure.SIGNER_MISMATCH ->
            "공식 업데이트 파일로 확인되지 않아 설치하지 않았어요."
        ApkVerificationFailure.VERSION_MISMATCH ->
            "업데이트 파일 버전이 올바르지 않아 설치하지 않았어요."
        ApkVerificationFailure.FILE_NOT_FOUND ->
            "다운로드한 업데이트 파일을 찾지 못했어요. 다시 시도해주세요."
        ApkVerificationFailure.UNKNOWN ->
            "업데이트를 진행하지 못했어요. 다시 시도해주세요."
    }

    private companion object {
        const val TAG = "AppUpdateManager"
    }
}
