package dev.sivarj.assistant.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE type = :type AND deleted = 0 ORDER BY name")
    fun observeByType(type: CategoryType): Flow<List<Category>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: Category)

    @Query("UPDATE categories SET deleted = 1, updatedAt = :now WHERE id = :id OR parentId = :id")
    suspend fun softDeleteWithChildren(id: String, now: Long = System.currentTimeMillis())
}

@Dao
interface TodoDao {
    @Query("SELECT * FROM todos WHERE deleted = 0 ORDER BY status ASC, dueAt IS NULL, dueAt ASC, createdAt DESC")
    fun observeAll(): Flow<List<Todo>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(todo: Todo)

    @Update
    suspend fun update(todo: Todo)

    @Query("UPDATE todos SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long = System.currentTimeMillis())
}

@Dao
interface JournalDao {
    @Query("SELECT * FROM journal_entries WHERE deleted = 0 ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<JournalEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: JournalEntry)

    @Query("UPDATE journal_entries SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long = System.currentTimeMillis())
}

@Dao
interface IdeaDao {
    @Query("SELECT * FROM ideas WHERE deleted = 0 ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Idea>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(idea: Idea)

    @Query("UPDATE ideas SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long = System.currentTimeMillis())
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE deleted = 0 ORDER BY archived ASC, createdAt ASC")
    fun observeAll(): Flow<List<Habit>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(habit: Habit)

    @Query("UPDATE habits SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM habit_checkins WHERE habitId IN (SELECT id FROM habits WHERE deleted = 0)")
    fun observeAllCheckins(): Flow<List<HabitCheckin>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCheckin(checkin: HabitCheckin)

    @Query("DELETE FROM habit_checkins WHERE habitId = :habitId AND epochDay = :epochDay")
    suspend fun deleteCheckin(habitId: String, epochDay: Long)
}
