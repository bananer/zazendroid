package de.bananer.zazendroid.data.progress

import de.bananer.zazendroid.data.catalog.Course
import de.bananer.zazendroid.data.catalog.Unit

data class NextUnit(
    val courseId: String,
    val courseTitle: String,
    val unit: Unit,
)

/**
 * Pure "continue next unit of a started course" query, no Android deps.
 * Started = progress contains the courseId. For each started non-completed course
 * emits [NextUnit] with `unit = units[min(stored+1, lastIndex)]`; fully completed
 * courses (`stored >= lastIndex`) are skipped; never-started courses excluded.
 * Result sorted by course title.
 */
object ContinueQueue {
    fun nextUp(courses: List<Course>, progress: Map<String, Int>): List<NextUnit> {
        return courses.mapNotNull { course ->
            val stored = progress[course.id] ?: return@mapNotNull null
            if (course.units.isEmpty()) return@mapNotNull null
            if (stored >= course.units.lastIndex) return@mapNotNull null
            val next = course.units[minOf(stored + 1, course.units.lastIndex)]
            NextUnit(courseId = course.id, courseTitle = course.title, unit = next)
        }.sortedBy { it.courseTitle }
    }
}
