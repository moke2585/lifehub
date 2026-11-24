package com.lifehub.app.market.index

import com.lifehub.app.plugin.runtime.index.IndexSignatureVerifier
import com.lifehub.app.plugins.testing.PluginArchiveTestBuilder
import java.security.KeyPair
import java.security.Signature
import java.util.Base64

object TestIndexFixture {
    const val KEY_ID = "test-index-key"

    val keyPair: KeyPair = PluginArchiveTestBuilder.keyPair()
    val publicKeyPem: String = publicKeyPem(keyPair)
    val registryBytes: ByteArray = """{"schema":1,"plugins":[]}""".toByteArray()

    fun signatureBytes(registry: ByteArray = registryBytes): ByteArray {
        val digest = IndexSignatureVerifier.sha256Hex(registry)
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(keyPair.private)
        signer.update(IndexSignatureVerifier.payload(digest))
        val signature = Base64.getEncoder().encodeToString(signer.sign())
        return """
            {
              "schema": 1,
              "algorithm": "ECDSA_P256_SHA256",
              "keyId": "$KEY_ID",
              "registrySha256": "$digest",
              "signature": "$signature",
              "signedAt": "2026-11-23T00:00:00Z"
            }
        """.trimIndent().toByteArray()
    }

    fun publicKey(keyId: String): String? = publicKeyPem.takeIf { keyId == KEY_ID }

    private fun publicKeyPem(keyPair: KeyPair): String {
        val base64 = Base64.getEncoder().encodeToString(keyPair.public.encoded)
        return buildString {
            appendLine("-----BEGIN PUBLIC KEY-----")
            base64.chunked(64).forEach { appendLine(it) }
            appendLine("-----END PUBLIC KEY-----")
        }
    }
}
