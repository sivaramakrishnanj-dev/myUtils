package dev.sivarj.assistant.ai

sealed interface EnrichResult {
    data class Success(val text: String) : EnrichResult
    data class Failure(val error: String) : EnrichResult
}

enum class ContentType { TODO, JOURNAL, IDEA, APPOINTMENT }

/**
 * A pluggable LLM backend. Implementations take the full app config at
 * construction and perform one system-prompt + user-text completion.
 */
interface LlmProvider {
    /** Human-readable name for error messages. */
    val name: String

    /** True when this provider has the credentials it needs. */
    val isConfigured: Boolean

    suspend fun complete(systemPrompt: String, userText: String): EnrichResult
}
