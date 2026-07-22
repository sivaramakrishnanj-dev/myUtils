package dev.sivarj.assistant.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "settings")
private val json = Json { ignoreUnknownKeys = true }

object PrefsKeys {
    val LLM_PROVIDER = stringPreferencesKey("llm_provider")
    val AWS_ACCESS_KEY = stringPreferencesKey("aws_access_key")
    val AWS_SECRET_KEY = stringPreferencesKey("aws_secret_key")
    val AWS_REGION = stringPreferencesKey("aws_region")
    val BEDROCK_MODEL_ID = stringPreferencesKey("bedrock_model_id")
    val ANTHROPIC_API_KEY = stringPreferencesKey("anthropic_api_key")
    val ANTHROPIC_MODEL_ID = stringPreferencesKey("anthropic_model_id")
    // Same key as the pre-provider era: old {"id","name"} JSON decodes into
    // LlmModel with the default provider (BEDROCK), so existing custom models survive.
    val CUSTOM_MODELS = stringPreferencesKey("custom_models")
    val PROMPT_TODO = stringPreferencesKey("prompt_todo")
    val PROMPT_JOURNAL = stringPreferencesKey("prompt_journal")
    val PROMPT_IDEA = stringPreferencesKey("prompt_idea")
    val PROMPT_APPOINTMENT = stringPreferencesKey("prompt_appointment")
}

enum class LlmProviderKind(val displayName: String) {
    BEDROCK("AWS Bedrock"),
    ANTHROPIC("Anthropic API"),
}

@Serializable
data class LlmModel(
    val id: String,
    val name: String,
    val provider: LlmProviderKind = LlmProviderKind.BEDROCK,
)

/** Models shipped with the app; user-added models are appended after these. */
val BUILT_IN_MODELS = listOf(
    // Bedrock model IDs carry region/vendor prefixes and version suffixes.
    LlmModel("us.anthropic.claude-sonnet-4-20250514-v1:0", "Claude Sonnet 4", LlmProviderKind.BEDROCK),
    LlmModel("us.anthropic.claude-haiku-4-5-20251001-v1:0", "Claude Haiku 4.5", LlmProviderKind.BEDROCK),
    LlmModel("anthropic.claude-3-haiku-20240307-v1:0", "Claude 3 Haiku", LlmProviderKind.BEDROCK),
    LlmModel("anthropic.claude-3-5-sonnet-20241022-v2:0", "Claude 3.5 Sonnet v2", LlmProviderKind.BEDROCK),
    // Anthropic API uses clean aliases.
    LlmModel("claude-sonnet-5", "Claude Sonnet 5", LlmProviderKind.ANTHROPIC),
    LlmModel("claude-haiku-4-5", "Claude Haiku 4.5", LlmProviderKind.ANTHROPIC),
    LlmModel("claude-opus-4-8", "Claude Opus 4.8", LlmProviderKind.ANTHROPIC),
)

object DefaultPrompts {
    val TODO = """
        You are an AI assistant that converts raw voice-transcribed text into clear,
        actionable todo items. Fix grammar and spelling errors. If the text describes
        multiple tasks, output each one as a separate line prefixed with "- ". If it is
        a single task, output just the cleaned-up title on one line. Do NOT add
        commentary or explanation — output only the todo items.
    """.trimIndent()

    val JOURNAL = """
        You are an AI assistant that cleans up raw voice-transcribed journal entries.
        Fix grammar, spelling, and punctuation errors. Keep the writer's voice and tone.
        Do NOT editorialize, add commentary, or summarize — output the corrected entry
        only.
    """.trimIndent()

    val IDEA = """
        You are an AI assistant that cleans up raw voice-transcribed idea notes.
        Fix grammar and spelling errors while preserving the original meaning. Make it
        clear and concise. Do NOT add commentary — output the cleaned-up idea only.
    """.trimIndent()

    val APPOINTMENT = """
        You are an AI assistant that extracts appointment details from voice-transcribed
        text. Output ONLY a JSON object with these fields:
        {"title": "...", "notes": "...", "startHour": H, "startMin": M, "endHour": H, "endMin": M}
        Hours are 0-23 (24h format). If no end time is mentioned, default to startHour+1.
        If no notes are mentioned, use an empty string. Do NOT add commentary or
        explanation — output only the JSON object.
    """.trimIndent()
}

data class AwsConfig(
    val provider: LlmProviderKind = LlmProviderKind.BEDROCK,
    val accessKey: String = "",
    val secretKey: String = "",
    val region: String = "us-east-1",
    val bedrockModelId: String = BUILT_IN_MODELS.first { it.provider == LlmProviderKind.BEDROCK }.id,
    val anthropicApiKey: String = "",
    val anthropicModelId: String = BUILT_IN_MODELS.first { it.provider == LlmProviderKind.ANTHROPIC }.id,
    val customModels: List<LlmModel> = emptyList(),
    val promptTodo: String = DefaultPrompts.TODO,
    val promptJournal: String = DefaultPrompts.JOURNAL,
    val promptIdea: String = DefaultPrompts.IDEA,
    val promptAppointment: String = DefaultPrompts.APPOINTMENT,
) {
    val isConfigured: Boolean
        get() = when (provider) {
            LlmProviderKind.BEDROCK -> accessKey.isNotBlank() && secretKey.isNotBlank()
            LlmProviderKind.ANTHROPIC -> anthropicApiKey.isNotBlank()
        }

    /** The selected model for the active provider. */
    val activeModelId: String
        get() = when (provider) {
            LlmProviderKind.BEDROCK -> bedrockModelId
            LlmProviderKind.ANTHROPIC -> anthropicModelId
        }

    /** Built-in + user models for [forProvider], deduped by id. */
    fun modelsFor(forProvider: LlmProviderKind): List<LlmModel> {
        val builtIn = BUILT_IN_MODELS.filter { it.provider == forProvider }
        val custom = customModels.filter { it.provider == forProvider && builtIn.none { b -> b.id == it.id } }
        return builtIn + custom
    }
}

class AppSettings(private val context: Context) {

    val awsConfig: Flow<AwsConfig> = context.dataStore.data.map { prefs ->
        AwsConfig(
            provider = prefs[PrefsKeys.LLM_PROVIDER]?.let {
                runCatching { LlmProviderKind.valueOf(it) }.getOrNull()
            } ?: LlmProviderKind.BEDROCK,
            accessKey = prefs[PrefsKeys.AWS_ACCESS_KEY] ?: "",
            secretKey = prefs[PrefsKeys.AWS_SECRET_KEY] ?: "",
            region = prefs[PrefsKeys.AWS_REGION] ?: "us-east-1",
            bedrockModelId = prefs[PrefsKeys.BEDROCK_MODEL_ID]
                ?: BUILT_IN_MODELS.first { it.provider == LlmProviderKind.BEDROCK }.id,
            anthropicApiKey = prefs[PrefsKeys.ANTHROPIC_API_KEY] ?: "",
            anthropicModelId = prefs[PrefsKeys.ANTHROPIC_MODEL_ID]
                ?: BUILT_IN_MODELS.first { it.provider == LlmProviderKind.ANTHROPIC }.id,
            customModels = prefs[PrefsKeys.CUSTOM_MODELS]?.let {
                runCatching { json.decodeFromString<List<LlmModel>>(it) }.getOrDefault(emptyList())
            } ?: emptyList(),
            promptTodo = prefs[PrefsKeys.PROMPT_TODO] ?: DefaultPrompts.TODO,
            promptJournal = prefs[PrefsKeys.PROMPT_JOURNAL] ?: DefaultPrompts.JOURNAL,
            promptIdea = prefs[PrefsKeys.PROMPT_IDEA] ?: DefaultPrompts.IDEA,
            promptAppointment = prefs[PrefsKeys.PROMPT_APPOINTMENT] ?: DefaultPrompts.APPOINTMENT,
        )
    }

    suspend fun save(config: AwsConfig) {
        context.dataStore.edit { prefs ->
            prefs[PrefsKeys.LLM_PROVIDER] = config.provider.name
            prefs[PrefsKeys.AWS_ACCESS_KEY] = config.accessKey
            prefs[PrefsKeys.AWS_SECRET_KEY] = config.secretKey
            prefs[PrefsKeys.AWS_REGION] = config.region
            prefs[PrefsKeys.BEDROCK_MODEL_ID] = config.bedrockModelId
            prefs[PrefsKeys.ANTHROPIC_API_KEY] = config.anthropicApiKey
            prefs[PrefsKeys.ANTHROPIC_MODEL_ID] = config.anthropicModelId
            prefs[PrefsKeys.CUSTOM_MODELS] = json.encodeToString(config.customModels)
            prefs[PrefsKeys.PROMPT_TODO] = config.promptTodo
            prefs[PrefsKeys.PROMPT_JOURNAL] = config.promptJournal
            prefs[PrefsKeys.PROMPT_IDEA] = config.promptIdea
            prefs[PrefsKeys.PROMPT_APPOINTMENT] = config.promptAppointment
        }
    }
}
