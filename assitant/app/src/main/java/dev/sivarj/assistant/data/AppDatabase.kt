package dev.sivarj.assistant.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Category::class,
        Todo::class,
        JournalEntry::class,
        Idea::class,
        Habit::class,
        HabitCheckin::class,
        Appointment::class,
        Prayer::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun todoDao(): TodoDao
    abstract fun journalDao(): JournalDao
    abstract fun ideaDao(): IdeaDao
    abstract fun habitDao(): HabitDao
    abstract fun appointmentDao(): AppointmentDao
    abstract fun prayerDao(): PrayerDao

    companion object {
        /** v2 adds the appointments table; existing data must survive. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `appointments` (
                        `id` TEXT NOT NULL, `title` TEXT NOT NULL, `notes` TEXT NOT NULL,
                        `epochDay` INTEGER NOT NULL, `startMinutes` INTEGER NOT NULL,
                        `endMinutes` INTEGER NOT NULL, `rawTranscript` TEXT,
                        `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL,
                        `deleted` INTEGER NOT NULL, PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_appointments_epochDay` ON `appointments` (`epochDay`)")
            }
        }

        /** v3 adds habits.description. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `habits` ADD COLUMN `description` TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v4 adds ideas.title (Notes get an LLM-proposed heading). */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `ideas` ADD COLUMN `title` TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v5 adds the prayers table. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `prayers` (
                        `id` TEXT NOT NULL, `situation` TEXT NOT NULL,
                        `content` TEXT NOT NULL, `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL, `deleted` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "assistant.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
    }
}
