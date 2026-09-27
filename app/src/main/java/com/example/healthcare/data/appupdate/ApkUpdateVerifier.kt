package com.example.healthcare.data.appupdate

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

data class ApkIdentity(
    val packageName: String,
    val versionCode: Long,
    val signerSha256: Set<String>
)

interface ApkIdentityReader {
    fun readArchive(apkFile: File): ApkIdentity?
    fun readInstalled(packageName: String): ApkIdentity?
}

enum class ApkVerificationFailure {
    SHA256_MISMATCH,
    PACKAGE_MISMATCH,
    VERSION_NOT_HIGHER,
    VERSION_METADATA_MISMATCH,
    SIGNER_MISMATCH,
    APK_UNREADABLE
}

sealed interface ApkVerificationResult {
    data class Valid(val identity: ApkIdentity) : ApkVerificationResult
    data class Invalid(val failure: ApkVerificationFailure) : ApkVerificationResult
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
        currentVersionCode: Long,
        installedSignerSha256: Set<String>
    ): ApkVerificationResult {
        if (sha256(apkFile) != metadata.sha256.normalizedHex()) {
            return ApkVerificationResult.Invalid(ApkVerificationFailure.SHA256_MISMATCH)
        }
        val archive = identityReader.readArchive(apkFile)
            ?: return ApkVerificationResult.Invalid(ApkVerificationFailure.APK_UNREADABLE)
        if (archive.packageName != expectedPackageName) {
            return ApkVerificationResult.Invalid(ApkVerificationFailure.PACKAGE_MISMATCH)
        }
        if (archive.versionCode <= currentVersionCode) {
            return ApkVerificationResult.Invalid(ApkVerificationFailure.VERSION_NOT_HIGHER)
        }
        if (archive.versionCode != metadata.versionCode.toLong()) {
            return ApkVerificationResult.Invalid(ApkVerificationFailure.VERSION_METADATA_MISMATCH)
        }
        val archiveSigners = archive.signerSha256.map(String::normalizedHex).toSet()
        val installedSigners = installedSignerSha256.map(String::normalizedHex).toSet()
        if (knownSigner.isBlank() || knownSigner !in archiveSigners || knownSigner !in installedSigners ||
            archiveSigners != installedSigners
        ) {
            return ApkVerificationResult.Invalid(ApkVerificationFailure.SIGNER_MISMATCH)
        }
        return ApkVerificationResult.Valid(archive)
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

private fun String.normalizedHex(): String = trim().uppercase()
