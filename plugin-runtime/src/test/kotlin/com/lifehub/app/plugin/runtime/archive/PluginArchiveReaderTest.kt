package com.lifehub.app.plugin.runtime.archive

import com.lifehub.app.plugins.testing.PluginArchiveTestBuilder
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginArchiveReaderTest {

    @Test
    fun validArchiveIsParsedAndVerified() {
        val archive = PluginArchiveTestBuilder.archive()

        val result = PluginArchiveReader.read(archive)

        assertTrue(result.toString(), result is PluginArchiveResult.Success)
        val content = (result as PluginArchiveResult.Success).content
        assertEquals(PluginArchiveTestBuilder.DEFAULT_PLUGIN_ID, content.pluginId)
        assertEquals(
            setOf("manifest.json", "pages/home.json"),
            content.files.keys,
        )
        assertEquals(64, content.signer.fingerprint.length)
        assertEquals(PluginArchiveTestBuilder.DEFAULT_KEY_ID, content.signer.keyId)
    }

    @Test
    fun missingSignatureIsRejected() {
        val files = PluginArchiveTestBuilder.validFiles()

        val result = PluginArchiveReader.read(
            PluginArchiveTestBuilder.zip(PluginArchiveTestBuilder.DEFAULT_PLUGIN_ID, files),
        )

        assertTrue(result.toString(), result is PluginArchiveResult.Failure)
        assertTrue((result as PluginArchiveResult.Failure).message.contains("manifest.sig"))
    }

    @Test
    fun tamperedFileIsRejected() {
        val keyPair = PluginArchiveTestBuilder.keyPair()
        val files = PluginArchiveTestBuilder.validFiles()
        val signature = PluginArchiveTestBuilder.signature(
            pluginId = PluginArchiveTestBuilder.DEFAULT_PLUGIN_ID,
            version = "1.0.0",
            files = files,
            keyPair = keyPair,
        )
        val tampered = files + ("pages/home.json" to files.getValue("pages/home.json").copyOf().apply {
            this[0] = ' '.code.toByte()
        })
        val archive = PluginArchiveTestBuilder.zip(
            PluginArchiveTestBuilder.DEFAULT_PLUGIN_ID,
            tampered + ("manifest.sig" to signature.toByteArray()),
        )

        val result = PluginArchiveReader.read(archive)

        assertTrue(result is PluginArchiveResult.Failure)
        assertTrue(
            (result as PluginArchiveResult.Failure).message.contains("摘要不匹配"),
        )
    }

    @Test
    fun signaturePayloadFromOtherFilesIsRejected() {
        val keyPair = PluginArchiveTestBuilder.keyPair()
        val files = PluginArchiveTestBuilder.validFiles()
        val otherFiles = files + ("data/extra.txt" to "extra".toByteArray())
        val signature = PluginArchiveTestBuilder.signature(
            pluginId = PluginArchiveTestBuilder.DEFAULT_PLUGIN_ID,
            version = "1.0.0",
            files = otherFiles,
            keyPair = keyPair,
        )
        val archive = PluginArchiveTestBuilder.zip(
            PluginArchiveTestBuilder.DEFAULT_PLUGIN_ID,
            files + ("manifest.sig" to signature.toByteArray()),
        )

        val result = PluginArchiveReader.read(archive)

        assertTrue(result is PluginArchiveResult.Failure)
        assertTrue(
            (result as PluginArchiveResult.Failure).message.contains("不一致"),
        )
    }

    @Test
    fun extraUnsignedFileIsRejected() {
        val keyPair = PluginArchiveTestBuilder.keyPair()
        val files = PluginArchiveTestBuilder.validFiles()
        val signature = PluginArchiveTestBuilder.signature(
            PluginArchiveTestBuilder.DEFAULT_PLUGIN_ID,
            "1.0.0",
            files,
            keyPair,
        )
        val withExtra = PluginArchiveTestBuilder.zip(
            PluginArchiveTestBuilder.DEFAULT_PLUGIN_ID,
            files + ("data/extra.txt" to "extra".toByteArray()) +
                ("manifest.sig" to signature.toByteArray()),
        )

        val result = PluginArchiveReader.read(withExtra)

        assertTrue(result is PluginArchiveResult.Failure)
        assertTrue(
            (result as PluginArchiveResult.Failure).message.contains("未签名"),
        )
    }

    @Test
    fun pathTraversalIsRejected() {
        val archive = PluginArchiveTestBuilder.zip(
            PluginArchiveTestBuilder.DEFAULT_PLUGIN_ID,
            mapOf("../evil.txt" to "x".toByteArray()),
        )

        val result = PluginArchiveReader.read(archive)

        assertTrue(result is PluginArchiveResult.Failure)
        assertTrue(
            (result as PluginArchiveResult.Failure).message.contains("不安全路径"),
        )
    }

    @Test
    fun duplicateEntryIsRejected() {
        val files = PluginArchiveTestBuilder.validFiles()
        val archive = rawZip(
            listOf(
                "${PluginArchiveTestBuilder.DEFAULT_PLUGIN_ID}/manifest.json" to
                    files.getValue("manifest.json"),
                "${PluginArchiveTestBuilder.DEFAULT_PLUGIN_ID}/manifest.json" to
                    files.getValue("manifest.json"),
            ),
        )

        val result = PluginArchiveReader.read(archive)

        assertTrue(result is PluginArchiveResult.Failure)
        assertTrue(
            (result as PluginArchiveResult.Failure).message,
            result.message.contains("重复条目"),
        )
    }

    @Test
    fun executableEntryIsRejected() {
        val archive = PluginArchiveTestBuilder.zip(
            PluginArchiveTestBuilder.DEFAULT_PLUGIN_ID,
            mapOf("lib/evil.so" to "x".toByteArray()),
        )

        val result = PluginArchiveReader.read(archive)

        assertTrue(result is PluginArchiveResult.Failure)
        assertTrue(
            (result as PluginArchiveResult.Failure).message.contains("可执行文件"),
        )
    }

    @Test
    fun topLevelDirectoryMustMatchPluginId() {
        val archive = PluginArchiveTestBuilder.archive(root = "com.example.other")

        val result = PluginArchiveReader.read(archive)

        assertTrue(result is PluginArchiveResult.Failure)
        assertTrue(
            (result as PluginArchiveResult.Failure).message.contains("顶层目录"),
        )
    }

    @Test
    fun signatureVersionMismatchIsRejected() {
        val keyPair = PluginArchiveTestBuilder.keyPair()
        val files = PluginArchiveTestBuilder.validFiles()
        val signature = PluginArchiveTestBuilder.signature(
            PluginArchiveTestBuilder.DEFAULT_PLUGIN_ID,
            "2.0.0",
            files,
            keyPair,
        )
        val archive = PluginArchiveTestBuilder.zip(
            PluginArchiveTestBuilder.DEFAULT_PLUGIN_ID,
            files + ("manifest.sig" to signature.toByteArray()),
        )

        val result = PluginArchiveReader.read(archive)

        assertTrue(result is PluginArchiveResult.Failure)
        assertTrue(
            (result as PluginArchiveResult.Failure).message.contains("version"),
        )
    }

    @Test
    fun oversizedManifestIsRejected() {
        val archive = PluginArchiveTestBuilder.zip(
            PluginArchiveTestBuilder.DEFAULT_PLUGIN_ID,
            mapOf("manifest.json" to ByteArray(PluginArchiveReader.MAX_MANIFEST_BYTES + 1)),
        )

        val result = PluginArchiveReader.read(archive)

        assertTrue(result is PluginArchiveResult.Failure)
        assertTrue(
            (result as PluginArchiveResult.Failure).message.contains("上限"),
        )
    }

    /** 手工写入 stored ZIP，允许重复条目（ZipOutputStream 会拒绝重复名）。 */
    private fun rawZip(entries: List<Pair<String, ByteArray>>): ByteArray {
        val output = ByteArrayOutputStream()
        val data = DataOutputStream(output)
        val central = ByteArrayOutputStream()
        val centralData = DataOutputStream(central)

        entries.forEach { (name, bytes) ->
            val nameBytes = name.toByteArray(Charsets.UTF_8)
            val crc = CRC32().apply { update(bytes) }.value
            val offset = output.size()

            data.writeIntLE(0x04034b50)
            data.writeShortLE(20)
            data.writeShortLE(0)
            data.writeShortLE(0)
            data.writeShortLE(0)
            data.writeShortLE(0)
            data.writeIntLE(crc.toInt())
            data.writeIntLE(bytes.size)
            data.writeIntLE(bytes.size)
            data.writeShortLE(nameBytes.size)
            data.writeShortLE(0)
            data.write(nameBytes)
            data.write(bytes)

            centralData.writeIntLE(0x02014b50)
            centralData.writeShortLE(20)
            centralData.writeShortLE(20)
            centralData.writeShortLE(0)
            centralData.writeShortLE(0)
            centralData.writeShortLE(0)
            centralData.writeShortLE(0)
            centralData.writeIntLE(crc.toInt())
            centralData.writeIntLE(bytes.size)
            centralData.writeIntLE(bytes.size)
            centralData.writeShortLE(nameBytes.size)
            centralData.writeShortLE(0)
            centralData.writeShortLE(0)
            centralData.writeShortLE(0)
            centralData.writeShortLE(0)
            centralData.writeIntLE(0)
            centralData.writeIntLE(offset)
            centralData.write(nameBytes)
        }

        val centralBytes = central.toByteArray()
        val centralOffset = output.size()
        data.write(centralBytes)
        data.writeIntLE(0x06054b50)
        data.writeShortLE(0)
        data.writeShortLE(0)
        data.writeShortLE(entries.size)
        data.writeShortLE(entries.size)
        data.writeIntLE(centralBytes.size)
        data.writeIntLE(centralOffset)
        data.writeShortLE(0)
        return output.toByteArray()
    }

    private fun DataOutputStream.writeShortLE(value: Int) {
        writeByte(value and 0xFF)
        writeByte((value ushr 8) and 0xFF)
    }

    private fun DataOutputStream.writeIntLE(value: Int) {
        writeByte(value and 0xFF)
        writeByte((value ushr 8) and 0xFF)
        writeByte((value ushr 16) and 0xFF)
        writeByte((value ushr 24) and 0xFF)
    }
}
