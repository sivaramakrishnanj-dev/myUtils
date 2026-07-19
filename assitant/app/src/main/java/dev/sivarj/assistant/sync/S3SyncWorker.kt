package dev.sivarj.assistant.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.auth.awscredentials.CredentialsProvider
import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.content.ByteStream
import dev.sivarj.assistant.AssistantApp
import dev.sivarj.assistant.data.AppDatabase
import dev.sivarj.assistant.settings.AppSettings
import dev.sivarj.assistant.settings.AwsConfig
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { prettyPrint = true; encodeDefaults = true }

class S3SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as AssistantApp
        val config = AppSettings(applicationContext).awsConfig.first()
        if (!config.isConfigured || config.s3Bucket.isBlank()) return Result.success()

        return try {
            val db = app.database
            syncAll(db, config)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private suspend fun syncAll(db: AppDatabase, config: AwsConfig) {
        val s3 = S3Client {
            region = config.region
            credentialsProvider = StaticCreds(config.accessKey, config.secretKey)
        }
        s3.use { client ->
            syncTodos(client, db, config.s3Bucket)
            syncJournal(client, db, config.s3Bucket)
            syncIdeas(client, db, config.s3Bucket)
            syncHabits(client, db, config.s3Bucket)
            syncCategories(client, db, config.s3Bucket)
        }
    }

    private suspend fun syncTodos(client: S3Client, db: AppDatabase, bucket: String) {
        val todos = db.todoDao().observeAll().first()
        todos.forEach { todo ->
            val payload = json.encodeToString(
                SyncTodo(
                    id = todo.id, title = todo.title, notes = todo.notes,
                    status = todo.status.name, categoryId = todo.categoryId,
                    dueAt = todo.dueAt, createdAt = todo.createdAt, updatedAt = todo.updatedAt,
                )
            )
            putObject(client, bucket, "todos/${todo.id}.json", payload)
        }
    }

    private suspend fun syncJournal(client: S3Client, db: AppDatabase, bucket: String) {
        val entries = db.journalDao().observeAll().first()
        entries.forEach { entry ->
            val payload = json.encodeToString(
                SyncJournal(
                    id = entry.id, content = entry.content,
                    createdAt = entry.createdAt, updatedAt = entry.updatedAt,
                )
            )
            putObject(client, bucket, "journal/${entry.id}.json", payload)
        }
    }

    private suspend fun syncIdeas(client: S3Client, db: AppDatabase, bucket: String) {
        val ideas = db.ideaDao().observeAll().first()
        ideas.forEach { idea ->
            val payload = json.encodeToString(
                SyncIdea(
                    id = idea.id, content = idea.content, categoryId = idea.categoryId,
                    createdAt = idea.createdAt, updatedAt = idea.updatedAt,
                )
            )
            putObject(client, bucket, "ideas/${idea.id}.json", payload)
        }
    }

    private suspend fun syncHabits(client: S3Client, db: AppDatabase, bucket: String) {
        val habits = db.habitDao().observeAll().first()
        val checkins = db.habitDao().observeAllCheckins().first()
        habits.forEach { habit ->
            val habitCheckins = checkins.filter { it.habitId == habit.id }.map { it.epochDay }
            val payload = json.encodeToString(
                SyncHabit(
                    id = habit.id, name = habit.name, archived = habit.archived,
                    checkinDays = habitCheckins,
                    createdAt = habit.createdAt, updatedAt = habit.updatedAt,
                )
            )
            putObject(client, bucket, "habits/${habit.id}.json", payload)
        }
    }

    private suspend fun syncCategories(client: S3Client, db: AppDatabase, bucket: String) {
        val todoCats = db.categoryDao().observeByType(dev.sivarj.assistant.data.CategoryType.TODO).first()
        val ideaCats = db.categoryDao().observeByType(dev.sivarj.assistant.data.CategoryType.IDEA).first()
        val all = todoCats + ideaCats
        all.forEach { cat ->
            val payload = json.encodeToString(
                SyncCategory(
                    id = cat.id, name = cat.name, type = cat.type.name,
                    parentId = cat.parentId, updatedAt = cat.updatedAt,
                )
            )
            putObject(client, bucket, "categories/${cat.id}.json", payload)
        }
    }

    private suspend fun putObject(client: S3Client, bucket: String, key: String, body: String) {
        client.putObject(PutObjectRequest {
            this.bucket = bucket
            this.key = key
            this.body = ByteStream.fromString(body)
            contentType = "application/json"
        })
    }
}

private class StaticCreds(private val ak: String, private val sk: String) : CredentialsProvider {
    override suspend fun resolve(attributes: Attributes) = Credentials(accessKeyId = ak, secretAccessKey = sk)
}

@Serializable data class SyncTodo(
    val id: String, val title: String, val notes: String, val status: String,
    val categoryId: String?, val dueAt: Long?, val createdAt: Long, val updatedAt: Long,
)
@Serializable data class SyncJournal(
    val id: String, val content: String, val createdAt: Long, val updatedAt: Long,
)
@Serializable data class SyncIdea(
    val id: String, val content: String, val categoryId: String?,
    val createdAt: Long, val updatedAt: Long,
)
@Serializable data class SyncHabit(
    val id: String, val name: String, val archived: Boolean,
    val checkinDays: List<Long>, val createdAt: Long, val updatedAt: Long,
)
@Serializable data class SyncCategory(
    val id: String, val name: String, val type: String,
    val parentId: String?, val updatedAt: Long,
)
