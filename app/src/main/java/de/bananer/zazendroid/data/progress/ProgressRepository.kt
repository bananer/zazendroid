package de.bananer.zazendroid.data.progress

import de.bananer.zazendroid.data.local.CourseProgressEntity
import de.bananer.zazendroid.data.local.ProgressDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Storage seam for per-course progress. Favorites/resume/tracking arrive as new
 * repository interfaces over the same `ZazenDb` singleton; ViewModels never touch DAOs.
 */
interface ProgressRepository {
    fun progressFlow(courseId: String): Flow<Int?>
    fun allProgressFlow(): Flow<Map<String, Int>>
    suspend fun markCompleted(courseId: String, orderIndex: Int)
    suspend fun clear(courseId: String)
}

class RoomProgressRepository(private val dao: ProgressDao) : ProgressRepository {
    override fun progressFlow(courseId: String): Flow<Int?> =
        dao.observe(courseId).map { it?.lastCompletedIndex }

    override fun allProgressFlow(): Flow<Map<String, Int>> =
        dao.observeAll().map { list -> list.associate { it.courseId to it.lastCompletedIndex } }

    /** Monotonic upsert: only persists when greater than the stored value, never regresses. */
    override suspend fun markCompleted(courseId: String, orderIndex: Int) {
        val current = dao.observe(courseId).first()?.lastCompletedIndex
        if (current == null || orderIndex > current) {
            dao.upsert(
                CourseProgressEntity(
                    courseId = courseId,
                    lastCompletedIndex = orderIndex,
                    updatedAtEpochMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    override suspend fun clear(courseId: String) {
        dao.clear(courseId)
    }
}
