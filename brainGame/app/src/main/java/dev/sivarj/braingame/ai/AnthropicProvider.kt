package dev.sivarj.braingame.ai

import android.util.Log
import dev.sivarj.braingame.settings.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Claude via the Anthropic Messages API. Plain OkHttp — no SDK.
 *
 * Ported from the assistant app's provider, with three changes this app needs:
 * a cacheable system prompt, `output_config` carrying both a JSON schema and an
 * effort level, and explicit `stop_reason: "refusal"` handling.
 */
class AnthropicProvider(private val config: AppConfig) : LlmProvider {

    override val name = "Anthropic API"

    override val isConfigured: Boolean
        get() = config.apiKey.isNotBlank()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        // Generous: a thinking model on a hard puzzle can legitimately take a while.
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun complete(request: LlmRequest): LlmResult = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext LlmResult.Failure("API key not set — add it in Settings")

        try {
            val payload = buildRequestBody(request)
            val httpRequest = Request.Builder()
                .url(ENDPOINT)
                .header("x-api-key", config.apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .header("content-type", "application/json")
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(httpRequest).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext LlmResult.Failure(errorMessage(response.code, body))
                }
                parseResponse(body)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Anthropic API call failed", e)
            LlmResult.Failure(e.message ?: "${e.javaClass.simpleName} (no message)")
        }
    }

    private fun buildRequestBody(request: LlmRequest): String {
        val json = buildJsonObject {
            put("model", config.modelId)
            put("max_tokens", request.maxTokens)

            // System prompt as a block array so it can carry cache_control. The
            // varying part of the request lives in the user turn, keeping this
            // prefix byte-identical and therefore cacheable.
            put("system", buildJsonArray {
                add(buildJsonObject {
                    put("type", "text")
                    put("text", request.systemPrompt)
                    putJsonObject("cache_control") { put("type", "ephemeral") }
                })
            })

            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", request.userText)
                })
            })

            putJsonObject("output_config") {
                put("effort", request.effort.wireValue)
                request.jsonSchema?.let { schema ->
                    putJsonObject("format") {
                        put("type", "json_schema")
                        put("schema", schema)
                    }
                }
            }
        }
        return json.toString()
    }

    private fun parseResponse(body: String): LlmResult {
        val root = Json.parseToJsonElement(body).jsonObject

        // Check stop_reason before reading content: on a refusal the content
        // array is empty or partial, so indexing it first would look like an
        // empty-response bug rather than a policy decline.
        val stopReason = root["stop_reason"]?.jsonPrimitive?.contentOrNullSafe
        if (stopReason == "refusal") {
            val explanation = root["stop_details"]?.jsonObject
                ?.get("explanation")?.jsonPrimitive?.contentOrNullSafe
            return LlmResult.Failure(
                explanation ?: "The model declined this request. Try a different theme."
            )
        }

        val text = root["content"]?.jsonArray
            ?.filter { it.jsonObject["type"]?.jsonPrimitive?.contentOrNullSafe == "text" }
            ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNullSafe }
            ?.joinToString("")
            .orEmpty()

        logCacheUsage(root)

        return if (text.isBlank()) {
            if (stopReason == "max_tokens") {
                LlmResult.Failure("Response hit the token limit before producing output")
            } else {
                LlmResult.Failure("Empty response from model")
            }
        } else {
            LlmResult.Success(text.trim())
        }
    }

    /**
     * Cache hits are invisible unless checked: a silent invalidator shows up as
     * cache_read staying at zero across a session rather than as an error.
     */
    private fun logCacheUsage(root: JsonObject) {
        val usage = root["usage"]?.jsonObject ?: return
        fun field(key: String) = usage[key]?.jsonPrimitive?.contentOrNullSafe ?: "0"
        Log.d(
            TAG,
            "tokens in=${field("input_tokens")} out=${field("output_tokens")} " +
                "cache_write=${field("cache_creation_input_tokens")} " +
                "cache_read=${field("cache_read_input_tokens")}",
        )
    }

    private fun errorMessage(code: Int, body: String): String = try {
        val message = Json.parseToJsonElement(body).jsonObject["error"]
            ?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNullSafe
        message ?: "HTTP $code"
    } catch (_: Exception) {
        "HTTP $code: ${body.take(200)}"
    }

    private companion object {
        const val TAG = "AnthropicProvider"
        const val ENDPOINT = "https://api.anthropic.com/v1/messages"
        const val ANTHROPIC_VERSION = "2023-06-01"
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

/**
 * `jsonPrimitive.content` throws on a JSON null; this returns null instead so
 * optional response fields can be read without a guard at every call site.
 */
private val kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe: String?
    get() = if (this is kotlinx.serialization.json.JsonNull) null else content
