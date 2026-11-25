package com.lifehub.app.plugin.apk

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.lifehub.pluginapi.PluginActionRequest
import com.lifehub.pluginapi.PluginActionResult
import com.lifehub.pluginapi.PluginApkProtocol
import com.lifehub.pluginapi.PluginDataResult
import com.lifehub.pluginapi.PluginDataSnapshot
import com.lifehub.pluginapi.PluginDescriptor
import com.lifehub.pluginapi.PluginIssue
import com.lifehub.pluginapi.PluginIssueCode
import com.lifehub.pluginapi.PluginManifestParser
import com.lifehub.pluginapi.PluginPageDataRequest
import com.lifehub.pluginapi.PluginPathRules
import com.lifehub.pluginapi.PluginSource
import com.lifehub.pluginapi.PluginStatus
import com.lifehub.app.plugin.PluginPackageSource
import com.lifehub.app.plugin.PluginPackageTrust
import java.io.InputStream
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

data class ApkPluginHandle(
    val pluginId: String,
    val packageName: String,
    val authority: String,
    val versionCode: Long,
    val signerFingerprint: String,
)

/**
 * 通过自定义 intent + ContentProvider 发现已安装的 APK 插件。
 *
 * 页面仍由宿主渲染；action/data/备份通过 Binder 调用插件 provider，
 * provider 内部使用自己的 SQLite 数据库。
 */
class ApkPluginPackageSource(
    private val context: Context,
    private val log: (String) -> Unit = {},
) : PluginPackageSource {

    private val resolver = context.contentResolver
    private val packageManager = context.packageManager
    private val seenPreferences = context.getSharedPreferences(
        SEEN_PREFERENCES,
        Context.MODE_PRIVATE,
    )

    @Volatile
    private var handles: Map<String, ApkPluginHandle>? = null

    override fun pluginIds(): List<String> = discover().keys.sorted()

    override fun trustFor(pluginId: String): PluginPackageTrust =
        if (discover().containsKey(pluginId)) {
            PluginPackageTrust.APK_PLUGIN_SIGNED
        } else {
            PluginPackageTrust.UNTRUSTED
        }

    override fun placeholders(): List<PluginDescriptor> {
        val installed = discover().keys
        return seenPreferences.all.mapNotNull { (key, value) ->
            if (!key.startsWith(SEEN_NAME_PREFIX)) return@mapNotNull null
            val pluginId = key.removePrefix(SEEN_NAME_PREFIX)
            if (pluginId in installed) return@mapNotNull null
            val displayName = value as? String ?: pluginId
            PluginDescriptor(
                pluginId = pluginId,
                displayName = displayName,
                manifest = null,
                status = PluginStatus.NOT_INSTALLED,
                source = PluginSource.EXTERNAL_APK,
                issues = listOf(
                    PluginIssue(
                        code = PluginIssueCode.MANIFEST_UNREADABLE,
                        message = "插件 APK 未安装：$displayName",
                    ),
                ),
            )
        }
    }

    override fun open(pluginId: String, relativePath: String): InputStream? {
        if (!PluginPathRules.isSafeRelativePath(relativePath)) return null
        val handle = discover()[pluginId] ?: return null
        val method = when (relativePath) {
            MANIFEST_FILE -> PluginApkProtocol.METHOD_MANIFEST
            SIGNATURE_FILE -> return null
            else -> PluginApkProtocol.METHOD_PAGE
        }
        val argument = if (method == PluginApkProtocol.METHOD_PAGE) relativePath else null
        val json = call(handle, method, argument)?.json() ?: return null
        return json.toByteArray(Charsets.UTF_8).inputStream()
    }

    fun refresh() {
        handles = null
    }

    fun installedHandle(pluginId: String): ApkPluginHandle? = discover()[pluginId]

    fun dispatchAction(request: PluginActionRequest): PluginActionResult {
        val handle = discover()[request.pluginId]
            ?: return PluginActionResult.Failure("插件 APK 未安装：${request.pluginId}")
        val payload = PluginApkProtocol.json.encodeToString(request)
        if (payload.toByteArray(Charsets.UTF_8).size > PluginApkProtocol.MAX_PAYLOAD_BYTES) {
            return PluginActionResult.Failure("插件请求超过 Binder 负载上限")
        }
        val json = call(handle, PluginApkProtocol.METHOD_ACTION, payload)?.json()
            ?: return PluginActionResult.Failure("插件 APK 没有响应：${request.actionId}")
        return runCatching {
            PluginApkProtocol.json.decodeFromString<PluginActionResult>(json)
        }.getOrElse { error ->
            PluginActionResult.Failure("插件 APK 返回了无效结果：${error.message ?: "未知错误"}")
        }
    }

    fun dispatchData(request: PluginPageDataRequest): PluginDataResult {
        val handle = discover()[request.pluginId]
            ?: return PluginDataResult(
                emptyTitle = "无法加载",
                emptyMessage = "插件 APK 未安装：${request.pluginId}",
                isError = true,
            )
        val payload = PluginApkProtocol.json.encodeToString(request)
        if (payload.toByteArray(Charsets.UTF_8).size > PluginApkProtocol.MAX_PAYLOAD_BYTES) {
            return PluginDataResult(
                emptyTitle = "无法加载",
                emptyMessage = "插件请求超过 Binder 负载上限",
                isError = true,
            )
        }
        val json = call(handle, PluginApkProtocol.METHOD_DATA, payload)?.json()
            ?: return PluginDataResult(
                emptyTitle = "无法加载",
                emptyMessage = "插件 APK 没有响应：${request.sourceId}",
                isError = true,
            )
        return runCatching {
            PluginApkProtocol.json.decodeFromString<PluginDataResult>(json)
        }.getOrElse { error ->
            PluginDataResult(
                emptyTitle = "无法加载",
                emptyMessage = "插件 APK 返回了无效数据：${error.message ?: "未知错误"}",
                isError = true,
            )
        }
    }

    fun exportBackup(pluginId: String): String? {
        val handle = discover()[pluginId] ?: return null
        return call(handle, PluginApkProtocol.METHOD_EXPORT_BACKUP, null)?.json()
    }

    fun importBackup(pluginId: String, payload: String): Result<Unit> {
        val handle = discover()[pluginId]
            ?: return Result.failure(IllegalStateException("插件 APK 未安装：$pluginId"))
        if (payload.toByteArray(Charsets.UTF_8).size > PluginApkProtocol.MAX_PAYLOAD_BYTES) {
            return Result.failure(IllegalStateException("备份数据超过 Binder 负载上限"))
        }
        val bundle = call(handle, PluginApkProtocol.METHOD_IMPORT_BACKUP, payload)
            ?: return Result.failure(IllegalStateException("插件 APK 没有响应"))
        if (!bundle.getBoolean(PluginApkProtocol.KEY_OK, false)) {
            return Result.failure(
                IllegalStateException(
                    bundle.getString(PluginApkProtocol.KEY_ERROR) ?: "插件 APK 导入失败",
                ),
            )
        }
        return Result.success(Unit)
    }

    fun backupRecordCount(pluginId: String): Int? {
        val payload = exportBackup(pluginId) ?: return null
        return runCatching {
            PluginApkProtocol.json.decodeFromString<PluginDataSnapshot>(payload).recordCount
        }.getOrNull()
    }

    private fun discover(): Map<String, ApkPluginHandle> {
        handles?.let { return it }
        synchronized(this) {
            handles?.let { return it }
            val discovered = linkedMapOf<String, ApkPluginHandle>()
            val intent = Intent(PluginApkProtocol.ACTION_DISCOVER)
            val providers = runCatching {
                packageManager.queryIntentContentProviders(intent, 0)
            }.getOrElse { error ->
                log("apk_plugin_query_failed error=${error.message}")
                emptyList()
            }
            providers.forEach { resolve ->
                val providerInfo = resolve.providerInfo ?: return@forEach
                if (providerInfo.packageName == context.packageName) return@forEach
                if (!providerInfo.exported) return@forEach
                val authority = providerInfo.authority ?: return@forEach

                val versionBundle = callRaw(authority, PluginApkProtocol.METHOD_VERSION, null)
                    ?: return@forEach
                val version = versionBundle.getInt(PluginApkProtocol.KEY_VERSION, 0)
                if (!PluginApkProtocol.isCompatible(version)) {
                    log("apk_plugin_version_unsupported package=${providerInfo.packageName} version=$version")
                    return@forEach
                }

                val manifestJson = callRaw(authority, PluginApkProtocol.METHOD_MANIFEST, null)
                    ?.json()
                    ?: return@forEach
                val manifest = PluginManifestParser.parse(manifestJson).getOrNull() ?: return@forEach
                if (discovered.containsKey(manifest.pluginId)) {
                    log("apk_plugin_duplicate pluginId=${manifest.pluginId}")
                    return@forEach
                }

                val signer = ApkPackageInspector.signerFingerprint(
                    packageManager,
                    providerInfo.packageName,
                ) ?: return@forEach
                discovered[manifest.pluginId] = ApkPluginHandle(
                    pluginId = manifest.pluginId,
                    packageName = providerInfo.packageName,
                    authority = authority,
                    versionCode = packageVersionCode(providerInfo.packageName),
                    signerFingerprint = signer,
                )
                seenPreferences.edit()
                    .putString("$SEEN_NAME_PREFIX${manifest.pluginId}", manifest.name)
                    .apply()
            }
            // 空的发现结果不缓存：插件进程可能刚被系统冻结，下一次调用重试。
            handles = discovered.takeIf { it.isNotEmpty() }
            return discovered
        }
    }

    private fun call(
        handle: ApkPluginHandle,
        method: String,
        argument: String?,
    ): Bundle? = callRaw(handle.authority, method, argument)

    private fun callRaw(authority: String, method: String, argument: String?): Bundle? {
        val started = System.nanoTime()
        val result = runCatching {
            resolver.call(Uri.parse("content://$authority"), method, argument, null)
        }.onFailure { error ->
            log("apk_plugin_call_failed authority=$authority method=$method error=${error.message}")
        }.getOrNull()
        val durationMs = (System.nanoTime() - started) / 1_000_000
        log(
            "apk_plugin_call authority=$authority method=$method " +
                "requestChars=${argument?.length ?: 0} " +
                "responseChars=${result?.getString(PluginApkProtocol.KEY_JSON)?.length ?: 0} " +
                "durationMs=$durationMs",
        )
        return result
    }

    private fun Bundle.json(): String? {
        if (!getBoolean(PluginApkProtocol.KEY_OK, true)) {
            log("apk_plugin_error error=${getString(PluginApkProtocol.KEY_ERROR)}")
            return null
        }
        return getString(PluginApkProtocol.KEY_JSON)
    }

    private fun packageVersionCode(packageName: String): Long {
        val info = runCatching {
            packageManager.getPackageInfo(packageName, 0)
        }.getOrNull() ?: return 0L
        return ApkPackageInspector.versionCode(info)
    }

    private companion object {
        const val MANIFEST_FILE = "manifest.json"
        const val SIGNATURE_FILE = "manifest.sig"
        const val SEEN_PREFERENCES = "apk_plugin_seen"
        const val SEEN_NAME_PREFIX = "name_"
    }
}
