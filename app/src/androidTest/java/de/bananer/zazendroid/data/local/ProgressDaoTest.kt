package de.bananer.zazendroid.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgressDaoTest {
    private lateinit var db: ZazenDb
    private lateinit var dao: ProgressDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ZazenDb::class.java,
        ).build()
        dao = db.progressDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun upsertObserveClearRoundTrip() = runTest {
        assertNull(dao.observe("c1").first())
        dao.upsert(CourseProgressEntity("c1", 0, 1L))
        assertEquals(0, dao.observe("c1").first()?.lastCompletedIndex)
        dao.upsert(CourseProgressEntity("c1", 2, 2L))
        assertEquals(2, dao.observe("c1").first()?.lastCompletedIndex)
        assertEquals(1, dao.observeAll().first().size)
        dao.clear("c1")
        assertNull(dao.observe("c1").first())
        assertEquals(0, dao.observeAll().first().size)
    }
}
