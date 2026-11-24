package com.lifehub.app.market.index

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PluginIndexCacheTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun writeAndReadPreservesMetadata() {
        val cache = PluginIndexCache(temporaryFolder.newFolder("cache"))

        cache.write(
            registryBytes = """{"schema":1,"plugins":[]}""".toByteArray(),
            signatureBytes = "signature".toByteArray(),
            etag = "\"etag\"",
            lastModified = "Mon, 23 Nov 2026 00:00:00 GMT",
            fetchedAt = 42L,
        )

        val cached = cache.read()
        assertEquals("\"etag\"", cached?.etag)
        assertEquals("Mon, 23 Nov 2026 00:00:00 GMT", cached?.lastModified)
        assertEquals(42L, cached?.fetchedAt)
        assertEquals("signature", cached?.signatureBytes?.toString(Charsets.UTF_8))
    }

    @Test
    fun missingMetaUsesZeroTimestamp() {
        val dir = temporaryFolder.newFolder("cache")
        val cache = PluginIndexCache(dir)
        cache.write(
            registryBytes = "{}".toByteArray(),
            signatureBytes = "sig".toByteArray(),
            etag = null,
            lastModified = null,
            fetchedAt = 1L,
        )
        assertTrue(File(dir, "meta.properties").delete())

        assertEquals(0L, cache.read()?.fetchedAt)
    }

    @Test
    fun clearRemovesAllFiles() {
        val dir = temporaryFolder.newFolder("cache")
        val cache = PluginIndexCache(dir)
        cache.write("{}".toByteArray(), "sig".toByteArray(), null, null, 0L)

        cache.clear()

        assertNull(cache.read())
    }
}
