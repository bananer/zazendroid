package de.bananer.zazendroid.data.mood

import de.bananer.zazendroid.data.local.MoodCheckinEntity
import de.bananer.zazendroid.data.local.MoodDao
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private class FakeMoodDao : MoodDao {
    private val state = MutableStateFlow(mapOf<Long, MoodCheckinEntity>())

    override fun observe(date: Long): Flow<MoodCheckinEntity?> = state.map { it[date] }

    override fun observeRange(from: Long, to: Long): Flow<List<MoodCheckinEntity>> =
        state.map { m -> m.values.filter { it.dateEpochDay in from..to }.sortedBy { it.dateEpochDay } }

    override suspend fun upsert(e: MoodCheckinEntity) {
        state.update { it + (e.dateEpochDay to e) }
    }
}

class RoomMoodRepositoryTest {
    @Test
    fun `save then today returns entry`() = runTest {
        val repo = RoomMoodRepository(FakeMoodDao(), today = { 100L })
        assertNull(repo.todayFlow().first())
        repo.save(10, 20, 30)
        val got = repo.todayFlow().first()!!
        assertEquals(10, got.sleep)
        assertEquals(20, got.stress)
        assertEquals(30, got.mood)
    }

    @Test
    fun `same-day save overwrites`() = runTest {
        val repo = RoomMoodRepository(FakeMoodDao(), today = { 100L })
        repo.save(10, 20, 30)
        repo.save(1, 2, 3)
        assertEquals(1, repo.todayFlow().first()!!.sleep)
        assertEquals(1, repo.last14DaysFlow().first().size)
    }

    @Test
    fun `last14 covers window only`() = runTest {
        val dao = FakeMoodDao()
        val repo = RoomMoodRepository(dao, today = { 100L })
        repo.saveForDate(LocalDate.ofEpochDay(80L), 1, 1, 1) // outside
        repo.saveForDate(LocalDate.ofEpochDay(87L), 2, 2, 2) // inside (100-13)
        repo.saveForDate(LocalDate.ofEpochDay(100L), 3, 3, 3)
        val days = repo.last14DaysFlow().first().map { it.dateEpochDay }
        assertEquals(listOf(87L, 100L), days)
    }

    @Test
    fun `scores clamped to 0-100`() = runTest {
        val repo = RoomMoodRepository(FakeMoodDao(), today = { 100L })
        repo.save(-5, 50, 500)
        val got = repo.todayFlow().first()!!
        assertEquals(0, got.sleep)
        assertEquals(100, got.mood)
    }
}
