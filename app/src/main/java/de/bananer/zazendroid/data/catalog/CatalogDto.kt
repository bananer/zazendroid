package de.bananer.zazendroid.data.catalog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire contract for `GET <serverUrl>/catalog.json`.
 *
 * Sample:
 * ```json
 * {
 *   "catalogVersion": 1,
 *   "appInfo": {"appName": "Zazen", "description": "Sit.", "version": 1},
 *   "categories": [
 *     {"id": "cat-basics", "title": "Basics", "prio": 10}
 *   ],
 *   "courses": [
 *     {"id": "c1", "title": "Basics", "description": "Start here",
 *      "authorName": "Diana Winston", "categoryId": "cat-basics",
 *      "units": [
 *        {"id": "u1", "title": "Breath", "audioUrl": "/audio/breath.mp3",
 *         "durationSeconds": 600, "startOfMeditationInSeconds": 30},
 *        {"id": "u2", "title": "Body", "audioUrl": "https://cdn.example.com/body.mp3"}
 *      ]}
 *   ],
 *   "singles": [
 *     {"id": "s1", "title": "Quick Breath", "description": "Reset fast",
 *      "authorName": "Diana Winston", "categoryId": "cat-basics",
 *      "durationSeconds": 300, "audioUrl": "/audio/quick-breath.mp3"}
 *   ]
 * }
 * ```
 *
 * Rules (load-bearing):
 * - All ids are opaque stable Strings. The server must never reuse an id for
 *   different content: favorites, resume positions, and progress all key off
 *   `course.id` / `unit.id` / `single.id` across catalog updates.
 * - Unit order is array order; mapped to [Unit.orderIndex].
 * - `audioUrl` may be absolute (`https://…`) or server-relative (`/audio/x.mp3`);
 *   the repository resolves relative URLs against the configured server base URL.
 *   This is the only URL rule (applies to singles too).
 * - `categories` is prio-sorted by the mapper (missing prio sorts last, stable).
 *   Display titles always resolve via `categories` id lookup, never denormalized.
 * - `authorName`, `startOfMeditationInSeconds` are optional
 *   display/metadata fields; absent means unknown, never an error.
 * - `singles` is optional; absent means no singles.
 */
@Serializable
data class CatalogDto(
    val catalogVersion: Int = 0,
    val appInfo: AppInfoDto,
    val categories: List<CategoryDto> = emptyList(),
    val courses: List<CourseDto> = emptyList(),
    val singles: List<SingleDto> = emptyList(),
)

@Serializable
data class CategoryDto(
    val id: String = "",
    val title: String = "",
    val prio: Int = Int.MAX_VALUE,
)

@Serializable
data class AppInfoDto(
    val appName: String = "",
    val description: String = "",
    val version: Int = 0,
)

@Serializable
data class CourseDto(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val authorName: String? = null,
    val categoryId: String = "",
    val units: List<UnitDto> = emptyList(),
)

@Serializable
data class UnitDto(
    val id: String = "",
    val title: String = "",
    val audioUrl: String = "",
    @SerialName("durationSeconds")
    val durationSeconds: Long? = null,
    @SerialName("startOfMeditationInSeconds")
    val startOfMeditationInSeconds: Long? = null,
)

@Serializable
data class SingleDto(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val authorName: String? = null,
    val categoryId: String = "",
    @SerialName("durationSeconds")
    val durationSeconds: Long? = null,
    val audioUrl: String = "",
)
