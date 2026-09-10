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
 *   "courses": [
 *     {"id": "c1", "title": "Basics", "description": "Start here",
 *      "units": [
 *        {"id": "u1", "title": "Breath", "audioUrl": "/audio/breath.mp3", "durationSeconds": 600},
 *        {"id": "u2", "title": "Body", "audioUrl": "https://cdn.example.com/body.mp3"}
 *      ]}
 *   ]
 * }
 * ```
 *
 * Rules (load-bearing):
 * - All ids are opaque stable Strings. The server must never reuse an id for
 *   different content: favorites, resume positions, and progress all key off
 *   `course.id` / `unit.id` across catalog updates.
 * - Unit order is array order; mapped to [Unit.orderIndex].
 * - `audioUrl` may be absolute (`https://…`) or server-relative (`/audio/x.mp3`);
 *   the repository resolves relative URLs against the configured server base URL.
 *   This is the only URL rule.
 */
@Serializable
data class CatalogDto(
    val catalogVersion: Int = 0,
    val appInfo: AppInfoDto,
    val courses: List<CourseDto> = emptyList(),
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
    val units: List<UnitDto> = emptyList(),
)

@Serializable
data class UnitDto(
    val id: String = "",
    val title: String = "",
    val audioUrl: String = "",
    @SerialName("durationSeconds")
    val durationSeconds: Long? = null,
)
