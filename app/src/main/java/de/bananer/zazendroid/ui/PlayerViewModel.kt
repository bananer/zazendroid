package de.bananer.zazendroid.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.bananer.zazendroid.data.favorites.Favorite
import de.bananer.zazendroid.data.favorites.FavoriteKind
import de.bananer.zazendroid.data.favorites.FavoritesRepository
import de.bananer.zazendroid.data.playback.PlaybackManager
import de.bananer.zazendroid.data.playback.PlaybackUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Player truth lives in [PlaybackManager]; favorite star reads + writes
 * [FavoritesRepository]. No current item (idle) → star hidden, toggle no-op.
 */
class PlayerViewModel(
    private val playback: PlaybackManager,
    private val favorites: FavoritesRepository,
) : ViewModel() {
    fun ensureForegroundService() = playback.ensureForegroundService()
    val uiState: StateFlow<PlaybackUiState> = playback.state

    val isFavorite: StateFlow<Boolean> = combine(
        playback.state,
        favorites.favoritesFlow(),
    ) { state, favs ->
        val key = state.favoriteKey() ?: return@combine false
        favs.any { it.itemId == key.itemId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun toggleFavorite() {
        val key = playback.state.value.favoriteKey() ?: return
        viewModelScope.launch { favorites.toggle(key) }
    }

    fun toggle() = playback.toggle()
    fun seekTo(ms: Long) = playback.seekTo(ms)
    fun seekBy(deltaMs: Long) = playback.seekBy(deltaMs)
}

private fun PlaybackUiState.favoriteKey(): Favorite? = when {
    single != null -> Favorite(single.id, FavoriteKind.SINGLE, courseId = null)
    unit != null -> Favorite(unit.id, FavoriteKind.UNIT, courseId = courseId)
    else -> null
}
