package de.bananer.zazendroid.data.favorites

import de.bananer.zazendroid.data.catalog.Course
import de.bananer.zazendroid.data.catalog.Single
import de.bananer.zazendroid.data.catalog.Unit
import org.junit.Assert.assertEquals
import org.junit.Test

class FavoriteListTest {
    private fun unit(id: String, title: String, index: Int) = Unit(
        id = id,
        courseId = "c1",
        title = title,
        audioUrl = "http://x/$id.mp3",
        durationSeconds = 60,
        orderIndex = index,
    )

    private val course = Course(
        id = "c1",
        title = "Basics",
        description = "d",
        units = listOf(unit("u2", "Bravo", 0), unit("u1", "Alpha", 1)),
    )
    private val single = Single(
        id = "s1",
        title = "Charlie",
        description = "d",
        audioUrl = "http://x/s1.mp3",
        durationSeconds = 60,
    )

    @Test
    fun `resolves units and singles sorted by title`() {
        val out = FavoriteList.resolve(
            courses = listOf(course),
            singles = listOf(single),
            favorites = listOf(
                Favorite("u1", FavoriteKind.UNIT, "c1"),
                Favorite("s1", FavoriteKind.SINGLE, null),
                Favorite("u2", FavoriteKind.UNIT, "c1"),
            ),
        )
        assertEquals(
            listOf("Alpha", "Bravo", "Charlie"),
            out.map { it.title() },
        )
    }

    @Test
    fun `drops orphans missing from catalog`() {
        val out = FavoriteList.resolve(
            courses = listOf(course),
            singles = emptyList(),
            favorites = listOf(
                Favorite("gone", FavoriteKind.UNIT, "c1"),
                Favorite("s1", FavoriteKind.SINGLE, null),
                Favorite("u1", FavoriteKind.UNIT, "c1"),
            ),
        )
        assertEquals(1, out.size)
        assertEquals("Alpha", out.single().title())
    }

    @Test
    fun `duplicate unit id prefers matching courseId`() {
        val other = Course(
            id = "c2",
            title = "Other",
            description = "d",
            units = listOf(unit("u1", "Same id elsewhere", 0).copy(courseId = "c2")),
        )
        val out = FavoriteList.resolve(
            courses = listOf(course, other),
            singles = emptyList(),
            favorites = listOf(Favorite("u1", FavoriteKind.UNIT, "c2")),
        )
        val entry = out.single() as FavoriteEntry.UnitFavorite
        assertEquals("c2", entry.course.id)
    }
}
