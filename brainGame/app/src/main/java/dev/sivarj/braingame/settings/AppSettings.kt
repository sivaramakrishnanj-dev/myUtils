package dev.sivarj.braingame.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.sivarj.braingame.ai.DefaultPrompts
import dev.sivarj.braingame.domain.Themes
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
    val PROMPT_GENERATION = stringPreferencesKey("prompt_generation")
    val PROMPT_EXPLANATION = stringPreferencesKey("prompt_explanation")
    val ENABLED_THEMES = stringPreferencesKey("enabled_themes")
}

@Serializable
data class LlmModel(val id: String, val name: String)

/**
 * Selectable models, best-first. Opus 5 is the default: this app makes a handful
 * of calls a day, so quality matters more than the per-call cost, and the
 * override exists for when it doesn't.
 */
val BUILT_IN_MODELS = listOf(
    LlmModel("claude-opus-5", "Claude Opus 5"),
    LlmModel("claude-fable-5", "Claude Fable 5"),
    LlmModel("claude-sonnet-5", "Claude Sonnet 5"),
    LlmModel("claude-opus-4-8", "Claude Opus 4.8"),
    LlmModel("claude-haiku-4-5", "Claude Haiku 4.5"),
)

data class AppConfig(
    val apiKey: String = "",
    val modelId: String = BUILT_IN_MODELS.first().id,
    val customModels: List<LlmModel> = emptyList(),
    val promptGeneration: String = DefaultPrompts.GENERATION,
    val promptExplanation: String = DefaultPrompts.EXPLANATION,
    val enabledThemes: List<String> = Themes.ALL,
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank()

    val allModels: List<LlmModel>
        get() = BUILT_IN_MODELS + customModels.filter { c -> BUILT_IN_MODELS.none { it.id == c.id } }

    /** Falls back to every theme rather than blocking generation on an empty selection. */
    val effectiveThemes: List<String>
        get() = enabledThemes.ifEmpty { Themes.ALL }
}

class AppSettings(private val context: Context) {

    val config: Flow<AppConfig> = context.dataStore.data.map { prefs ->
        AppConfig(
            apiKey = prefs[PrefsKeys.ANTHROPIC_API_KEY] ?: "",
            modelId = prefs[PrefsKeys.MODEL_ID] ?: BUILT_IN_MODELS.first().id,
            customModels = prefs[PrefsKeys.CUSTOM_MODELS]?.let {
                runCatching { json.decodeFromString<List<LlmModel>>(it) }.getOrDefault(emptyList())
            } ?: emptyList(),
            promptGeneration = prefs[PrefsKeys.PROMPT_GENERATION] ?: DefaultPrompts.GENERATION,
            promptExplanation = prefs[PrefsKeys.PROMPT_EXPLANATION] ?: DefaultPrompts.EXPLANATION,
            enabledThemes = prefs[PrefsKeys.ENABLED_THEMES]?.let {
                runCatching { json.decodeFromString<List<String>>(it) }.getOrNull()
            } ?: Themes.ALL,
        )
    }

    suspend fun save(config: AppConfig) {
        context.dataStore.edit { prefs ->
            prefs[PrefsKeys.ANTHROPIC_API_KEY] = config.apiKey
            prefs[PrefsKeys.MODEL_ID] = config.modelId
            prefs[PrefsKeys.CUSTOM_MODELS] = json.encodeToString(config.customModels)
            prefs[PrefsKeys.PROMPT_GENERATION] = config.promptGeneration
            prefs[PrefsKeys.PROMPT_EXPLANATION] = config.promptExplanation
            prefs[PrefsKeys.ENABLED_THEMES] = json.encodeToString(config.enabledThemes)
        }
    }
}
