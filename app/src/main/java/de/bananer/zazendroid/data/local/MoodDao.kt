package de.bananer.zazendroid.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MoodDao {
    @Query("SELECT * FROM daily_checkin WHERE dateEpochDay = :date")
    fun observe(date: Long): Flow<MoodCheckinEntity?>

    @Query("SELECT * FROM daily_checkin WHERE dateEpochDay BETWEEN :from AND :to ORDER BY dateEpochDay")
    fun observeRange(from: Long, to: Long): Flow<List<MoodCheckinEntity>>

    @Upsert
    suspend fun upsert(e: MoodCheckinEntity)
}
