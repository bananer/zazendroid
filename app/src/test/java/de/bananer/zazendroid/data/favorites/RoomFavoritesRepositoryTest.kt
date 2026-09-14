package de.bananer.zazendroid.data.favorites

import de.bananer.zazendroid.data.local.FavoriteDao
import de.bananer.zazendroid.data.local.FavoriteEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** In-memory fake: FavoriteDao is an interface, logic tested without Room. */
private class FakeFavoriteDao : FavoriteDao {
    private val state = MutableStateFlow(mapOf<String, FavoriteEntity>())

    override fun observeAll(): Flow<List<FavoriteEntity>> = state.map { it.values.toList() }
    override suspend fun get(id: String): FavoriteEntity? = state.value[id]
    override suspend fun upsert(e: FavoriteEntity) {
        state.update { it + (e.itemId to e) }
    }
    override suspend fun delete(id: String) {
        state.update { it - id }
    }
}

class RoomFavoritesRepositoryTest {
    private val unit = Favorite("u1", FavoriteKind.UNIT, "c1")

    @Test
    fun `toggle adds then removes`() = runTest {
        val repo = RoomFavoritesRepository(FakeFavoriteDao())
        assertEquals(emptyList<Favorite>(), repo.favoritesFlow().first())
        repo.toggle(unit)
        assertEquals(listOf(unit), repo.favoritesFlow().first())
        repo.toggle(unit)
        assertEquals(emptyList<Favorite>(), repo.favoritesFlow().first())
    }

    @Test
    fun `setFavorite is idempotent`() = runTest {
        val repo = RoomFavoritesRepository(FakeFavoriteDao())
        repo.setFavorite(unit, true)
        repo.setFavorite(unit, true)
        assertEquals(listOf(unit), repo.favoritesFlow().first())
        repo.setFavorite(unit, false)
        assertEquals(emptyList<Favorite>(), repo.favoritesFlow().first())
    }
}
