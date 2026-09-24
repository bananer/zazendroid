package de.bananer.zazendroid.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per calendar day (local date as epoch day). Scores are 0–100,
 * no numeric meaning shown in UI (ring input hides the number).
 * Higher stress score = more stressed.
 */
@Entity(tableName = "daily_checkin")
data class MoodCheckinEntity(
    @PrimaryKey val dateEpochDay: Long,
    val sleepScore: Int,
    val stressScore: Int,
    val moodScore: Int,
    val updatedAtEpochMs: Long,
)
