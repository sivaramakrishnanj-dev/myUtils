package dev.sivarj.assistant.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Where a piece of content came from and how far AI enrichment has progressed.
 * Phase 1 only ever writes NONE; PENDING/DONE/FAILED are used once Bedrock
 * enrichment lands in Phase 3.
 */
enum class EnrichmentStatus { NONE, PENDING, DONE, FAILED }

enum class CategoryType { TODO, IDEA }

/**
 * Categories form a two-level tree: rows with parentId == null are top-level,
 * rows with parentId set are sub-categories. One table serves both todos and
 * ideas, discriminated by [type].
 */
@Entity(
    tableName = "categories",
    indices = [Index("parentId"), Index("type")],
)
data class Category(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: CategoryType,
    val parentId: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
)

enum class TodoStatus { OPEN, DONE }

@Entity(
    tableName = "todos",
    indices = [Index("categoryId"), Index("status")],
)
data class Todo(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val notes: String = "",
    val status: TodoStatus = TodoStatus.OPEN,
    /** Epoch millis; null = no due date. */
    val dueAt: Long? = null,
    /** May point at a top-level category or a sub-category. */
    val categoryId: String? = null,
    val rawTranscript: String? = null,
    val enrichmentStatus: EnrichmentStatus = EnrichmentStatus.NONE,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
)

@Entity(tableName = "journal_entries")
data class JournalEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val content: String,
    val rawTranscript: String? = null,
    val enrichmentStatus: EnrichmentStatus = EnrichmentStatus.NONE,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
)

@Entity(
    tableName = "ideas",
    indices = [Index("categoryId")],
)
data class Idea(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val content: String,
    val categoryId: String? = null,
    val rawTranscript: String? = null,
    val enrichmentStatus: EnrichmentStatus = EnrichmentStatus.NONE,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
)

@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
)

/**
 * One row per habit per day. [epochDay] is LocalDate.toEpochDay() in the
 * user's local timezone at the moment of check-in; streaks are computed from
 * these values, never stored.
 */
@Entity(
    tableName = "habit_checkins",
    indices = [Index(value = ["habitId", "epochDay"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = Habit::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
data class HabitCheckin(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val habitId: String,
    val epochDay: Long,
    val updatedAt: Long = System.currentTimeMillis(),
)
