package dev.sivarj.assistant.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Category::class,
        Todo::class,
        JournalEntry::class,
        Idea::class,
        Habit::class,
        HabitCheckin::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun todoDao(): TodoDao
    abstract fun journalDao(): JournalDao
    abstract fun ideaDao(): IdeaDao
    abstract fun habitDao(): HabitDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "assistant.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
