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
    ],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun todoDao(): TodoDao
    abstract fun journalDao(): JournalDao
    abstract fun ideaDao(): IdeaDao
    abstract fun habitDao(): HabitDao
    abstract fun appointmentDao(): AppointmentDao

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

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "assistant.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
