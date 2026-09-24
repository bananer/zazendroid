package de.bananer.zazendroid.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Single app database (version 3).
 *
 * v2 adds `favorite(itemId PK, kind, courseId, updatedAtEpochMs)` for
 * per-unit/per-single favorites (see [FavoriteEntity]).
 * v3 adds `daily_checkin(dateEpochDay PK, sleepScore, stressScore, moodScore,
 * updatedAtEpochMs)` for daily sleep/stress/mood tracking
 * (see [MoodCheckinEntity]).
 *
 * Reserved future tables (not created; names claimed so they never collide —
 * each future is a new `@Entity` + DAO + version bump with a written `Migration`,
 * never a second database):
 * - `unit_state(unitId PK, courseId, positionMs, durationMs, updatedAtEpochMs)`
 *   for per-unit playback-resume positions.
 *
 * No `fallbackToDestructiveMigration`: upgrades crash loudly until a real
 * `Migration` is added.
 */
@Database(
    entities = [CourseProgressEntity::class, FavoriteEntity::class, MoodCheckinEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class ZazenDb : RoomDatabase() {
    abstract fun progressDao(): ProgressDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun moodDao(): MoodDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `favorite` (" +
                "`itemId` TEXT NOT NULL, " +
                "`kind` TEXT NOT NULL, " +
                "`courseId` TEXT, " +
                "`updatedAtEpochMs` INTEGER NOT NULL, " +
                "PRIMARY KEY(`itemId`))",
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `daily_checkin` (" +
                "`dateEpochDay` INTEGER NOT NULL, " +
                "`sleepScore` INTEGER NOT NULL, " +
                "`stressScore` INTEGER NOT NULL, " +
                "`moodScore` INTEGER NOT NULL, " +
                "`updatedAtEpochMs` INTEGER NOT NULL, " +
                "PRIMARY KEY(`dateEpochDay`))",
        )
    }
}
