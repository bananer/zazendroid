package de.bananer.zazendroid.ui

import de.bananer.zazendroid.data.catalog.Course
import de.bananer.zazendroid.data.catalog.Single
import de.bananer.zazendroid.data.favorites.Favorite
import de.bananer.zazendroid.data.favorites.FavoritesRepository
import de.bananer.zazendroid.data.playback.PlaybackManager
import de.bananer.zazendroid.data.playback.PlaybackUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private class FakePlaybackManager : PlaybackManager {
    private val _state = MutableStateFlow(PlaybackUiState())
    override val state: StateFlow<PlaybackUiState> = _state.asStateFlow()
    val calls = mutableListOf<String>()
    var lastSeek: Long = -1

    fun emit(s: PlaybackUiState) {
        _state.value = s
    }

    override fun play(course: Course, startIndex: Int) {
        calls += "play:$startIndex"
    }
    override fun playSingle(single: Single) {
        calls += "playSingle:${single.id}"
    }
    override fun toggle() {
        calls += "toggle"
    }
    override fun queue(course: Course, startIndex: Int) {
        calls += "queue:$startIndex"
    }
    override fun queueSingle(single: Single) {
        calls += "queueSingle:${single.id}"
    }
    override fun preload(course: Course, startIndex: Int) {
        calls += "preload:$startIndex"
    }
    override fun preloadSingle(single: Single) {
        calls += "preloadSingle:${single.id}"
    }
    override fun seekTo(positionMs: Long) {
        calls += "seek"
        lastSeek = positionMs
    }
    override fun seekBy(deltaMs: Long) {
        calls += "seekBy:$deltaMs"
    }
    override fun ensureForegroundService() {
        calls += "service"
    }
}

private class FakeFavoritesRepository(initial: List<Favorite> = emptyList()) : FavoritesRepository {
    private val state = MutableStateFlow(initial)
    override fun favoritesFlow() = state.asStateFlow()
    override suspend fun toggle(fav: Favorite) {
        state.value = if (state.value.any { it.itemId == fav.itemId }) {
            state.value.filterNot { it.itemId == fav.itemId }
        } else {
            state.value + fav
        }
    }
    override suspend fun setFavorite(fav: Favorite, favorite: Boolean) {
        state.value = if (favorite) {
            (state.value.filterNot { it.itemId == fav.itemId }) + fav
        } else {
            state.value.filterNot { it.itemId == fav.itemId }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `controls delegate to playback manager`() = runTest {
        val fake = FakePlaybackManager()
        val vm = PlayerViewModel(fake, FakeFavoritesRepository())
        vm.toggle()
        vm.seekTo(12_000)
        vm.seekBy(-30_000)
        assertEquals(listOf("toggle", "seek", "seekBy:-30000"), fake.calls)
        assertEquals(12_000L, fake.lastSeek)
    }

    @Test
    fun `state mirrors manager`() = runTest {
        val fake = FakePlaybackManager()
        val vm = PlayerViewModel(fake, FakeFavoritesRepository())
        assertEquals(false, vm.uiState.value.isPlaying)
        assertEquals(false, vm.isFavorite.first())
    }

    @Test
    fun `toggleFavorite flips stored favorite for current unit`() = runTest {
        val fake = FakePlaybackManager()
        val course = Course(
            id = "c1",
            title = "Basics",
            description = "d",
            units = listOf(
                de.bananer.zazendroid.data.catalog.Unit(
                    id = "u1",
                    courseId = "c1",
                    title = "One",
                    audioUrl = "http://x/u1.mp3",
                    durationSeconds = 60,
                    orderIndex = 0,
                ),
            ),
        )
        fake.emit(PlaybackUiState(courseId = "c1", unit = course.units[0]))
        val favs = FakeFavoritesRepository()
        val vm = PlayerViewModel(fake, favs)
        assertEquals(false, vm.isFavorite.first())
        vm.toggleFavorite()
        assertEquals(true, vm.isFavorite.first())
        vm.toggleFavorite()
        assertEquals(false, vm.isFavorite.first())
    }

    @Test
    fun `toggleFavorite is no-op when idle`() = runTest {
        val fake = FakePlaybackManager()
        val favs = FakeFavoritesRepository()
        val vm = PlayerViewModel(fake, favs)
        vm.toggleFavorite()
        assertEquals(emptyList<Favorite>(), favs.favoritesFlow().first())
    }
}
