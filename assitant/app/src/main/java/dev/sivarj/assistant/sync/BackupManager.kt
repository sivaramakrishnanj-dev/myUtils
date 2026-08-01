package dev.sivarj.assistant.sync

import android.content.Context
import android.net.Uri
import dev.sivarj.assistant.data.AppDatabase
import dev.sivarj.assistant.data.Appointment
import dev.sivarj.assistant.data.Category
import dev.sivarj.assistant.data.CategoryType
import dev.sivarj.assistant.data.EnrichmentStatus
import dev.sivarj.assistant.data.Habit
import dev.sivarj.assistant.data.HabitCheckin
import dev.sivarj.assistant.data.Idea
import dev.sivarj.assistant.data.JournalEntry
import dev.sivarj.assistant.data.Todo
import dev.sivarj.assistant.data.TodoStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private val json = Json { prettyPrint = false; encodeDefaults = true; ignoreUnknownKeys = true }

@Serializable
data class BackupTodo(
    val id: String, val title: String, val notes: String, val status: String,
    val categoryId: String? = null, val dueAt: Long? = null,
    val rawTranscript: String? = null, val enrichmentStatus: String = "NONE",
    val createdAt: Long, val updatedAt: Long, val deleted: Boolean = false,
)

@Serializable
data class BackupJournal(
    val id: String, val content: String,
    val rawTranscript: String? = null, val enrichmentStatus: String = "NONE",
    val createdAt: Long, val updatedAt: Long, val deleted: Boolean = false,
)

@Serializable
data class BackupIdea(
    val id: String, val title: String = "", val content: String, val categoryId: String? = null,
    val rawTranscript: String? = null, val enrichmentStatus: String = "NONE",
    val createdAt: Long, val updatedAt: Long, val deleted: Boolean = false,
)

@Serializable
data class BackupHabit(
    val id: String, val name: String, val description: String = "",
    val archived: Boolean = false,
    val createdAt: Long, val updatedAt: Long, val deleted: Boolean = false,
)

@Serializable
data class BackupCheckin(
    val id: String, val habitId: String, val epochDay: Long, val updatedAt: Long,
)

@Serializable
data class BackupCategory(
    val id: String, val name: String, val type: String,
    val parentId: String? = null, val updatedAt: Long, val deleted: Boolean = false,
)

@Serializable
data class BackupAppointment(
    val id: String, val title: String, val notes: String = "",
    val epochDay: Long, val startMinutes: Int, val endMinutes: Int,
    val rawTranscript: String? = null,
    val createdAt: Long, val updatedAt: Long, val deleted: Boolean = false,
)

class BackupManager(private val context: Context, private val db: AppDatabase) {

    suspend fun exportToUri(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val out = context.contentResolver.openOutputStream(uri)
                ?: error("Cannot open output stream")
            out.use { exportToStream(it) }
        }
    }

    suspend fun importFromUri(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val inp = context.contentResolver.openInputStream(uri)
                ?: error("Cannot open input stream")
            inp.use { importFromStream(it) }
        }
    }

    private suspend fun exportToStream(out: OutputStream): Int {
        var count = 0
        ZipOutputStream(out).use { zip ->
            // Todos
            val todos = db.todoDao().observeAll().first()
            zip.putNextEntry(ZipEntry("todos.json"))
            val todosJson = json.encodeToString(todos.map { t ->
                BackupTodo(t.id, t.title, t.notes, t.status.name, t.categoryId, t.dueAt,
                    t.rawTranscript, t.enrichmentStatus.name, t.createdAt, t.updatedAt, t.deleted)
            })
            zip.write(todosJson.toByteArray())
            zip.closeEntry()
            count += todos.size

            // Journal
            val entries = db.journalDao().observeAll().first()
            zip.putNextEntry(ZipEntry("journal.json"))
            zip.write(json.encodeToString(entries.map { e ->
                BackupJournal(e.id, e.content, e.rawTranscript, e.enrichmentStatus.name,
                    e.createdAt, e.updatedAt, e.deleted)
            }).toByteArray())
            zip.closeEntry()
            count += entries.size

            // Ideas
            val ideas = db.ideaDao().observeAll().first()
            zip.putNextEntry(ZipEntry("ideas.json"))
            zip.write(json.encodeToString(ideas.map { i ->
                BackupIdea(i.id, i.title, i.content, i.categoryId, i.rawTranscript,
                    i.enrichmentStatus.name, i.createdAt, i.updatedAt, i.deleted)
            }).toByteArray())
            zip.closeEntry()
            count += ideas.size

            // Habits + checkins
            val habits = db.habitDao().observeAll().first()
            zip.putNextEntry(ZipEntry("habits.json"))
            zip.write(json.encodeToString(habits.map { h ->
                BackupHabit(h.id, h.name, h.description, h.archived, h.createdAt, h.updatedAt, h.deleted)
            }).toByteArray())
            zip.closeEntry()
            count += habits.size

            val checkins = db.habitDao().observeAllCheckins().first()
            zip.putNextEntry(ZipEntry("checkins.json"))
            zip.write(json.encodeToString(checkins.map { c ->
                BackupCheckin(c.id, c.habitId, c.epochDay, c.updatedAt)
            }).toByteArray())
            zip.closeEntry()

            // Categories
            val cats = db.categoryDao().observeByType(CategoryType.TODO).first() +
                db.categoryDao().observeByType(CategoryType.IDEA).first()
            zip.putNextEntry(ZipEntry("categories.json"))
            zip.write(json.encodeToString(cats.map { c ->
                BackupCategory(c.id, c.name, c.type.name, c.parentId, c.updatedAt, c.deleted)
            }).toByteArray())
            zip.closeEntry()

            // Appointments
            val appts = db.appointmentDao().observeAll().first()
            zip.putNextEntry(ZipEntry("appointments.json"))
            zip.write(json.encodeToString(appts.map { a ->
                BackupAppointment(a.id, a.title, a.notes, a.epochDay, a.startMinutes,
                    a.endMinutes, a.rawTranscript, a.createdAt, a.updatedAt, a.deleted)
            }).toByteArray())
            zip.closeEntry()
            count += appts.size
        }
        return count
    }

    private suspend fun importFromStream(inp: InputStream): Int {
        var count = 0
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(inp).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = zip.readBytes()
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        entries["categories.json"]?.let { bytes ->
            val cats = json.decodeFromString<List<BackupCategory>>(String(bytes))
            cats.forEach { c ->
                db.categoryDao().upsert(Category(
                    id = c.id, name = c.name,
                    type = CategoryType.valueOf(c.type),
                    parentId = c.parentId, updatedAt = c.updatedAt, deleted = c.deleted,
                ))
            }
        }

        entries["todos.json"]?.let { bytes ->
            val todos = json.decodeFromString<List<BackupTodo>>(String(bytes))
            todos.forEach { t ->
                db.todoDao().upsert(Todo(
                    id = t.id, title = t.title, notes = t.notes,
                    status = TodoStatus.valueOf(t.status),
                    categoryId = t.categoryId, dueAt = t.dueAt,
                    rawTranscript = t.rawTranscript,
                    enrichmentStatus = EnrichmentStatus.valueOf(t.enrichmentStatus),
                    createdAt = t.createdAt, updatedAt = t.updatedAt, deleted = t.deleted,
                ))
            }
            count += todos.size
        }

        entries["journal.json"]?.let { bytes ->
            val items = json.decodeFromString<List<BackupJournal>>(String(bytes))
            items.forEach { e ->
                db.journalDao().upsert(JournalEntry(
                    id = e.id, content = e.content,
                    rawTranscript = e.rawTranscript,
                    enrichmentStatus = EnrichmentStatus.valueOf(e.enrichmentStatus),
                    createdAt = e.createdAt, updatedAt = e.updatedAt, deleted = e.deleted,
                ))
            }
            count += items.size
        }

        entries["ideas.json"]?.let { bytes ->
            val items = json.decodeFromString<List<BackupIdea>>(String(bytes))
            items.forEach { i ->
                db.ideaDao().upsert(Idea(
                    id = i.id, title = i.title, content = i.content, categoryId = i.categoryId,
                    rawTranscript = i.rawTranscript,
                    enrichmentStatus = EnrichmentStatus.valueOf(i.enrichmentStatus),
                    createdAt = i.createdAt, updatedAt = i.updatedAt, deleted = i.deleted,
                ))
            }
            count += items.size
        }

        entries["habits.json"]?.let { bytes ->
            val items = json.decodeFromString<List<BackupHabit>>(String(bytes))
            items.forEach { h ->
                db.habitDao().upsert(Habit(
                    id = h.id, name = h.name, description = h.description, archived = h.archived,
                    createdAt = h.createdAt, updatedAt = h.updatedAt, deleted = h.deleted,
                ))
            }
            count += items.size
        }

        entries["checkins.json"]?.let { bytes ->
            val items = json.decodeFromString<List<BackupCheckin>>(String(bytes))
            items.forEach { c ->
                db.habitDao().insertCheckin(HabitCheckin(
                    id = c.id, habitId = c.habitId, epochDay = c.epochDay, updatedAt = c.updatedAt,
                ))
            }
        }

        entries["appointments.json"]?.let { bytes ->
            val items = json.decodeFromString<List<BackupAppointment>>(String(bytes))
            items.forEach { a ->
                db.appointmentDao().upsert(Appointment(
                    id = a.id, title = a.title, notes = a.notes,
                    epochDay = a.epochDay, startMinutes = a.startMinutes, endMinutes = a.endMinutes,
                    rawTranscript = a.rawTranscript,
                    createdAt = a.createdAt, updatedAt = a.updatedAt, deleted = a.deleted,
                ))
            }
            count += items.size
        }

        return count
    }
}
