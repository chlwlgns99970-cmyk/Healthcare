package com.example.healthcare

import com.example.healthcare.data.update.KFindPublicMetadataSource
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KFindPublicMetadataSourceTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    @Test
    fun sameOfficialVersionDoesNotRequestImport() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"version\":\"2026-08-28\"}"))
        val source = KFindPublicMetadataSource(OkHttpClient(), server.url("metadata").toString())
        assertNull(source.check("2026-08-28"))
        assertEquals("POST", server.takeRequest().method)
    }

    @Test
    fun newerOfficialVersionIsReportedAsSnapshotOnlyWithoutFakeRows() = runTest {
        server.enqueue(MockResponse().setResponseCode(200)
            .addHeader("ETag", "official-v2")
            .setBody("{\"version\":\"2026-09-27\"}"))
        val source = KFindPublicMetadataSource(OkHttpClient(), server.url("metadata").toString())
        val candidate = source.check("2026-08-28")!!
        assertEquals("2026-09-27", candidate.version)
        assertEquals("official-v2", candidate.etag)
        assertNull(candidate.items)
        assertTrue(candidate.sourceId == "K-FIND")
    }
}
