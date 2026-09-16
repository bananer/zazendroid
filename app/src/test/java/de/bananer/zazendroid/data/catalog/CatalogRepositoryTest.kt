package de.bananer.zazendroid.data.catalog

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val BODY = """
{"catalogVersion":1,
 "appInfo":{"appName":"Zazen","description":"Sit.","version":1},
 "courses":[{"id":"c1","title":"Basics","description":"d",
   "units":[{"id":"u1","title":"Breath","audioUrl":"/audio/b.mp3"}]}]}
"""

private fun serve(body: String, code: Int = 200): Pair<MockWebServer, String> {
    val server = MockWebServer()
    server.dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse =
            MockResponse().setResponseCode(code).setBody(body)
    }
    server.start()
    return server to server.url("/").toString().removeSuffix("/")
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CatalogRepositoryTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()

    private fun repo(base: String, dir: File, scope: TestScope) = CatalogRepository(
        baseUrlFlow = MutableStateFlow(base),
        okHttpClient = client,
        json = json,
        cacheDir = dir,
        scope = scope,
    )

    @Test
    fun `success persists cache and resolves urls`() = runTest {
        val (server, base) = serve(BODY)
        try {
            val dir = Files.createTempDirectory("cache").toFile()
            val r = repo(base, dir, TestScope(UnconfinedTestDispatcher()))
            r.loadNow(base)
            val state = r.catalogFlow.value
            assertTrue(state is CatalogState.Ready)
            state as CatalogState.Ready
            assertEquals(false, state.fromCache)
            assertEquals("$base/audio/b.mp3", state.catalog.courses[0].units[0].audioUrl)
            assertEquals(1, dir.listFiles()?.size)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `malformed json yields user-facing error`() = runTest {
        val (server, base) = serve("not json{{{", 200)
        try {
            val dir = Files.createTempDirectory("cache").toFile()
            val r = repo(base, dir, TestScope(UnconfinedTestDispatcher()))
            r.loadNow(base)
            val state = r.catalogFlow.value
            assertTrue(state is CatalogState.Error)
            assertEquals(CatalogErrorKind.INVALID_CONTENT, (state as CatalogState.Error).kind)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `unreachable server falls back to cache`() = runTest {
        val (server, base) = serve(BODY)
        val dir = Files.createTempDirectory("cache").toFile()
        val r = repo(base, dir, TestScope(UnconfinedTestDispatcher()))
        r.loadNow(base)
        assertTrue(r.catalogFlow.value is CatalogState.Ready)
        server.shutdown()

        r.loadNow(base)
        val state = r.catalogFlow.value
        assertTrue(state is CatalogState.Ready)
        assertEquals(true, (state as CatalogState.Ready).fromCache)
    }

    @Test
    fun `unreachable server without cache is error`() = runTest {
        val dir = Files.createTempDirectory("cache").toFile()
        val r = repo("http://127.0.0.1:9", dir, TestScope(UnconfinedTestDispatcher()))
        r.loadNow("http://127.0.0.1:9")
        val state = r.catalogFlow.value
        assertTrue(state is CatalogState.Error)
        assertEquals(CatalogErrorKind.UNREACHABLE, (state as CatalogState.Error).kind)
    }
}
