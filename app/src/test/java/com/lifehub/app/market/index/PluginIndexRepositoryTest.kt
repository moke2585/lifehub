package com.lifehub.app.market.index

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PluginIndexRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var cache: PluginIndexCache
    private lateinit var client: FakeIndexClient
    private lateinit var repository: PluginIndexRepository

    @Before
    fun setUp() {
        cache = PluginIndexCache(temporaryFolder.newFolder("market_index"))
        client = FakeIndexClient(
            registry = {
                IndexHttpResponse(
                    status = 200,
                    body = TestIndexFixture.registryBytes,
                    etag = "\"etag-1\"",
                    lastModified = "Mon, 23 Nov 2026 00:00:00 GMT",
                )
            },
            signature = { IndexHttpResponse(200, TestIndexFixture.signatureBytes(), null, null) },
        )
        repository = PluginIndexRepository(
            client = client,
            cache = cache,
            bundledRegistry = { TestIndexFixture.registryBytes },
            publicKey = TestIndexFixture::publicKey,
            indexUrl = "https://example.com/registry.json",
            signatureUrl = "https://example.com/registry.sig",
            clock = { 1_000L },
        )
    }

    @Test
    fun networkIndexIsVerifiedAndCached() {
        val snapshot = repository.refresh()

        assertEquals(PluginIndexSource.NETWORK, snapshot.source)
        assertFalse(snapshot.offline)
        assertEquals(1_000L, snapshot.fetchedAt)
        assertTrue(snapshot.index.plugins.isEmpty())
        assertNotNull(cache.read())
        assertEquals("\"etag-1\"", cache.read()?.etag)
        assertEquals(null, client.lastRegistryEtag)
    }

    @Test
    fun notModifiedReusesCache() {
        repository.refresh()
        client.registry = {
            IndexHttpResponse(304, null, "\"etag-1\"", "Mon, 23 Nov 2026 00:00:00 GMT")
        }
        client.signatureCalls = 0

        val snapshot = repository.refresh()

        assertEquals(PluginIndexSource.CACHE, snapshot.source)
        assertFalse(snapshot.offline)
        assertEquals(0, client.signatureCalls)
    }

    @Test
    fun networkFailureFallsBackToCache() {
        repository.refresh()
        client.registry = { throw IOException("offline") }

        val snapshot = repository.refresh()

        assertEquals(PluginIndexSource.CACHE, snapshot.source)
        assertTrue(snapshot.offline)
        assertTrue(snapshot.error.orEmpty().contains("offline"))
    }

    @Test
    fun invalidSignatureDoesNotReplaceCache() {
        repository.refresh()
        val tampered = TestIndexFixture.registryBytes.copyOf().apply {
            this[lastIndex - 1] = ' '.code.toByte()
        }
        client.registry = { IndexHttpResponse(200, tampered, "\"etag-2\"", null) }

        val snapshot = repository.refresh()

        assertEquals(PluginIndexSource.CACHE, snapshot.source)
        assertTrue(snapshot.offline)
        assertEquals("\"etag-1\"", cache.read()?.etag)
    }

    @Test
    fun corruptCacheFallsBackToBundled() {
        cache.write(
            registryBytes = TestIndexFixture.registryBytes,
            signatureBytes = "not a signature".toByteArray(),
            etag = "\"bad\"",
            lastModified = null,
            fetchedAt = 1L,
        )
        client.registry = { throw IOException("offline") }

        val snapshot = repository.refresh()

        assertEquals(PluginIndexSource.BUNDLED, snapshot.source)
        assertTrue(snapshot.offline)
        assertEquals(null, cache.read())
    }

    @Test
    fun loadCachedUsesBundledWhenCacheMissing() {
        val snapshot = repository.loadCached()

        assertEquals(PluginIndexSource.BUNDLED, snapshot.source)
        assertTrue(snapshot.offline)
    }

    private class FakeIndexClient(
        var registry: () -> IndexHttpResponse,
        var signature: () -> IndexHttpResponse,
    ) : IndexHttpClient {
        var lastRegistryEtag: String? = null
        var signatureCalls: Int = 0

        override fun get(url: String, etag: String?, lastModified: String?): IndexHttpResponse {
            return if (url.endsWith("registry.sig")) {
                signatureCalls += 1
                signature()
            } else {
                lastRegistryEtag = etag
                registry()
            }
        }
    }
}
