package de.bananer.zazendroid.data.catalog

import java.text.Normalizer

/**
 * Search result: course hits (title/description), unit hits (title, with parent
 * course for navigation), single hits (title/description). Author names are NOT
 * searched (only title/description per spec).
 */
sealed interface SearchHit {
    data class CourseHit(val course: Course) : SearchHit
    data class UnitHit(val course: Course, val unit: Unit) : SearchHit
    data class SingleHit(val single: Single) : SearchHit
}

/**
 * Pure case/diacritic-insensitive substring search over the loaded catalog.
 * Empty/blank query returns no hits (not a full dump).
 */
object CatalogSearch {
    fun search(catalog: Catalog, query: String): List<SearchHit> {
        val q = normalize(query)
        if (q.isBlank()) return emptyList()
        val hits = mutableListOf<SearchHit>()
        for (course in catalog.courses) {
            if (normalize(course.title).contains(q) || normalize(course.description).contains(q)) {
                hits += SearchHit.CourseHit(course)
            }
            for (unit in course.units) {
                if (normalize(unit.title).contains(q)) {
                    hits += SearchHit.UnitHit(course, unit)
                }
            }
        }
        for (single in catalog.singles) {
            if (normalize(single.title).contains(q) || normalize(single.description).contains(q)) {
                hits += SearchHit.SingleHit(single)
            }
        }
        return hits
    }

    internal fun normalize(s: String): String {
        val decomposed = Normalizer.normalize(s, Normalizer.Form.NFD)
        return decomposed.replace("\\p{Mn}+".toRegex(), "").lowercase()
    }
}
