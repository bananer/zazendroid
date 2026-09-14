package de.bananer.zazendroid.data.favorites

import de.bananer.zazendroid.data.catalog.Course
import de.bananer.zazendroid.data.catalog.Single
import de.bananer.zazendroid.data.catalog.Unit

enum class FavoriteKind { UNIT, SINGLE }

/** Stored favorite: unit favorites carry their courseId for playback lookup. */
data class Favorite(
    val itemId: String,
    val kind: FavoriteKind,
    val courseId: String?,
)

/** Catalog-resolved favorite for display/playback; orphans (id gone from catalog) are dropped. */
sealed interface FavoriteEntry {
    data class UnitFavorite(val course: Course, val unit: Unit) : FavoriteEntry
    data class SingleFavorite(val single: Single) : FavoriteEntry
}

fun FavoriteEntry.title(): String = when (this) {
    is FavoriteEntry.UnitFavorite -> unit.title
    is FavoriteEntry.SingleFavorite -> single.title
}

/**
 * Pure catalog join, no Android deps (mirrors [de.bananer.zazendroid.data.progress.ContinueQueue]).
 * Duplicate unit ids across courses resolve to the entry whose course matches
 * [Favorite.courseId]; result sorted by title.
 */
object FavoriteList {
    fun resolve(
        courses: List<Course>,
        singles: List<Single>,
        favorites: List<Favorite>,
    ): List<FavoriteEntry> {
        val unitsById = mutableMapOf<String, MutableList<Pair<Course, Unit>>>()
        for (course in courses) {
            for (unit in course.units) {
                unitsById.getOrPut(unit.id) { mutableListOf() } += course to unit
            }
        }
        val singlesById = singles.associateBy { it.id }
        return favorites.mapNotNull { fav ->
            when (fav.kind) {
                FavoriteKind.UNIT -> {
                    val candidates = unitsById[fav.itemId] ?: return@mapNotNull null
                    val (course, unit) = candidates.find { it.first.id == fav.courseId }
                        ?: candidates.first()
                    FavoriteEntry.UnitFavorite(course, unit)
                }
                FavoriteKind.SINGLE -> {
                    val single = singlesById[fav.itemId] ?: return@mapNotNull null
                    FavoriteEntry.SingleFavorite(single)
                }
            }
        }.sortedBy { it.title() }
    }
}
