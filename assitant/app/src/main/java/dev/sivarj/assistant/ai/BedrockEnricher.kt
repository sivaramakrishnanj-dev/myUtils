package dev.sivarj.assistant.ai

import android.util.Log
import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.sdk.kotlin.services.bedrockruntime.model.ContentBlock
import aws.sdk.kotlin.services.bedrockruntime.model.ConversationRole
import aws.sdk.kotlin.services.bedrockruntime.model.ConverseRequest
import aws.sdk.kotlin.services.bedrockruntime.model.Message
import aws.sdk.kotlin.services.bedrockruntime.model.SystemContentBlock
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.auth.awscredentials.CredentialsProvider
import aws.smithy.kotlin.runtime.collections.Attributes
import dev.sivarj.assistant.settings.AwsConfig

sealed interface EnrichResult {
    data class Success(val text: String) : EnrichResult
    data class Failure(val error: String) : EnrichResult
}

enum class ContentType { TODO, JOURNAL, IDEA, APPOINTMENT }

class BedrockEnricher(private val config: AwsConfig) {

    suspend fun enrich(rawText: String, type: ContentType): EnrichResult {
        if (!config.isConfigured) return EnrichResult.Failure("AWS credentials not configured")
        if (rawText.isBlank()) return EnrichResult.Failure("Nothing to enrich")

        return try {
            val client = BedrockRuntimeClient {
                region = config.region
                credentialsProvider = StaticCredentialsProvider(config.accessKey, config.secretKey)
            }
            val systemPrompt = when (type) {
                ContentType.TODO -> config.promptTodo
                ContentType.JOURNAL -> config.promptJournal
                ContentType.IDEA -> config.promptIdea
                ContentType.APPOINTMENT -> config.promptAppointment
            }
            client.use { bedrock ->
                val response = bedrock.converse(ConverseRequest {
                    modelId = config.bedrockModelId
                    system = listOf(SystemContentBlock.Text(systemPrompt))
                    messages = listOf(
                        Message {
                            role = ConversationRole.User
                            content = listOf(ContentBlock.Text(rawText))
                        }
                    )
                })
                val text = response.output
                    ?.let { it as? aws.sdk.kotlin.services.bedrockruntime.model.ConverseOutput.Message }
                    ?.value?.content
                    ?.filterIsInstance<ContentBlock.Text>()
                    ?.joinToString("") { it.value }
                    ?: ""
                if (text.isBlank()) EnrichResult.Failure("Empty response from model")
                else EnrichResult.Success(text.trim())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bedrock converse failed", e)
            // Surface the deepest meaningful message so the UI shows the real
            // cause, not just the top-level wrapper (whose message is often null).
            val messages = generateSequence<Throwable>(e) { it.cause }
                .mapNotNull { t -> t.message?.let { "${t.javaClass.simpleName}: $it" } }
                .toList()
            val detail = messages.lastOrNull()
                ?: "${e.javaClass.simpleName} (no message) — check logcat"
            EnrichResult.Failure(detail)
        }
    }

    private companion object {
        const val TAG = "BedrockEnricher"
    }
}

private class StaticCredentialsProvider(
    private val accessKey: String,
    private val secretKey: String,
) : CredentialsProvider {
    override suspend fun resolve(attributes: Attributes): Credentials =
        Credentials(accessKeyId = accessKey, secretAccessKey = secretKey)
}
