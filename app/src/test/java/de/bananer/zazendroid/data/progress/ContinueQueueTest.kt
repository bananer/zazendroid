package de.bananer.zazendroid.data.progress

import de.bananer.zazendroid.data.catalog.Course
import de.bananer.zazendroid.data.catalog.Unit
import org.junit.Assert.assertEquals
import org.junit.Test

private fun course(id: String, title: String, n: Int) = Course(
    id = id,
    title = title,
    description = "",
    units = List(n) { i ->
        Unit(id = "$id-u$i", courseId = id, title = "U$i", audioUrl = "https://x/$i.mp3", durationSeconds = null, orderIndex = i)
    },
)

class ContinueQueueTest {
    private val courses = listOf(course("c1", "Beta", 3), course("c2", "Alpha", 2))

    @Test
    fun `started course continues at next index`() {
        val next = ContinueQueue.nextUp(courses, mapOf("c1" to 0))
        assertEquals(1, next.size)
        assertEquals("c1", next[0].courseId)
        assertEquals(1, next[0].unit.orderIndex)
    }

    @Test
    fun `completed course is absent`() {
        val next = ContinueQueue.nextUp(courses, mapOf("c1" to 2))
        assertEquals(0, next.size)
    }

    @Test
    fun `never-started courses excluded, result sorted by title`() {
        val next = ContinueQueue.nextUp(courses, mapOf("c1" to 0, "c2" to 0))
        assertEquals(listOf("c2", "c1"), next.map { it.courseId })
        assertEquals(1, next[0].unit.orderIndex)
    }

    @Test
    fun `empty progress yields nothing`() {
        assertEquals(0, ContinueQueue.nextUp(courses, emptyMap()).size)
    }
}
