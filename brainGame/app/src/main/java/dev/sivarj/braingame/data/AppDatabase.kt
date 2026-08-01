package dev.sivarj.braingame.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import dev.sivarj.braingame.domain.Skill

/** Stores enums as their names so a reordered enum can't silently remap rows. */
class Converters {
    @TypeConverter fun skillToString(value: Skill): String = value.name

    @TypeConverter fun stringToSkill(value: String): Skill =
        runCatching { Skill.valueOf(value) }.getOrDefault(Skill.ARITHMETIC)

    @TypeConverter fun statusToString(value: PuzzleStatus): String = value.name

    @TypeConverter fun stringToStatus(value: String): PuzzleStatus =
        runCatching { PuzzleStatus.valueOf(value) }.getOrDefault(PuzzleStatus.ABANDONED)
}

@Database(
    entities = [PuzzleEntity::class, SkillRatingEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun puzzleDao(): PuzzleDao
    abstract fun skillRatingDao(): SkillRatingDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "braingame.db").build()
    }
}
