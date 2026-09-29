package com.example.healthcare.data.appupdate

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

data class ApkIdentity(
    val packageName: String,
    val versionCode: Long,
    val signerSha256: Set<String>
)

enum class ApkIdentityReadFailure {
    APK_PARSE_FAILED,
    SIGNER_READ_FAILED
}

sealed interface ApkIdentityReadResult {
    data class Success(val identity: ApkIdentity) : ApkIdentityReadResult
    data class Failure(
        val reason: ApkIdentityReadFailure,
        val detail: String? = null
    ) : ApkIdentityReadResult
}

interface ApkIdentityReader {
    fun readArchive(apkFile: File): ApkIdentityReadResult
    fun readInstalled(packageName: String): ApkIdentityReadResult
}

enum class ApkVerificationFailure {
    FILE_NOT_FOUND,
    FILE_SIZE_MISMATCH,
    DOWNLOAD_INCOMPLETE,
    HASH_CALCULATION_FAILED,
    HASH_MISMATCH,
    APK_PARSE_FAILED,
    PACKAGE_MISMATCH,
    VERSION_MISMATCH,
    SIGNER_READ_FAILED,
    SIGNER_MISMATCH,
    UNKNOWN
}

data class ApkVerificationDiagnostics(
    val expectedSize: Long? = null,
    val actualSize: Long? = null,
    val expectedSha256: String? = null,
    val actualSha256: String? = null,
    val expectedPackage: String? = null,
    val actualPackage: String? = null,
    val currentVersion: Long? = null,
    val expectedVersion: Long? = null,
    val actualVersion: Long? = null,
    val expectedSignerSha256: String? = null,
    val archiveSignerSha256: Set<String> = emptySet(),
    val installedSignerSha256: Set<String> = emptySet(),
    val detail: String? = null
)

sealed interface ApkVerificationResult {
    data class Success(val identity: ApkIdentity) : ApkVerificationResult
    data class Failure(
        val reason: ApkVerificationFailure,
        val diagnostics: ApkVerificationDiagnostics
    ) : ApkVerificationResult
}

class ApkUpdateVerifier(
    private val identityReader: ApkIdentityReader,
    private val expectedPackageName: String,
    knownReleaseSignerSha256: String
) {
    private val knownSigner = knownReleaseSignerSha256.normalizedHex()

    fun verify(
        apkFile: File,
        metadata: AppReleaseMetadata,
        currentVersionCode: Long
    ): ApkVerificationResult {
        val baseDiagnostics = ApkVerificationDiagnostics(
            expectedSize = metadata.fileSizeBytes,
            actualSize = apkFile.takeIf(File::exists)?.length(),
            expectedSha256 = metadata.sha256.normalizedHex(),
            expectedPackage = expectedPackageName,
            currentVersion = currentVersionCode,
            expectedVersion = metadata.versionCode.toLong(),
            expectedSignerSha256 = knownSigner
        )
        if (!apkFile.exists() || !apkFile.isFile) {
            return ApkVerificationResult.Failure(
                ApkVerificationFailure.FILE_NOT_FOUND,
                baseDiagnostics
            )
        }
        if (apkFile.length() != metadata.fileSizeBytes) {
            return ApkVerificationResult.Failure(
                ApkVerificationFailure.FILE_SIZE_MISMATCH,
                baseDiagnostics.copy(actualSize = apkFile.length())
            )
        }

        val actualSha = runCatching { sha256(apkFile).normalizedHex() }.getOrElse { error ->
            return ApkVerificationResult.Failure(
                ApkVerificationFailure.HASH_CALCULATION_FAILED,
                baseDiagnostics.copy(detail = error.javaClass.simpleName)
            )
        }
        val hashDiagnostics = baseDiagnostics.copy(actualSha256 = actualSha)
        if (actualSha != metadata.sha256.normalizedHex()) {
            return ApkVerificationResult.Failure(
                ApkVerificationFailure.HASH_MISMATCH,
                hashDiagnostics
            )
        }

        val archive = when (val result = identityReader.readArchive(apkFile)) {
            is ApkIdentityReadResult.Success -> result.identity
            is ApkIdentityReadResult.Failure -> {
                return ApkVerificationResult.Failure(
                    result.reason.toVerificationFailure(),
                    hashDiagnostics.copy(detail = result.detail)
                )
            }
        }
        val archiveSigners = archive.signerSha256.map(String::normalizedHex).filter(String::isNotBlank).toSet()
        val archiveDiagnostics = hashDiagnostics.copy(
            actualPackage = archive.packageName,
            actualVersion = archive.versionCode,
            archiveSignerSha256 = archiveSigners
        )
        if (archive.packageName != expectedPackageName) {
            return ApkVerificationResult.Failure(
                ApkVerificationFailure.PACKAGE_MISMATCH,
                archiveDiagnostics
            )
        }
        if (archive.versionCode <= currentVersionCode || archive.versionCode != metadata.versionCode.toLong()) {
            return ApkVerificationResult.Failure(
                ApkVerificationFailure.VERSION_MISMATCH,
                archiveDiagnostics
            )
        }
        if (archiveSigners.isEmpty()) {
            return ApkVerificationResult.Failure(
                ApkVerificationFailure.SIGNER_READ_FAILED,
                archiveDiagnostics.copy(detail = "archive signer set is empty")
            )
        }

        val installed = when (val result = identityReader.readInstalled(expectedPackageName)) {
            is ApkIdentityReadResult.Success -> result.identity
            is ApkIdentityReadResult.Failure -> {
                return ApkVerificationResult.Failure(
                    result.reason.toVerificationFailure(),
                    archiveDiagnostics.copy(detail = result.detail)
                )
            }
        }
        val installedSigners = installed.signerSha256
            .map(String::normalizedHex)
            .filter(String::isNotBlank)
            .toSet()
        val signerDiagnostics = archiveDiagnostics.copy(installedSignerSha256 = installedSigners)
        if (installedSigners.isEmpty()) {
            return ApkVerificationResult.Failure(
                ApkVerificationFailure.SIGNER_READ_FAILED,
                signerDiagnostics.copy(detail = "installed signer set is empty")
            )
        }
        if (knownSigner.isBlank() || knownSigner !in archiveSigners || knownSigner !in installedSigners) {
            return ApkVerificationResult.Failure(
                ApkVerificationFailure.SIGNER_MISMATCH,
                signerDiagnostics
            )
        }
        return ApkVerificationResult.Success(archive)
    }

    companion object {
        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > 0) digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02X".format(it) }
        }
    }
}

private fun ApkIdentityReadFailure.toVerificationFailure(): ApkVerificationFailure = when (this) {
    ApkIdentityReadFailure.APK_PARSE_FAILED -> ApkVerificationFailure.APK_PARSE_FAILED
    ApkIdentityReadFailure.SIGNER_READ_FAILED -> ApkVerificationFailure.SIGNER_READ_FAILED
}

private fun String.normalizedHex(): String = trim().uppercase()
