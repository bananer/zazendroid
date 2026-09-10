package de.bananer.zazendroid.data.progress

import de.bananer.zazendroid.data.local.CourseProgressEntity
import de.bananer.zazendroid.data.local.ProgressDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** In-memory fake: DAO is an interface, so logic is tested without Room. */
private class FakeProgressDao : ProgressDao {
    private val state = MutableStateFlow(mapOf<String, CourseProgressEntity>())

    override fun observeAll(): Flow<List<CourseProgressEntity>> = state.map { it.values.toList() }

    override fun observe(id: String): Flow<CourseProgressEntity?> = state.map { it[id] }

    override suspend fun upsert(e: CourseProgressEntity) {
        state.update { it + (e.courseId to e) }
    }

    override suspend fun clear(id: String) {
        state.update { it - id }
    }
}

class RoomProgressRepositoryTest {
    @Test
    fun `markCompleted never regresses`() = runTest {
        val repo = RoomProgressRepository(FakeProgressDao())
        repo.markCompleted("c1", 2)
        repo.markCompleted("c1", 0)
        assertEquals(2, repo.progressFlow("c1").first())
    }

    @Test
    fun `markCompleted advances and clear removes`() = runTest {
        val repo = RoomProgressRepository(FakeProgressDao())
        repo.markCompleted("c1", 0)
        repo.markCompleted("c1", 1)
        assertEquals(1, repo.progressFlow("c1").first())
        assertEquals(mapOf("c1" to 1), repo.allProgressFlow().first())
        repo.clear("c1")
        assertEquals(null, repo.progressFlow("c1").first())
        assertEquals(emptyMap<String, Int>(), repo.allProgressFlow().first())
    }
}
