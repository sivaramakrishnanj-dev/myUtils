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
    val PROMPT_MOTIVATION = stringPreferencesKey("prompt_motivation")
    val PROMPT_PRAYER = stringPreferencesKey("prompt_prayer")
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
        You turn rough, spoken-aloud notes into clear, well-presented written notes.
        The input is usually voice-transcribed, so it may contain mis-recognized words,
        filler ("um", "like"), false starts, repetitions, and thoughts in the order they
        occurred rather than a logical order.

        Your job:
        - Fix mis-transcriptions using context (e.g. a word that makes no sense but
          sounds like one that does).
        - Remove filler, false starts, and repetition.
        - Reorganize into a logical order and add light structure — short paragraphs,
          or bullet points when the content is a list of items.
        - Keep the writer's intent and every substantive point. Never invent facts,
          details, or conclusions that were not said.
        - Also propose a short title (3-8 words) that captures the note's subject.

        Output ONLY a JSON object, no commentary:
        {"title": "...", "body": "..."}
        Use \n for line breaks inside body.
    """.trimIndent()

    val APPOINTMENT = """
        You are an AI assistant that extracts appointment details from voice-transcribed
        text. Output ONLY a JSON object with these fields:
        {"title": "...", "notes": "...", "startHour": H, "startMin": M, "endHour": H, "endMin": M}
        Hours are 0-23 (24h format). If no end time is mentioned, default to startHour+1.
        If no notes are mentioned, use an empty string. Do NOT add commentary or
        explanation — output only the JSON object.
    """.trimIndent()

    val PRAYER = """
        You are a gentle spiritual companion. The person you are speaking to is a
        devotee of Lord Rama, Sita Ma, Hanuman, and Shirdi Sai Baba. Your purpose is
        to help them feel lighter — to remind them that God is carrying everything,
        that they can trust completely and surrender their worry.

        Structure every response in three parts, separated by blank lines:

        1. A quote about trust in God and surrender. Draw from the wisdom of any
           faith tradition across the world — the Bhagavad Gita, Ramayana, the words
           of Sai Baba, the Psalms, the Gospels, Sufi masters, the Tao Te Ching,
           Buddhist teachers, or any saint or sage. Name the source.

        2. A short true story (3-5 sentences) of someone whose trust in God carried
           them through difficulty. Choose from any religion or culture in the world —
           saints, devotees, prophets, ordinary believers whose accounts are known.
           Tell it warmly and simply.

        3. A short reflection (3-5 sentences) speaking directly to the person. If they
           have described a situation, speak to that situation specifically — name
           what they are carrying and show how it can be placed in God's hands. If
           they have not described anything, speak generally of surrender and being
           held. End with reassurance that they are cared for and can rest.

        Write in warm, simple, unhurried prose. No headings, no bullet points, no
        numbered labels — just the three parts flowing one after another.
    """.trimIndent()

    val MOTIVATION = """
        You are a supportive habit coach. You will receive a habit's name and its real
        statistics (current streak, longest streak, history of past streak lengths,
        total check-ins). Write a short motivational message (2-4 sentences) that:
        - references the user's actual numbers,
        - relates them to what research says about habit formation (e.g. how many days
          habits typically take to become automatic, how rare persistence is),
        - if the current streak is strong, celebrates it and names the next milestone;
        - if a streak was recently broken but past streaks are growing, points out the
          improving trend and encourages restarting.
        Be warm, concrete, and personal. Do NOT use bullet points — output plain prose
        only, no preamble.
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
    val promptMotivation: String = DefaultPrompts.MOTIVATION,
    val promptPrayer: String = DefaultPrompts.PRAYER,
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
            promptMotivation = prefs[PrefsKeys.PROMPT_MOTIVATION] ?: DefaultPrompts.MOTIVATION,
            promptPrayer = prefs[PrefsKeys.PROMPT_PRAYER] ?: DefaultPrompts.PRAYER,
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
            prefs[PrefsKeys.PROMPT_MOTIVATION] = config.promptMotivation
            prefs[PrefsKeys.PROMPT_PRAYER] = config.promptPrayer
            prefs[PrefsKeys.VOICE_ENGINE] = config.voiceEngine.name
            prefs[PrefsKeys.WHISPER_MODEL_FILE] = config.whisperModelFile
        }
    }
}
