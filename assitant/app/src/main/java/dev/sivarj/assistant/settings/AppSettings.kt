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
    val ANTHROPIC_API_KEY = stringPreferencesKey("anthropic_api_key")
    val MODEL_ID = stringPreferencesKey("anthropic_model_id")
    val CUSTOM_MODELS = stringPreferencesKey("custom_models")
    val PROMPT_TODO = stringPreferencesKey("prompt_todo")
    val PROMPT_JOURNAL = stringPreferencesKey("prompt_journal")
    val PROMPT_IDEA = stringPreferencesKey("prompt_idea")
    val PROMPT_APPOINTMENT = stringPreferencesKey("prompt_appointment")
    val VOICE_ENGINE = stringPreferencesKey("voice_engine")
    val WHISPER_MODEL_FILE = stringPreferencesKey("whisper_model_file")
}

enum class VoiceEngine(val displayName: String) {
    SYSTEM("Android (live)"),
    WHISPER("Whisper (on-device, record then transcribe)"),
}

@Serializable
data class LlmModel(val id: String, val name: String)

/** Models from the Anthropic API (5 → 4.6 range). */
val BUILT_IN_MODELS = listOf(
    LlmModel("claude-sonnet-5", "Claude Sonnet 5"),
    LlmModel("claude-fable-5", "Claude Fable 5"),
    LlmModel("claude-opus-4-8", "Claude Opus 4.8"),
    LlmModel("claude-opus-4-7", "Claude Opus 4.7"),
    LlmModel("claude-sonnet-4-6", "Claude Sonnet 4.6"),
    LlmModel("claude-opus-4-6", "Claude Opus 4.6"),
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

data class AppConfig(
    val apiKey: String = "",
    val modelId: String = BUILT_IN_MODELS.first().id,
    val customModels: List<LlmModel> = emptyList(),
    val promptTodo: String = DefaultPrompts.TODO,
    val promptJournal: String = DefaultPrompts.JOURNAL,
    val promptIdea: String = DefaultPrompts.IDEA,
    val promptAppointment: String = DefaultPrompts.APPOINTMENT,
    val voiceEngine: VoiceEngine = VoiceEngine.SYSTEM,
    val whisperModelFile: String = "",
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank()

    val allModels: List<LlmModel>
        get() = BUILT_IN_MODELS + customModels.filter { c -> BUILT_IN_MODELS.none { it.id == c.id } }
}

class AppSettings(private val context: Context) {

    val config: Flow<AppConfig> = context.dataStore.data.map { prefs ->
        AppConfig(
            apiKey = prefs[PrefsKeys.ANTHROPIC_API_KEY] ?: "",
            modelId = prefs[PrefsKeys.MODEL_ID] ?: BUILT_IN_MODELS.first().id,
            customModels = prefs[PrefsKeys.CUSTOM_MODELS]?.let {
                runCatching { json.decodeFromString<List<LlmModel>>(it) }.getOrDefault(emptyList())
            } ?: emptyList(),
            promptTodo = prefs[PrefsKeys.PROMPT_TODO] ?: DefaultPrompts.TODO,
            promptJournal = prefs[PrefsKeys.PROMPT_JOURNAL] ?: DefaultPrompts.JOURNAL,
            promptIdea = prefs[PrefsKeys.PROMPT_IDEA] ?: DefaultPrompts.IDEA,
            promptAppointment = prefs[PrefsKeys.PROMPT_APPOINTMENT] ?: DefaultPrompts.APPOINTMENT,
            voiceEngine = prefs[PrefsKeys.VOICE_ENGINE]?.let {
                runCatching { VoiceEngine.valueOf(it) }.getOrNull()
            } ?: VoiceEngine.SYSTEM,
            whisperModelFile = prefs[PrefsKeys.WHISPER_MODEL_FILE] ?: "",
        )
    }

    suspend fun save(config: AppConfig) {
        context.dataStore.edit { prefs ->
            prefs[PrefsKeys.ANTHROPIC_API_KEY] = config.apiKey
            prefs[PrefsKeys.MODEL_ID] = config.modelId
            prefs[PrefsKeys.CUSTOM_MODELS] = json.encodeToString(config.customModels)
            prefs[PrefsKeys.PROMPT_TODO] = config.promptTodo
            prefs[PrefsKeys.PROMPT_JOURNAL] = config.promptJournal
            prefs[PrefsKeys.PROMPT_IDEA] = config.promptIdea
            prefs[PrefsKeys.PROMPT_APPOINTMENT] = config.promptAppointment
            prefs[PrefsKeys.VOICE_ENGINE] = config.voiceEngine.name
            prefs[PrefsKeys.WHISPER_MODEL_FILE] = config.whisperModelFile
        }
    }
}
