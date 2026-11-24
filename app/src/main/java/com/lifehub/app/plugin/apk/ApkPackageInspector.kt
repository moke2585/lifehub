package com.lifehub.app.plugin.apk

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

/** 读取 APK 包信息与签名证书指纹，供插件发现与市场安装共用。 */
object ApkPackageInspector {

    @Suppress("DEPRECATION")
    fun signerFingerprint(packageManager: PackageManager, packageName: String): String? =
        runCatching {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES,
                )
            } else {
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            }
            signerFingerprint(info)
        }.getOrNull()

    @Suppress("DEPRECATION")
    fun signerFingerprint(info: PackageInfo): String? = runCatching {
        val certificate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            info.signatures?.firstOrNull()
        } ?: return@runCatching null
        MessageDigest.getInstance("SHA-256")
            .digest(certificate.toByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }.getOrNull()

    @Suppress("DEPRECATION")
    fun archiveInfo(
        packageManager: PackageManager,
        file: File,
        includeSignatures: Boolean,
    ): PackageInfo? = runCatching {
        val flags = if (!includeSignatures) {
            0
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        packageManager.getPackageArchiveInfo(file.absolutePath, flags)
    }.getOrNull()

    fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
}
