package com.lifehub.app.market.download

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

/**
 * 下载插件产物到缓存目录，并在写入过程中计算 SHA-256。
 *
 * 只允许 HTTPS；超限、哈希不符或中断都会删除 `.part` 文件。
 */
class ArtifactDownloader(
    private val dir: File,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 60_000,
) {

    fun download(
        url: String,
        expectedSha256: String,
        maxBytes: Long,
        fileName: String,
    ): File {
        require(SHA256.matches(expectedSha256)) { "产物 SHA-256 格式无效" }
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("无法创建下载目录")
        }
        val target = File(dir, safeName(fileName))
        val part = File(dir, "${target.name}.part")
        part.delete()

        var current = URI(url)
        try {
            repeat(MAX_REDIRECTS + 1) {
                require(current.scheme == "https") { "下载地址必须是 HTTPS" }
                val connection = current.toURL().openConnection() as HttpURLConnection
                try {
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = connectTimeoutMs
                    connection.readTimeout = readTimeoutMs
                    connection.setRequestProperty("Accept", "application/octet-stream")
                    connection.setRequestProperty("User-Agent", USER_AGENT)

                    val status = connection.responseCode
                    if (status in REDIRECT_CODES) {
                        val location = connection.getHeaderField("Location")
                            ?: throw IOException("下载重定向缺少 Location")
                        current = current.resolve(location)
                        return@repeat
                    }
                    if (status != HttpURLConnection.HTTP_OK) {
                        throw IOException("下载失败：HTTP $status")
                    }
                    val declared = connection.contentLengthLong
                    if (declared > maxBytes) {
                        throw IOException("产物超过 ${maxBytes / 1024 / 1024} MB 上限")
                    }

                    val digest = MessageDigest.getInstance("SHA-256")
                    var total = 0L
                    connection.inputStream.use { input ->
                        part.outputStream().use { output ->
                            val buffer = ByteArray(16 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                total += read
                                if (total > maxBytes) {
                                    throw IOException("产物超过 ${maxBytes / 1024 / 1024} MB 上限")
                                }
                                digest.update(buffer, 0, read)
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    val actual = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
                    if (actual != expectedSha256) {
                        throw IOException("下载内容 SHA-256 不匹配")
                    }
                    if (target.exists() && !target.delete()) {
                        throw IOException("无法替换缓存文件")
                    }
                    if (!part.renameTo(target)) {
                        throw IOException("无法保存下载文件")
                    }
                    return target
                } finally {
                    connection.disconnect()
                }
            }
            throw IOException("下载重定向次数过多")
        } catch (error: Exception) {
            part.delete()
            throw error
        }
    }

    private fun safeName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)

    private companion object {
        val SHA256 = Regex("^[0-9a-f]{64}$")
        const val MAX_REDIRECTS = 3
        const val USER_AGENT = "LifeHub"
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}
