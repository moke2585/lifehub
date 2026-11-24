package com.lifehub.app.plugin.runtime.index

import com.lifehub.app.plugins.testing.PluginArchiveTestBuilder
import java.security.KeyPair
import java.security.Signature
import java.util.Base64
import org.junit.Assert.assertTrue
import org.junit.Test

class IndexSignatureVerifierTest {

    private val registry = """{"schema":1,"plugins":[]}""".toByteArray()

    @Test
    fun verifiesMatchingSignature() {
        val keyPair = PluginArchiveTestBuilder.keyPair()
        val result = IndexSignatureVerifier.verify(
            registry,
            signature(keyPair),
            publicKeyPem(keyPair),
        )

        assertTrue(result.toString(), result.isSuccess)
    }

    @Test
    fun rejectsTamperedRegistry() {
        val keyPair = PluginArchiveTestBuilder.keyPair()
        val tampered = registry.copyOf().apply { this[this.lastIndex - 1] = ' '.code.toByte() }

        val result = IndexSignatureVerifier.verify(
            tampered,
            signature(keyPair),
            publicKeyPem(keyPair),
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("摘要"))
    }

    @Test
    fun rejectsDifferentPublicKey() {
        val keyPair = PluginArchiveTestBuilder.keyPair()

        val result = IndexSignatureVerifier.verify(
            registry,
            signature(keyPair),
            publicKeyPem(PluginArchiveTestBuilder.keyPair()),
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("签名"))
    }

    @Test
    fun rejectsMismatchedRegistrySha256() {
        val keyPair = PluginArchiveTestBuilder.keyPair()
        val signed = signature(keyPair)

        val result = IndexSignatureVerifier.verify(
            registry,
            signed.copy(registrySha256 = "0".repeat(64)),
            publicKeyPem(keyPair),
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun rejectsMalformedSignatureJson() {
        val result = IndexSignatureVerifier.parse("""{"schema":1}""")

        assertTrue(result.isFailure)
    }

    @Test
    fun rejectsUnsafeKeyId() {
        val keyPair = PluginArchiveTestBuilder.keyPair()
        val signed = signature(keyPair)

        val result = IndexSignatureVerifier.verify(
            registry,
            signed.copy(keyId = "../key"),
            publicKeyPem(keyPair),
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("keyId"))
    }

    private fun signature(keyPair: KeyPair): IndexSignature {
        val digest = IndexSignatureVerifier.sha256Hex(registry)
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(keyPair.private)
        signer.update(IndexSignatureVerifier.payload(digest))
        return IndexSignature(
            schema = IndexSignatureVerifier.SCHEMA,
            algorithm = IndexSignatureVerifier.ALGORITHM,
            keyId = "test-index-key",
            registrySha256 = digest,
            signature = Base64.getEncoder().encodeToString(signer.sign()),
            signedAt = "2026-11-23T00:00:00Z",
        )
    }

    private fun publicKeyPem(keyPair: KeyPair): String {
        val base64 = Base64.getEncoder().encodeToString(keyPair.public.encoded)
        return buildString {
            appendLine("-----BEGIN PUBLIC KEY-----")
            base64.chunked(64).forEach { appendLine(it) }
            appendLine("-----END PUBLIC KEY-----")
        }
    }
}
