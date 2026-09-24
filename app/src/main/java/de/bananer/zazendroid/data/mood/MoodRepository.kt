package de.bananer.zazendroid.data.mood

import de.bananer.zazendroid.data.local.MoodCheckinEntity
import de.bananer.zazendroid.data.local.MoodDao
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** One saved day: three 0–100 scores (higher stress = more stressed). */
data class MoodCheckin(
    val dateEpochDay: Long,
    val sleep: Int,
    val stress: Int,
    val mood: Int,
)

/**
 * Storage seam for daily mood check-ins. ViewModels never touch DAOs.
 * One row per local-calendar day; saving twice overwrites (upsert).
 */
interface MoodRepository {
    fun todayFlow(): Flow<MoodCheckin?>
    fun last14DaysFlow(): Flow<List<MoodCheckin>>
    suspend fun save(sleep: Int, stress: Int, mood: Int)
    suspend fun saveForDate(date: LocalDate, sleep: Int, stress: Int, mood: Int)
}

fun todayEpochDay(): Long = LocalDate.now().toEpochDay()

class RoomMoodRepository(
    private val dao: MoodDao,
    private val today: () -> Long = ::todayEpochDay,
) : MoodRepository {
    override fun todayFlow(): Flow<MoodCheckin?> =
        dao.observe(today()).map { it?.toDomain() }

    override fun last14DaysFlow(): Flow<List<MoodCheckin>> {
        val end = today()
        return dao.observeRange(end - 13, end).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun save(sleep: Int, stress: Int, mood: Int) {
        dao.upsert(
            MoodCheckinEntity(
                dateEpochDay = today(),
                sleepScore = sleep.coerceIn(0, 100),
                stressScore = stress.coerceIn(0, 100),
                moodScore = mood.coerceIn(0, 100),
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun saveForDate(date: LocalDate, sleep: Int, stress: Int, mood: Int) {
        dao.upsert(
            MoodCheckinEntity(
                dateEpochDay = date.toEpochDay(),
                sleepScore = sleep.coerceIn(0, 100),
                stressScore = stress.coerceIn(0, 100),
                moodScore = mood.coerceIn(0, 100),
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }
}

private fun MoodCheckinEntity.toDomain() = MoodCheckin(dateEpochDay, sleepScore, stressScore, moodScore)
