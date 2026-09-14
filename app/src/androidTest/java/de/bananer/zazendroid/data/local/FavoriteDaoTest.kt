package de.bananer.zazendroid.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.bananer.zazendroid.data.favorites.FavoriteKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoriteDaoTest {
    private lateinit var db: ZazenDb
    private lateinit var dao: FavoriteDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ZazenDb::class.java,
        ).build()
        dao = db.favoriteDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun upsertGetDeleteRoundTrip() = runTest {
        assertNull(dao.get("u1"))
        dao.upsert(FavoriteEntity("u1", FavoriteKind.UNIT.name, "c1", 1L))
        assertEquals("c1", dao.get("u1")?.courseId)
        assertEquals(1, dao.observeAll().first().size)
        dao.delete("u1")
        assertNull(dao.get("u1"))
        assertEquals(0, dao.observeAll().first().size)
    }

    @Test
    fun singleFavoriteHasNullCourse() = runTest {
        dao.upsert(FavoriteEntity("s1", FavoriteKind.SINGLE.name, null, 1L))
        assertNull(dao.get("s1")?.courseId)
    }
}
