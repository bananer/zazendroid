package de.bananer.zazendroid.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun catalog() = Catalog(
    appInfo = AppInfo("Z", "D", 1),
    categories = listOf(Category("c-basics", "Basics", 10)),
    courses = listOf(
        Course(
            id = "c1",
            title = "Guided Meditations",
            description = "English originals for sleep",
            categoryId = "c-basics",
            units = listOf(
                Unit("u1", "c1", "Breathing Meditation", "http://x/u1.mp3", 300, orderIndex = 0),
                Unit("u2", "c1", "Body Scan", "http://x/u2.mp3", 300, orderIndex = 1),
            ),
        ),
    ),
    singles = listOf(
        Single("s1", "Quick Breath Reset", "Short reset", categoryId = "c-basics", audioUrl = "http://x/s.mp3", durationSeconds = 60),
    ),
    loadedFrom = "http://x",
    fromCache = false,
)

class CatalogSearchTest {
    @Test
    fun `blank query returns nothing`() {
        assertTrue(CatalogSearch.search(catalog(), "  ").isEmpty())
    }

    @Test
    fun `course title hit`() {
        val hits = CatalogSearch.search(catalog(), "guided")
        assertEquals(1, hits.size)
        assertTrue(hits[0] is SearchHit.CourseHit)
    }

    @Test
    fun `course description hit`() {
        assertEquals(1, CatalogSearch.search(catalog(), "originals").size)
    }

    @Test
    fun `unit title hit carries parent course`() {
        val hits = CatalogSearch.search(catalog(), "breathing")
        assertEquals(1, hits.size)
        val unit = hits[0] as SearchHit.UnitHit
        assertEquals("c1", unit.course.id)
        assertEquals("u1", unit.unit.id)
    }

    @Test
    fun `single title and description hits`() {
        assertEquals(1, CatalogSearch.search(catalog(), "quick breath").size)
        assertEquals(1, CatalogSearch.search(catalog(), "short reset").size)
    }

    @Test
    fun `case and diacritic insensitive`() {
        assertTrue(CatalogSearch.search(catalog(), "MEDITATION").isNotEmpty())
        val withUmlaut = catalog().copy(
            singles = listOf(
                Single("s2", "Meditation der liebenden Güte", "d", audioUrl = "http://x/s2.mp3", durationSeconds = 60),
            ),
        )
        assertEquals(1, CatalogSearch.search(withUmlaut, "gute").size)
    }
}
