package de.bananer.zazendroid.data.catalog

/** Domain catalog loaded from [loadedFrom] server URL; [fromCache] marks stale file fallback. */
data class Catalog(
    val appInfo: AppInfo,
    val categories: List<Category> = emptyList(),
    val courses: List<Course>,
    val singles: List<Single> = emptyList(),
    val loadedFrom: String,
    val fromCache: Boolean,
) {
    /** Category title lookup; unknown id renders as empty (chips only show known). */
    private val titlesById: Map<String, String> by lazy { categories.associate { it.id to it.title } }
    fun categoryTitle(id: String): String? = titlesById[id]
}

data class AppInfo(
    val appName: String,
    val description: String,
    val version: Int,
)

data class Category(
    val id: String,
    val title: String,
    val prio: Int,
)

data class Course(
    val id: String,
    val title: String,
    val description: String,
    val authorName: String? = null,
    val categoryId: String = "",
    val units: List<Unit>,
)

data class Unit(
    val id: String,
    val courseId: String,
    val title: String,
    /** Resolved absolute URL (relative inputs joined against the server base URL). */
    val audioUrl: String,
    val durationSeconds: Long?,
    /** Point where the guided content starts (intro talk before); null = unknown. */
    val startOfMeditationInSeconds: Long? = null,
    val orderIndex: Int,
)

/** Meditation unit outside of any course; never tracked in course progress. */
data class Single(
    val id: String,
    val title: String,
    val description: String,
    val authorName: String? = null,
    val categoryId: String = "",
    /** Resolved absolute URL (relative inputs join against the server base URL). */
    val audioUrl: String,
    val durationSeconds: Long?,
)

/** Catalog load failure; [Empty] means nothing survived validation filtering. */
sealed interface CatalogError {
    data object Empty : CatalogError
}

/**
 * Maps wire DTO to domain. Resolves relative audio URLs via
 * `baseUrl.trimEnd('/') + "/" + relative.trimStart('/')`; absolute URLs pass
 * through unchanged; blank audioUrl throws [IllegalArgumentException] (fail-fast,
 * surfaced as catalog load error, never silent skip).
 *
 * Validation: drops courses with empty id/title or empty units; drops units with
 * empty id/title/audioUrl; drops singles with empty id/title/audioUrl. Empty
 * catalog (no courses and no singles) after filtering throws [CatalogError.Empty]
 * wrapped as [CatalogEmptyException].
 */
fun CatalogDto.toDomain(baseUrl: String): Catalog {
    val categories = categories.mapNotNull { cat ->
        if (cat.id.isBlank() || cat.title.isBlank()) return@mapNotNull null
        Category(id = cat.id, title = cat.title, prio = cat.prio)
    }.sortedWith(compareBy({ it.prio }, { it.title }))
    val courses = courses.mapNotNull { course ->
        if (course.id.isBlank() || course.title.isBlank() || course.units.isEmpty()) return@mapNotNull null
        val units = course.units.mapIndexedNotNull { index, unit ->
            if (unit.id.isBlank() || unit.title.isBlank() || unit.audioUrl.isBlank()) {
                return@mapIndexedNotNull null
            }
            Unit(
                id = unit.id,
                courseId = course.id,
                title = unit.title,
                audioUrl = resolveAudioUrl(baseUrl, unit.audioUrl),
                durationSeconds = unit.durationSeconds,
                startOfMeditationInSeconds = unit.startOfMeditationInSeconds,
                orderIndex = index,
            )
        }
        if (units.isEmpty()) return@mapNotNull null
        Course(
            id = course.id,
            title = course.title,
            description = course.description,
            authorName = course.authorName,
            categoryId = course.categoryId,
            units = units,
        )
    }
    val singles = singles.mapNotNull { single ->
        if (single.id.isBlank() || single.title.isBlank() || single.audioUrl.isBlank()) {
            return@mapNotNull null
        }
        Single(
            id = single.id,
            title = single.title,
            description = single.description,
            authorName = single.authorName,
            categoryId = single.categoryId,
            audioUrl = resolveAudioUrl(baseUrl, single.audioUrl),
            durationSeconds = single.durationSeconds,
        )
    }
    if (courses.isEmpty() && singles.isEmpty()) throw CatalogEmptyException()
    return Catalog(
        appInfo = AppInfo(appInfo.appName, appInfo.description, appInfo.version),
        categories = categories,
        courses = courses,
        singles = singles,
        loadedFrom = baseUrl,
        fromCache = false,
    )
}

class CatalogEmptyException : IllegalStateException("Catalog empty after validation")

fun resolveAudioUrl(baseUrl: String, audioUrl: String): String {
    val trimmed = audioUrl.trim()
    if (trimmed.isBlank()) throw IllegalArgumentException("blank audioUrl")
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
    return baseUrl.trimEnd('/') + "/" + trimmed.trimStart('/')
}
