package de.bananer.zazendroid.ui

import de.bananer.zazendroid.data.catalog.Course
import de.bananer.zazendroid.data.playback.PlaybackManager
import de.bananer.zazendroid.data.playback.PlaybackUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    override fun play(course: Course, startIndex: Int) {
        calls += "play:$startIndex"
    }
    override fun toggle() {
        calls += "toggle"
    }
    override fun queue(course: Course, startIndex: Int) {
        calls += "queue:$startIndex"
    }
    override fun seekTo(positionMs: Long) {
        calls += "seek"
        lastSeek = positionMs
    }
    override fun seekBy(deltaMs: Long) {
        calls += "seekBy:$deltaMs"
    }
    override fun next() {
        calls += "next"
    }
    override fun previous() {
        calls += "prev"
    }
    override fun ensureForegroundService() {
        calls += "service"
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
        val vm = PlayerViewModel(fake)
        vm.toggle()
        vm.seekTo(12_000)
        vm.seekBy(-30_000)
        vm.next()
        vm.previous()
        assertEquals(listOf("toggle", "seek", "seekBy:-30000", "next", "prev"), fake.calls)
        assertEquals(12_000L, fake.lastSeek)
    }

    @Test
    fun `state mirrors manager`() = runTest {
        val fake = FakePlaybackManager()
        val vm = PlayerViewModel(fake)
        assertEquals(false, vm.uiState.value.isPlaying)
    }
}
