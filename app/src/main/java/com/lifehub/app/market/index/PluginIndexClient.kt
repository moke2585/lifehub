package com.lifehub.app.market.index

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI

/** 索引请求的原始响应；304 时 [body] 为空。 */
data class IndexHttpResponse(
    val status: Int,
    val body: ByteArray?,
    val etag: String?,
    val lastModified: String?,
)

fun interface IndexHttpClient {
    fun get(url: String, etag: String?, lastModified: String?): IndexHttpResponse
}

/**
 * 最小 HTTPS 客户端：只用于拉取静态索引与签名，不引入额外依赖。
 */
class HttpIndexClient(
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 15_000,
    private val maxBytes: Int = MAX_INDEX_BYTES,
) : IndexHttpClient {

    override fun get(url: String, etag: String?, lastModified: String?): IndexHttpResponse {
        var current = URI(url)
        repeat(MAX_REDIRECTS + 1) {
            require(current.scheme == "https") { "索引地址必须是 HTTPS" }
            val connection = current.toURL().openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = connectTimeoutMs
                connection.readTimeout = readTimeoutMs
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", USER_AGENT)
                if (!etag.isNullOrBlank()) {
                    connection.setRequestProperty("If-None-Match", etag)
                }
                if (!lastModified.isNullOrBlank()) {
                    connection.setRequestProperty("If-Modified-Since", lastModified)
                }

                val status = connection.responseCode
                if (status in REDIRECT_CODES) {
                    val location = connection.getHeaderField("Location")
                        ?: throw IOException("索引重定向缺少 Location")
                    current = current.resolve(location)
                    return@repeat
                }
                if (status == HttpURLConnection.HTTP_NOT_MODIFIED) {
                    return IndexHttpResponse(
                        status = status,
                        body = null,
                        etag = connection.getHeaderField("ETag") ?: etag,
                        lastModified = connection.getHeaderField("Last-Modified") ?: lastModified,
                    )
                }
                if (status != HttpURLConnection.HTTP_OK) {
                    throw IOException("索引请求失败：HTTP $status")
                }
                val body = connection.inputStream.use { readBounded(it) }
                return IndexHttpResponse(
                    status = status,
                    body = body,
                    etag = connection.getHeaderField("ETag"),
                    lastModified = connection.getHeaderField("Last-Modified"),
                )
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("索引请求重定向次数过多")
    }

    private fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw IOException("索引超过 ${maxBytes / 1024} KiB 上限")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    companion object {
        const val MAX_INDEX_BYTES: Int = 1024 * 1024
        private const val MAX_REDIRECTS = 3
        private const val USER_AGENT = "LifeHub"
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}
