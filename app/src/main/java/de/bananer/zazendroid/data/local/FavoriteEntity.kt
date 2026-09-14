package de.bananer.zazendroid.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per favorited playable. `itemId` is the unit or single id
 * (stable server ids — same keying rule as progress/resume);
 * `courseId` is set for unit favorites (locates the unit for playback),
 * null for singles.
 */
@Entity(tableName = "favorite")
data class FavoriteEntity(
    @PrimaryKey val itemId: String,
    val kind: String,
    val courseId: String?,
    val updatedAtEpochMs: Long,
)
