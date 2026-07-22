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

class BedrockProvider(private val config: AwsConfig) : LlmProvider {

    override val name = "AWS Bedrock"

    override val isConfigured: Boolean
        get() = config.accessKey.isNotBlank() && config.secretKey.isNotBlank()

    override suspend fun complete(systemPrompt: String, userText: String): EnrichResult {
        if (!isConfigured) return EnrichResult.Failure("AWS credentials not configured")
        if (userText.isBlank()) return EnrichResult.Failure("Nothing to enrich")

        return try {
            val client = BedrockRuntimeClient {
                region = config.region
                credentialsProvider = StaticCredentialsProvider(config.accessKey, config.secretKey)
            }
            client.use { bedrock ->
                val response = bedrock.converse(ConverseRequest {
                    modelId = config.bedrockModelId
                    system = listOf(SystemContentBlock.Text(systemPrompt))
                    messages = listOf(
                        Message {
                            role = ConversationRole.User
                            content = listOf(ContentBlock.Text(userText))
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
            val messages = generateSequence<Throwable>(e) { it.cause }
                .mapNotNull { t -> t.message?.let { "${t.javaClass.simpleName}: $it" } }
                .toList()
            val detail = messages.lastOrNull()
                ?: "${e.javaClass.simpleName} (no message) — check logcat"
            EnrichResult.Failure(detail)
        }
    }

    private companion object {
        const val TAG = "BedrockProvider"
    }
}

private class StaticCredentialsProvider(
    private val accessKey: String,
    private val secretKey: String,
) : CredentialsProvider {
    override suspend fun resolve(attributes: Attributes): Credentials =
        Credentials(accessKeyId = accessKey, secretAccessKey = secretKey)
}
