package de.bananer.zazendroid.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorite")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorite WHERE itemId = :id")
    suspend fun get(id: String): FavoriteEntity?

    @Upsert
    suspend fun upsert(e: FavoriteEntity)

    @Query("DELETE FROM favorite WHERE itemId = :id")
    suspend fun delete(id: String)
}
