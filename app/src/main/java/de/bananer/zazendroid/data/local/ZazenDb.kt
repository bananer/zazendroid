package de.bananer.zazendroid.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Single app database (version 1).
 *
 * Reserved future tables (not created; names claimed so they never collide —
 * each future is a new `@Entity` + DAO + version bump with a written `Migration`,
 * never a second database):
 * - `unit_state(unitId PK, courseId, favorite, positionMs, durationMs, updatedAtEpochMs)`
 *   for per-unit favorites + playback-resume positions.
 * - `daily_checkin(dateEpochDay PK, sleepScore, stressScore, moodScore, note)`
 *   for daily sleep/stress/mood tracking.
 *
 * No `fallbackToDestructiveMigration`: upgrades crash loudly until a real
 * `Migration` is added.
 */
@Database(entities = [CourseProgressEntity::class], version = 1, exportSchema = true)
abstract class ZazenDb : RoomDatabase() {
    abstract fun progressDao(): ProgressDao
}
