package com.lifehub.app.market.install

import android.content.pm.PackageManager
import com.lifehub.app.plugin.apk.ApkPackageInspector
import com.lifehub.app.plugin.runtime.index.IndexedPlugin
import java.io.File

data class VerifiedApk(
    val entry: IndexedPlugin,
    val file: File,
    val applicationId: String,
    val versionName: String?,
    val versionCode: Long,
    val signerFingerprint: String,
)

/**
 * 安装前校验下载的 APK：包名、版本、versionCode 与证书指纹必须与索引一致。
 */
class ApkArtifactVerifier(private val packageManager: PackageManager) {

    fun verify(
        file: File,
        entry: IndexedPlugin,
        installedVersionCode: Long?,
        installedSigner: String?,
    ): Result<VerifiedApk> = runCatching {
        require(entry.isApk) { "索引条目不是 APK" }
        val expectedApplicationId = entry.applicationId
            ?: error("索引缺少 applicationId")
        val expectedVersionCode = entry.versionCode
            ?: error("索引缺少 versionCode")

        val info = ApkPackageInspector.archiveInfo(
            packageManager = packageManager,
            file = file,
            includeSignatures = true,
        ) ?: error("无法读取 APK 信息")

        require(info.packageName == expectedApplicationId) {
            "APK 包名与索引不一致：${info.packageName}"
        }
        val versionCode = ApkPackageInspector.versionCode(info)
        require(versionCode == expectedVersionCode) {
            "APK versionCode 与索引不一致：$versionCode"
        }
        require(info.versionName == entry.version) {
            "APK 版本名与索引不一致：${info.versionName}"
        }
        val signer = ApkPackageInspector.signerFingerprint(info)
            ?: error("无法读取 APK 签名证书")
        require(signer == entry.signerSha256) {
            "APK 签名者与索引不一致"
        }
        if (installedSigner != null) {
            require(installedSigner == signer) { "签名者与已安装版本不一致" }
        }
        if (installedVersionCode != null) {
            require(versionCode >= installedVersionCode) { "不能降级安装" }
        }

        VerifiedApk(
            entry = entry,
            file = file,
            applicationId = info.packageName,
            versionName = info.versionName,
            versionCode = versionCode,
            signerFingerprint = signer,
        )
    }
}
