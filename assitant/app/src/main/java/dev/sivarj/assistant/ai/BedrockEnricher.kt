package dev.sivarj.assistant.ai

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

enum class ContentType { TODO, JOURNAL, IDEA }

object Prompts {
    fun system(type: ContentType): String = when (type) {
        ContentType.TODO -> """
            You are an AI assistant that converts raw voice-transcribed text into clear,
            actionable todo items. Fix grammar and spelling errors. If the text describes
            multiple tasks, output each one as a separate line prefixed with "- ". If it is
            a single task, output just the cleaned-up title on one line. Do NOT add
            commentary or explanation — output only the todo items.
        """.trimIndent()

        ContentType.JOURNAL -> """
            You are an AI assistant that cleans up raw voice-transcribed journal entries.
            Fix grammar, spelling, and punctuation errors. Keep the writer's voice and tone.
            Do NOT editorialize, add commentary, or summarize — output the corrected entry
            only.
        """.trimIndent()

        ContentType.IDEA -> """
            You are an AI assistant that cleans up raw voice-transcribed idea notes.
            Fix grammar and spelling errors while preserving the original meaning. Make it
            clear and concise. Do NOT add commentary — output the cleaned-up idea only.
        """.trimIndent()
    }
}

class BedrockEnricher(private val config: AwsConfig) {

    suspend fun enrich(rawText: String, type: ContentType): EnrichResult {
        if (!config.isConfigured) return EnrichResult.Failure("AWS credentials not configured")
        if (rawText.isBlank()) return EnrichResult.Failure("Nothing to enrich")

        return try {
            val client = BedrockRuntimeClient {
                region = config.region
                credentialsProvider = StaticCredentialsProvider(config.accessKey, config.secretKey)
            }
            client.use { bedrock ->
                val response = bedrock.converse(ConverseRequest {
                    modelId = config.bedrockModelId
                    system = listOf(SystemContentBlock.Text(Prompts.system(type)))
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
            EnrichResult.Failure(e.message ?: "Unknown error calling Bedrock")
        }
    }
}

private class StaticCredentialsProvider(
    private val accessKey: String,
    private val secretKey: String,
) : CredentialsProvider {
    override suspend fun resolve(attributes: Attributes): Credentials =
        Credentials(accessKeyId = accessKey, secretAccessKey = secretKey)
}
