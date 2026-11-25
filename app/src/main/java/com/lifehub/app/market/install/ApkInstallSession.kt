package com.lifehub.app.market.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.IOException

/** 通过系统 PackageInstaller 安装已校验的 APK。 */
class ApkInstallSession(private val context: Context) {

    fun install(verified: VerifiedApk): Result<Unit> = runCatching {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL,
        ).apply {
            setAppPackageName(verified.applicationId)
        }
        val sessionId = installer.createSession(params)
        var committed = false
        try {
            installer.openSession(sessionId).use { session ->
                verified.file.inputStream().use { input ->
                    session.openWrite("base.apk", 0, verified.file.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                val intent = Intent(context, ApkInstallReceiver::class.java).apply {
                    action = ApkInstallReceiver.ACTION_INSTALL_STATUS
                    putExtra(ApkInstallReceiver.EXTRA_PACKAGE_NAME, verified.applicationId)
                }
                // PackageInstaller 会向状态 Intent 写入 extras，因此必须使用 mutable PendingIntent；
                // 目标 receiver 是显式且 exported=false，mutable 不会暴露给其他应用。
                val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PendingIntent.FLAG_MUTABLE
                    } else {
                        0
                    }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    intent,
                    pendingFlags,
                )
                session.commit(pendingIntent.intentSender)
                committed = true
            }
        } catch (error: Exception) {
            throw IOException("无法启动系统安装器：${error.message ?: "未知错误"}", error)
        } finally {
            if (!committed) {
                runCatching { installer.abandonSession(sessionId) }
            }
        }
    }
}
