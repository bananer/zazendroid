package de.bananer.zazendroid.ui

import com.sun.net.httpserver.HttpServer
import de.bananer.zazendroid.data.catalog.CatalogRepository
import de.bananer.zazendroid.data.local.CourseProgressEntity
import de.bananer.zazendroid.data.local.ProgressDao
import de.bananer.zazendroid.data.progress.RoomProgressRepository
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val BODY = """
{"catalogVersion":1,
 "appInfo":{"appName":"Z","description":"D","version":1},
 "courses":[{"id":"c1","title":"Basics","description":"d",
   "units":[{"id":"u1","title":"One","audioUrl":"/a.mp3"},
            {"id":"u2","title":"Two","audioUrl":"/b.mp3"},
            {"id":"u3","title":"Three","audioUrl":"/c.mp3"}]}]}
"""

private class FakeProgressDao : ProgressDao {
    private val state = MutableStateFlow(mapOf<String, CourseProgressEntity>())
    override fun observeAll(): Flow<List<CourseProgressEntity>> = state.map { it.values.toList() }
    override fun observe(id: String): Flow<CourseProgressEntity?> = state.map { it[id] }
    override suspend fun upsert(e: CourseProgressEntity) {
        state.update { it + (e.courseId to e) }
    }
    override suspend fun clear(id: String) {
        state.update { it - id }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CourseDetailViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `rows reflect completed progress`() = runTest {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/catalog.json") { exchange ->
            val bytes = BODY.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            val repo = CatalogRepository(
                baseUrlFlow = MutableStateFlow(base),
                okHttpClient = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build(),
                json = Json { ignoreUnknownKeys = true; explicitNulls = false },
                cacheDir = Files.createTempDirectory("cache").toFile(),
                scope = TestScope(testDispatcher),
            )
            repo.loadNow(base)
            val progress = RoomProgressRepository(FakeProgressDao())
            progress.markCompleted("c1", 0)

            val vm = CourseDetailViewModel("c1", repo, progress)
            val state = vm.uiState.first { it is DetailUiState.Ready } as DetailUiState.Ready
            assertEquals("Basics", state.course.title)
            assertEquals(listOf(true, false, false), state.rows.map { it.completed })
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `unknown course is missing`() = runTest {
        val dir: File = Files.createTempDirectory("cache").toFile()
        val repo = CatalogRepository(
            baseUrlFlow = MutableStateFlow("http://127.0.0.1:9"),
            okHttpClient = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build(),
            json = Json { ignoreUnknownKeys = true; explicitNulls = false },
            cacheDir = dir,
            scope = TestScope(testDispatcher),
        )
        repo.loadNow("http://127.0.0.1:9")
        val vm = CourseDetailViewModel("nope", repo, RoomProgressRepository(FakeProgressDao()))
        val state = vm.uiState.first { it is DetailUiState.Missing }
        assertTrue(state is DetailUiState.Missing)
    }
}
