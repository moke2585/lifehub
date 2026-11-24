package com.lifehub.app.market.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.lifehub.app.LifeHubApplication

/**
 * 接收 PackageInstaller 的会话状态：拉起系统确认界面，或在成功后刷新插件发现。
 */
class ApkInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_STATUS) return

        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE,
        )
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> launchConfirmation(context, intent)

            PackageInstaller.STATUS_SUCCESS -> {
                val application = context.applicationContext as? LifeHubApplication
                application?.apkPluginSource?.refresh()
                ApkInstallStatusBus.publish(
                    ApkInstallEvent.Success(
                        intent.getStringExtra(EXTRA_PACKAGE_NAME),
                    ),
                )
            }

            else -> {
                val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                ApkInstallStatusBus.publish(
                    ApkInstallEvent.Failure(
                        detail?.takeIf { it.isNotBlank() } ?: statusMessage(status),
                    ),
                )
            }
        }
    }

    private fun launchConfirmation(context: Context, intent: Intent) {
        @Suppress("DEPRECATION")
        val confirmation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
        }
        if (confirmation == null) {
            ApkInstallStatusBus.publish(ApkInstallEvent.Failure("系统安装器没有返回确认界面"))
            return
        }
        confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(confirmation) }
            .onFailure {
                ApkInstallStatusBus.publish(ApkInstallEvent.Failure("无法打开系统安装界面"))
            }
    }

    private fun statusMessage(status: Int): String = when (status) {
        PackageInstaller.STATUS_FAILURE_ABORTED -> "安装已取消"
        PackageInstaller.STATUS_FAILURE_BLOCKED -> "系统阻止了安装"
        PackageInstaller.STATUS_FAILURE_CONFLICT -> "与已安装应用冲突"
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "与当前系统不兼容"
        PackageInstaller.STATUS_FAILURE_INVALID -> "安装包无效"
        PackageInstaller.STATUS_FAILURE_STORAGE -> "存储空间不足"
        else -> "安装失败（$status）"
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "com.lifehub.app.apk.INSTALL_STATUS"
        const val EXTRA_PACKAGE_NAME = "packageName"
    }
}
