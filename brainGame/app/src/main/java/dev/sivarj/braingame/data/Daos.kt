package dev.sivarj.braingame.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import dev.sivarj.braingame.domain.Skill
import kotlinx.coroutines.flow.Flow

@Dao
interface PuzzleDao {

    /**
     * The puzzle to resume. Ordered newest-first so that if an earlier bug ever
     * left two ACTIVE rows, the player gets the one they were actually playing
     * rather than a stale row.
     */
    @Query("SELECT * FROM puzzles WHERE status = 'ACTIVE' ORDER BY createdAt DESC LIMIT 1")
    fun observeActive(): Flow<PuzzleEntity?>

    @Query("SELECT * FROM puzzles WHERE status = 'ACTIVE' ORDER BY createdAt DESC LIMIT 1")
    suspend fun getActive(): PuzzleEntity?

    @Query("SELECT * FROM puzzles WHERE id = :id")
    suspend fun getById(id: String): PuzzleEntity?

    @Query("SELECT * FROM puzzles WHERE status != 'ACTIVE' ORDER BY updatedAt DESC LIMIT :limit")
    fun observeHistory(limit: Int = 50): Flow<List<PuzzleEntity>>

    @Query("SELECT COUNT(*) FROM puzzles WHERE status = :status")
    suspend fun countByStatus(status: PuzzleStatus): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(puzzle: PuzzleEntity)

    @Update
    suspend fun update(puzzle: PuzzleEntity)

    /**
     * Marks any lingering ACTIVE puzzle abandoned. Called before inserting a new
     * one so "New game" can never leave two playable puzzles behind.
     */
    @Query("UPDATE puzzles SET status = 'ABANDONED', updatedAt = :now WHERE status = 'ACTIVE'")
    suspend fun abandonActive(now: Long)
}

@Dao
interface SkillRatingDao {

    @Query("SELECT * FROM skill_ratings")
    fun observeAll(): Flow<List<SkillRatingEntity>>

    @Query("SELECT * FROM skill_ratings")
    suspend fun getAll(): List<SkillRatingEntity>

    @Query("SELECT * FROM skill_ratings WHERE skill = :skill")
    suspend fun get(skill: Skill): SkillRatingEntity?

    @Upsert
    suspend fun upsert(rating: SkillRatingEntity)
}
