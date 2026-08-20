package com.markel.flowstate.core.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * This is the main database class.
 * It tells Room which "Entities" (tables) it should be aware of
 * and which version of the database we are using.
 */
@Database(
    entities = [TaskEntity::class, SubTaskEntity::class, IdeaEntity::class, CheckListEntity::class, CheckListItemEntity::class, HabitEntity::class, HabitEntryEntity::class, HabitNumericEntryEntity::class, CategoryEntity::class], // List of all tables
    version = 20,
    exportSchema = true
)
@TypeConverters(HabitConverters::class)
abstract class FlowStateDatabase : RoomDatabase() {

    // Exposes our DAO so the rest of the app can use it
    abstract val taskDao: TaskDao
    abstract val ideaDao: IdeaDao
    abstract val checkListDao: CheckListDao
    abstract val habitDao: HabitDao
    abstract val categoryDao: CategoryDao

    // Room will use this to create the DB instance.
    companion object {
        const val DATABASE_NAME = "flowstate_db"
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN completedAt INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE subtasks ADD COLUMN completedAt INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create ideas table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `ideas` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `content` TEXT NOT NULL, 
                        `createdAt` INTEGER NOT NULL, 
                        `color` INTEGER NOT NULL
                    )
                """.trimIndent())

                // Create lists table
                db.execSQL("CREATE TABLE IF NOT EXISTS `checklists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL)")

                // Create list items table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `checklist_items` (
                        `id` TEXT NOT NULL, 
                        `listId` INTEGER NOT NULL, 
                        `text` TEXT NOT NULL, 
                        `isDone` INTEGER NOT NULL, 
                        `position` INTEGER NOT NULL,
                        PRIMARY KEY(`id`), 
                        FOREIGN KEY(`listId`) REFERENCES `checklists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE 
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ideas ADD COLUMN title TEXT NOT NULL DEFAULT ''")
            }
        }
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `grid_order` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `itemId` INTEGER NOT NULL,
                        `itemType` TEXT NOT NULL,
                        `position` INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE checklists ADD COLUMN color INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `habits` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `iconName` TEXT NOT NULL DEFAULT 'self_improvement',
                        `colorArgb` INTEGER NOT NULL DEFAULT -10185078,
                        `frequency` TEXT NOT NULL DEFAULT 'DAILY',
                        `createdAt` INTEGER NOT NULL
                    )
                """.trimIndent())
                        db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `habit_entries` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `habitId` INTEGER NOT NULL,
                        `completedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`habitId`) REFERENCES `habits`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_entries_habitId` ON `habit_entries` (`habitId`)")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habits ADD COLUMN habitType TEXT NOT NULL DEFAULT 'BOOLEAN'")
                db.execSQL("ALTER TABLE habits ADD COLUMN unit TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE habits ADD COLUMN targetValue REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE habits ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
                db.execSQL("""
            CREATE TABLE IF NOT EXISTS `habit_numeric_entries` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `habitId` INTEGER NOT NULL,
                `epochDay` INTEGER NOT NULL,
                `value` REAL NOT NULL,
                FOREIGN KEY(`habitId`) REFERENCES `habits`(`id`) ON DELETE CASCADE
            )
        """)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_numeric_entries_habitId` ON `habit_numeric_entries` (`habitId`)")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habits ADD COLUMN step REAL NOT NULL DEFAULT 1.0")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create new table with the correct primary key
                db.execSQL("""
                    CREATE TABLE habit_numeric_entries_new (
                        habitId INTEGER NOT NULL,
                        epochDay INTEGER NOT NULL,
                        value REAL NOT NULL,
                        PRIMARY KEY(habitId, epochDay),
                        FOREIGN KEY(habitId) REFERENCES habits(id) ON DELETE CASCADE
                    )
                """.trimIndent())

                // Insert previous data avoiding duplicates
                // We use MAX(id) to get the most recent entry
                db.execSQL("""
                    INSERT OR REPLACE INTO habit_numeric_entries_new (habitId, epochDay, value)
                    SELECT habitId, epochDay, value FROM habit_numeric_entries
                    WHERE id IN (SELECT MAX(id) FROM habit_numeric_entries GROUP BY habitId, epochDay)
                """.trimIndent())

                // Delete the old table and rename
                db.execSQL("DROP TABLE habit_numeric_entries")
                db.execSQL("ALTER TABLE habit_numeric_entries_new RENAME TO habit_numeric_entries")

                // Recreate the index
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_numeric_entries_habitId` ON `habit_numeric_entries` (`habitId`)")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add position column to ideas (default 0 so existing rows are unaffected)
                db.execSQL("ALTER TABLE ideas ADD COLUMN position INTEGER NOT NULL DEFAULT 0")

                // Add position column to checklists
                db.execSQL("ALTER TABLE checklists ADD COLUMN position INTEGER NOT NULL DEFAULT 0")

                // Drop the grid_order table — it is no longer used
                db.execSQL("DROP TABLE IF EXISTS grid_order")
            }
        }

        // Adds reminderTime to tasks for scheduled notifications
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN reminderTime INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE subtasks ADD COLUMN reminderTime INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS categories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        position INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("ALTER TABLE tasks ADD COLUMN categoryId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE ideas ADD COLUMN categoryId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE checklists ADD COLUMN categoryId INTEGER DEFAULT NULL")
            }
        }

        /**
         * v18 → v19: Promotes the "General" category from a runtime convention
         * (categoryId == NULL) to a real row in the `categories` table with a
         * fixed id of [com.markel.flowstate.core.domain.Category.GENERAL_ID] (1).
         *
         * Steps:
         *  1. Shift any existing category ids up by 1 to free id=1 for General.
         *     This only affects dev testers on v18 who already created
         *     categories (their ids started at 1). Published users arrive at
         *     v19 from v17, where the `categories` table is still empty, so
         *     the shift is a no-op.
         *  2. Insert the General row at id=1, position=0.
         *  3. Convert every NULL categoryId on tasks/ideas/checklists to 1,
         *     so all previously-uncategorized items land in General.
         *  4. Reset the autoincrement sequence so the next user-created
         *     category gets max(id)+1.
         */
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // ── 1. Shift existing category ids up by 1 ──
                // Use negative offset to avoid transient PK collisions.
                db.execSQL("UPDATE categories SET id = -id")
                db.execSQL("UPDATE categories SET id = -id + 1")

                // Shift references on tasks / ideas / checklists
                db.execSQL("UPDATE tasks SET categoryId = categoryId + 1 WHERE categoryId IS NOT NULL")
                db.execSQL("UPDATE ideas SET categoryId = categoryId + 1 WHERE categoryId IS NOT NULL")
                db.execSQL("UPDATE checklists SET categoryId = categoryId + 1 WHERE categoryId IS NOT NULL")

                // ── 2. Insert General at id=1 ──
                db.execSQL("INSERT INTO categories (id, name, position) VALUES (1, 'General', 0)")

                // ── 3. Convert NULL → 1 (General) ──
                db.execSQL("UPDATE tasks SET categoryId = 1 WHERE categoryId IS NULL")
                db.execSQL("UPDATE ideas SET categoryId = 1 WHERE categoryId IS NULL")
                db.execSQL("UPDATE checklists SET categoryId = 1 WHERE categoryId IS NULL")

                // ── 4. Reset autoincrement sequence ──
                db.execSQL(
                    "INSERT OR REPLACE INTO sqlite_sequence (name, seq) " +
                            "VALUES ('categories', (SELECT MAX(id) FROM categories))"
                )
            }
        }

        /**
         * Replaces the unused frequency field with the concrete weekdays on
         * which the habit is scheduled. Existing habits remain daily.
         *
         * The dependent entry tables are temporarily copied and recreated so
         * the migration works on Android versions whose SQLite does not
         * support DROP COLUMN, without losing entries through FK cascades.
         */
        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE habits_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        iconName TEXT NOT NULL,
                        colorArgb INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        habitType TEXT NOT NULL,
                        unit TEXT,
                        targetValue REAL,
                        step REAL NOT NULL,
                        position INTEGER NOT NULL,
                        scheduledDays TEXT NOT NULL DEFAULT '${HabitConverters.ALL_DAYS}'
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO habits_new (
                        id, name, iconName, colorArgb, createdAt, habitType,
                        unit, targetValue, step, position, scheduledDays
                    )
                    SELECT
                        id, name, iconName, colorArgb, createdAt, habitType,
                        unit, targetValue, step, position, '${HabitConverters.ALL_DAYS}'
                    FROM habits
                    """.trimIndent()
                )

                db.execSQL(
                    "CREATE TEMP TABLE habit_entries_19_backup AS " +
                            "SELECT id, habitId, completedAt FROM habit_entries"
                )
                db.execSQL(
                    "CREATE TEMP TABLE habit_numeric_entries_19_backup AS " +
                            "SELECT habitId, epochDay, value FROM habit_numeric_entries"
                )

                db.execSQL("DROP TABLE habit_entries")
                db.execSQL("DROP TABLE habit_numeric_entries")
                db.execSQL("DROP TABLE habits")
                db.execSQL("ALTER TABLE habits_new RENAME TO habits")

                db.execSQL(
                    """
                    CREATE TABLE habit_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        habitId INTEGER NOT NULL,
                        completedAt INTEGER NOT NULL,
                        FOREIGN KEY(habitId) REFERENCES habits(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX index_habit_entries_habitId " +
                            "ON habit_entries (habitId)"
                )
                db.execSQL(
                    """
                    INSERT INTO habit_entries (id, habitId, completedAt)
                    SELECT id, habitId, completedAt
                    FROM habit_entries_19_backup
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE habit_numeric_entries (
                        habitId INTEGER NOT NULL,
                        epochDay INTEGER NOT NULL,
                        value REAL NOT NULL,
                        PRIMARY KEY(habitId, epochDay),
                        FOREIGN KEY(habitId) REFERENCES habits(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX index_habit_numeric_entries_habitId " +
                            "ON habit_numeric_entries (habitId)"
                )
                db.execSQL(
                    """
                    INSERT INTO habit_numeric_entries (habitId, epochDay, value)
                    SELECT habitId, epochDay, value
                    FROM habit_numeric_entries_19_backup
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE habit_entries_19_backup")
                db.execSQL("DROP TABLE habit_numeric_entries_19_backup")

                db.execSQL("DELETE FROM sqlite_sequence WHERE name = 'habits'")
                db.execSQL(
                    "INSERT INTO sqlite_sequence (name, seq) " +
                            "SELECT 'habits', COALESCE(MAX(id), 0) FROM habits"
                )
            }
        }
    }
}