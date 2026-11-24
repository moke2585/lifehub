package com.lifehub.app.market.index

import com.lifehub.app.plugin.runtime.index.IndexSignatureVerifier
import com.lifehub.app.plugin.runtime.index.PluginIndex
import com.lifehub.app.plugin.runtime.index.PluginIndexParser
import java.io.IOException

enum class PluginIndexSource { NETWORK, CACHE, BUNDLED }

data class PluginIndexSnapshot(
    val index: PluginIndex,
    val source: PluginIndexSource,
    val fetchedAt: Long?,
    val offline: Boolean,
    val error: String? = null,
)

/**
 * 索引拉取、验签与回退。
 *
 * 顺序：网络 → 有效缓存 → 内置快照；任何失败都不会影响已安装插件。
 */
class PluginIndexRepository(
    private val client: IndexHttpClient,
    private val cache: PluginIndexCache,
    private val bundledRegistry: () -> ByteArray,
    private val publicKey: (String) -> String?,
    private val indexUrl: String,
    private val signatureUrl: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    fun loadCached(): PluginIndexSnapshot {
        val cached = readValidCache()
        if (cached != null) {
            return snapshot(cached, PluginIndexSource.CACHE, offline = false)
        }
        return bundledSnapshot()
    }

    fun refresh(): PluginIndexSnapshot {
        val cached = readValidCache()
        return refreshOnce(cached, useCacheValidators = true)
    }

    private fun refreshOnce(
        cached: ValidCache?,
        useCacheValidators: Boolean,
    ): PluginIndexSnapshot {
        val response = try {
            client.get(
                url = indexUrl,
                etag = if (useCacheValidators) cached?.raw?.etag else null,
                lastModified = if (useCacheValidators) cached?.raw?.lastModified else null,
            )
        } catch (error: Exception) {
            return fallback(cached, error)
        }

        if (response.status == 304) {
            if (cached != null) {
                return snapshot(cached, PluginIndexSource.CACHE, offline = false)
            }
            if (useCacheValidators) {
                return refreshOnce(null, useCacheValidators = false)
            }
            return fallback(null, IOException("索引未修改但本地缓存缺失"))
        }

        val registryBytes = response.body
            ?: return fallback(cached, IOException("索引响应为空"))
        val signatureBytes = try {
            client.get(signatureUrl, etag = null, lastModified = null).body
                ?: throw IOException("索引签名响应为空")
        } catch (error: Exception) {
            return fallback(cached, error)
        }

        return try {
            val index = parseVerified(registryBytes, signatureBytes)
            val fetchedAt = clock()
            cache.write(
                registryBytes = registryBytes,
                signatureBytes = signatureBytes,
                etag = response.etag,
                lastModified = response.lastModified,
                fetchedAt = fetchedAt,
            )
            PluginIndexSnapshot(
                index = index,
                source = PluginIndexSource.NETWORK,
                fetchedAt = fetchedAt,
                offline = false,
            )
        } catch (error: Exception) {
            fallback(cached, error)
        }
    }

    private fun parseVerified(registryBytes: ByteArray, signatureBytes: ByteArray): PluginIndex {
        val signature = IndexSignatureVerifier.parse(signatureBytes.toString(Charsets.UTF_8))
            .getOrThrow()
        val pem = publicKey(signature.keyId)
            ?: error("未知索引签名密钥：${signature.keyId}")
        IndexSignatureVerifier.verify(registryBytes, signature, pem).getOrThrow()
        return PluginIndexParser.parse(registryBytes.toString(Charsets.UTF_8)).getOrThrow()
    }

    private fun readValidCache(): ValidCache? {
        val raw = cache.read() ?: return null
        return try {
            ValidCache(raw, parseVerified(raw.registryBytes, raw.signatureBytes))
        } catch (error: Exception) {
            cache.clear()
            null
        }
    }

    private fun fallback(cached: ValidCache?, error: Exception): PluginIndexSnapshot =
        if (cached != null) {
            snapshot(
                cached = cached,
                source = PluginIndexSource.CACHE,
                offline = true,
                error = error.message,
            )
        } else {
            bundledSnapshot(error = error.message)
        }

    private fun snapshot(
        cached: ValidCache,
        source: PluginIndexSource,
        offline: Boolean,
        error: String? = null,
    ): PluginIndexSnapshot = PluginIndexSnapshot(
        index = cached.index,
        source = source,
        fetchedAt = cached.raw.fetchedAt.takeIf { it > 0 },
        offline = offline,
        error = error,
    )

    private fun bundledSnapshot(error: String? = null): PluginIndexSnapshot {
        val bytes = bundledRegistry()
        val index = PluginIndexParser.parse(bytes.toString(Charsets.UTF_8)).getOrThrow()
        return PluginIndexSnapshot(
            index = index,
            source = PluginIndexSource.BUNDLED,
            fetchedAt = null,
            offline = true,
            error = error,
        )
    }

    private data class ValidCache(
        val raw: CachedPluginIndex,
        val index: PluginIndex,
    )
}
