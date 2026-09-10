package de.bananer.zazendroid.data.catalog

/** Domain catalog loaded from [loadedFrom] server URL; [fromCache] marks stale file fallback. */
data class Catalog(
    val appInfo: AppInfo,
    val courses: List<Course>,
    val loadedFrom: String,
    val fromCache: Boolean,
)

data class AppInfo(
    val appName: String,
    val description: String,
    val version: Int,
)

data class Course(
    val id: String,
    val title: String,
    val description: String,
    val units: List<Unit>,
)

data class Unit(
    val id: String,
    val courseId: String,
    val title: String,
    /** Resolved absolute URL (relative inputs joined against the server base URL). */
    val audioUrl: String,
    val durationSeconds: Long?,
    val orderIndex: Int,
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
 * empty id/title/audioUrl. Empty catalog after filtering throws [CatalogError.Empty]
 * wrapped as [CatalogEmptyException].
 */
fun CatalogDto.toDomain(baseUrl: String): Catalog {
    val courses = courses.mapNotNull { course ->
        if (course.id.isBlank() || course.title.isBlank() || course.units.isEmpty()) return@mapNotNull null
        val units = course.units.mapIndexedNotNull { index, unit ->
            if (unit.id.isBlank() || unit.title.isBlank() || unit.audioUrl.isBlank()) {
                return@mapIndexedNotNull null
            }
            if (unit.audioUrl.isBlank()) throw IllegalArgumentException("blank audioUrl for unit ${unit.id}")
            Unit(
                id = unit.id,
                courseId = course.id,
                title = unit.title,
                audioUrl = resolveAudioUrl(baseUrl, unit.audioUrl),
                durationSeconds = unit.durationSeconds,
                orderIndex = index,
            )
        }
        if (units.isEmpty()) return@mapNotNull null
        Course(id = course.id, title = course.title, description = course.description, units = units)
    }
    if (courses.isEmpty()) throw CatalogEmptyException()
    return Catalog(
        appInfo = AppInfo(appInfo.appName, appInfo.description, appInfo.version),
        courses = courses,
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
