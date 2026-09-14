package de.bananer.zazendroid.data.favorites

import de.bananer.zazendroid.data.local.FavoriteDao
import de.bananer.zazendroid.data.local.FavoriteEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Storage seam for favorites. Same pattern as `ProgressRepository`:
 * ViewModels never touch DAOs; one implementation over the `ZazenDb` singleton.
 */
interface FavoritesRepository {
    fun favoritesFlow(): Flow<List<Favorite>>
    suspend fun toggle(fav: Favorite)
    suspend fun setFavorite(fav: Favorite, favorite: Boolean)
}

class RoomFavoritesRepository(private val dao: FavoriteDao) : FavoritesRepository {
    override fun favoritesFlow(): Flow<List<Favorite>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun toggle(fav: Favorite) {
        if (dao.get(fav.itemId) == null) {
            dao.upsert(fav.toEntity(System.currentTimeMillis()))
        } else {
            dao.delete(fav.itemId)
        }
    }

    override suspend fun setFavorite(fav: Favorite, favorite: Boolean) {
        if (favorite) {
            dao.upsert(fav.toEntity(System.currentTimeMillis()))
        } else {
            dao.delete(fav.itemId)
        }
    }
}

private fun FavoriteEntity.toDomain() = Favorite(
    itemId = itemId,
    kind = FavoriteKind.valueOf(kind),
    courseId = courseId,
)

private fun Favorite.toEntity(nowMs: Long) = FavoriteEntity(
    itemId = itemId,
    kind = kind.name,
    courseId = courseId,
    updatedAtEpochMs = nowMs,
)
