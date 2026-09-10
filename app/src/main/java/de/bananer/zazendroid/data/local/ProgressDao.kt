package de.bananer.zazendroid.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {
    @Query("SELECT * FROM course_progress")
    fun observeAll(): Flow<List<CourseProgressEntity>>

    @Query("SELECT * FROM course_progress WHERE courseId = :id")
    fun observe(id: String): Flow<CourseProgressEntity?>

    @Upsert
    suspend fun upsert(e: CourseProgressEntity)

    @Query("DELETE FROM course_progress WHERE courseId = :id")
    suspend fun clear(id: String)
}
