package com.example.healthcare.data.appupdate

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.io.File
import java.security.MessageDigest

internal enum class SigningQueryMode {
    MODERN,
    LEGACY
}

internal object ApkSigningCompatibility {
    fun queryModes(sdkInt: Int): List<SigningQueryMode> =
        if (sdkInt >= Build.VERSION_CODES.P) {
            listOf(SigningQueryMode.MODERN, SigningQueryMode.LEGACY)
        } else {
            listOf(SigningQueryMode.LEGACY)
        }
}

class AndroidApkIdentityReader(
    private val context: Context,
    private val sdkInt: Int = Build.VERSION.SDK_INT
) : ApkIdentityReader {
    private val packageManager: PackageManager = context.packageManager

    override fun readArchive(apkFile: File): ApkIdentityReadResult {
        if (!apkFile.exists() || !apkFile.isFile) {
            return ApkIdentityReadResult.Failure(
                ApkIdentityReadFailure.APK_PARSE_FAILED,
                "archive file is missing"
            )
        }
        var parsedPackage: PackageInfo? = null
        ApkSigningCompatibility.queryModes(sdkInt).forEach { mode ->
            val packageInfo = runCatching { archivePackageInfo(apkFile, mode) }.getOrNull()
                ?: return@forEach
            if (parsedPackage == null) parsedPackage = packageInfo
            val signers = runCatching { packageInfo.signerDigests() }.getOrDefault(emptySet())
            if (signers.isNotEmpty()) {
                return ApkIdentityReadResult.Success(packageInfo.toIdentity(signers))
            }
        }
        return if (parsedPackage == null) {
            ApkIdentityReadResult.Failure(
                ApkIdentityReadFailure.APK_PARSE_FAILED,
                "PackageManager could not parse archive"
            )
        } else {
            ApkIdentityReadResult.Failure(
                ApkIdentityReadFailure.SIGNER_READ_FAILED,
                "archive parsed but signer certificates were unavailable"
            )
        }
    }

    override fun readInstalled(packageName: String): ApkIdentityReadResult {
        var parsedPackage: PackageInfo? = null
        ApkSigningCompatibility.queryModes(sdkInt).forEach { mode ->
            val packageInfo = runCatching { installedPackageInfo(packageName, mode) }.getOrNull()
                ?: return@forEach
            if (parsedPackage == null) parsedPackage = packageInfo
            val signers = runCatching { packageInfo.signerDigests() }.getOrDefault(emptySet())
            if (signers.isNotEmpty()) {
                return ApkIdentityReadResult.Success(packageInfo.toIdentity(signers))
            }
        }
        return ApkIdentityReadResult.Failure(
            ApkIdentityReadFailure.SIGNER_READ_FAILED,
            if (parsedPackage == null) {
                "installed package info was unavailable"
            } else {
                "installed package signer certificates were unavailable"
            }
        )
    }

    @SuppressLint("NewApi")
    @Suppress("DEPRECATION")
    private fun archivePackageInfo(apkFile: File, mode: SigningQueryMode): PackageInfo? {
        val flags = mode.packageManagerFlag()
        return if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.PackageInfoFlags.of(flags.toLong())
            )
        } else {
            packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
        }
    }

    @SuppressLint("NewApi")
    @Suppress("DEPRECATION")
    private fun installedPackageInfo(packageName: String, mode: SigningQueryMode): PackageInfo {
        val flags = mode.packageManagerFlag()
        return if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(flags.toLong())
            )
        } else {
            packageManager.getPackageInfo(packageName, flags)
        }
    }

    @SuppressLint("NewApi")
    @Suppress("DEPRECATION")
    private fun PackageInfo.signerDigests(): Set<String> {
        val certificates = linkedSetOf<Signature>()
        if (sdkInt >= Build.VERSION_CODES.P) {
            signingInfo?.let { info ->
                certificates += info.apkContentsSigners.orEmpty()
                certificates += info.signingCertificateHistory.orEmpty()
            }
        }
        certificates += signatures.orEmpty()
        return certificates.mapTo(linkedSetOf()) { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") { "%02X".format(it) }
        }
    }

    @SuppressLint("NewApi")
    @Suppress("DEPRECATION")
    private fun PackageInfo.toIdentity(signers: Set<String>): ApkIdentity = ApkIdentity(
        packageName = packageName,
        versionCode = if (sdkInt >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong(),
        signerSha256 = signers
    )

    @SuppressLint("InlinedApi")
    @Suppress("DEPRECATION")
    private fun SigningQueryMode.packageManagerFlag(): Int = when (this) {
        SigningQueryMode.MODERN -> PackageManager.GET_SIGNING_CERTIFICATES
        SigningQueryMode.LEGACY -> PackageManager.GET_SIGNATURES
    }
}
