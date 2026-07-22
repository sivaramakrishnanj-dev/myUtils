package dev.sivarj.assistant.ai

import android.util.Log
import dev.sivarj.assistant.settings.AwsConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Claude via the Anthropic Messages API (https://api.anthropic.com/v1/messages).
 * No SDK needed — plain OkHttp request.
 */
class AnthropicProvider(private val config: AwsConfig) : LlmProvider {

    override val name = "Anthropic API"

    override val isConfigured: Boolean
        get() = config.anthropicApiKey.isNotBlank()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun complete(systemPrompt: String, userText: String): EnrichResult {
        if (!isConfigured) return EnrichResult.Failure("Anthropic API key not configured")
        if (userText.isBlank()) return EnrichResult.Failure("Nothing to enrich")

        return try {
            val body = json.encodeToString(
                ApiRequest(
                    model = config.anthropicModelId,
                    max_tokens = 2048,
                    system = systemPrompt,
                    messages = listOf(ApiMessage(role = "user", content = userText)),
                )
            )
            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .header("x-api-key", config.anthropicApiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = try {
                    val err = Json.parseToJsonElement(responseBody).jsonObject
                    err["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                        ?: "HTTP ${response.code}"
                } catch (_: Exception) {
                    "HTTP ${response.code}: $responseBody"
                }
                return EnrichResult.Failure(errorMsg)
            }

            val jsonResponse = Json.parseToJsonElement(responseBody).jsonObject
            val text = jsonResponse["content"]?.jsonArray
                ?.filter { it.jsonObject["type"]?.jsonPrimitive?.content == "text" }
                ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }
                ?.joinToString("")
                ?: ""
            if (text.isBlank()) EnrichResult.Failure("Empty response from model")
            else EnrichResult.Success(text.trim())
        } catch (e: Exception) {
            Log.e(TAG, "Anthropic API call failed", e)
            val detail = e.message ?: "${e.javaClass.simpleName} (no message)"
            EnrichResult.Failure(detail)
        }
    }

    @Serializable
    private data class ApiRequest(
        val model: String,
        val max_tokens: Int,
        val system: String,
        val messages: List<ApiMessage>,
    )

    @Serializable
    private data class ApiMessage(val role: String, val content: String)

    private companion object {
        const val TAG = "AnthropicProvider"
    }
}
