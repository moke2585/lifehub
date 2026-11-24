package com.lifehub.app.market.index

import java.io.File
import java.io.IOException
import java.util.Properties

data class CachedPluginIndex(
    val registryBytes: ByteArray,
    val signatureBytes: ByteArray,
    val etag: String?,
    val lastModified: String?,
    val fetchedAt: Long,
)

/**
 * 索引缓存：`registry.json`、`registry.sig` 与 `meta.properties`。
 *
 * 写入使用 `.tmp` + rename，避免进程中断留下半个文件。
 */
class PluginIndexCache(private val dir: File) {

    fun read(): CachedPluginIndex? {
        val registry = File(dir, REGISTRY_FILE)
        val signature = File(dir, SIGNATURE_FILE)
        if (!registry.isFile || !signature.isFile) return null
        if (registry.length() > MAX_REGISTRY_BYTES || signature.length() > MAX_SIGNATURE_BYTES) {
            return null
        }
        val meta = readMeta()
        return CachedPluginIndex(
            registryBytes = registry.readBytes(),
            signatureBytes = signature.readBytes(),
            etag = meta?.getProperty(KEY_ETAG),
            lastModified = meta?.getProperty(KEY_LAST_MODIFIED),
            fetchedAt = meta?.getProperty(KEY_FETCHED_AT)?.toLongOrNull() ?: 0L,
        )
    }

    fun write(
        registryBytes: ByteArray,
        signatureBytes: ByteArray,
        etag: String?,
        lastModified: String?,
        fetchedAt: Long,
    ) {
        require(registryBytes.size <= MAX_REGISTRY_BYTES) { "索引过大" }
        require(signatureBytes.size <= MAX_SIGNATURE_BYTES) { "签名过大" }
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("无法创建索引缓存目录")
        }
        writeAtomic(File(dir, REGISTRY_FILE), registryBytes)
        writeAtomic(File(dir, SIGNATURE_FILE), signatureBytes)
        val meta = Properties().apply {
            etag?.let { setProperty(KEY_ETAG, it) }
            lastModified?.let { setProperty(KEY_LAST_MODIFIED, it) }
            setProperty(KEY_FETCHED_AT, fetchedAt.toString())
        }
        val text = buildString {
            meta.forEach { key, value -> append(key).append('=').append(value).append('\n') }
        }
        writeAtomic(File(dir, META_FILE), text.toByteArray(Charsets.UTF_8))
    }

    fun clear() {
        dir.deleteRecursively()
    }

    private fun readMeta(): Properties? {
        val file = File(dir, META_FILE)
        if (!file.isFile) return null
        return runCatching {
            Properties().apply { file.inputStream().use { load(it) } }
        }.getOrNull()
    }

    private fun writeAtomic(target: File, bytes: ByteArray) {
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeBytes(bytes)
        if (target.exists() && !target.delete()) {
            temp.delete()
            throw IOException("无法替换缓存文件：${target.name}")
        }
        if (!temp.renameTo(target)) {
            temp.delete()
            throw IOException("无法写入缓存文件：${target.name}")
        }
    }

    private companion object {
        const val REGISTRY_FILE = "registry.json"
        const val SIGNATURE_FILE = "registry.sig"
        const val META_FILE = "meta.properties"
        const val KEY_ETAG = "etag"
        const val KEY_LAST_MODIFIED = "lastModified"
        const val KEY_FETCHED_AT = "fetchedAt"
        const val MAX_REGISTRY_BYTES = 1024 * 1024
        const val MAX_SIGNATURE_BYTES = 16 * 1024
    }
}
