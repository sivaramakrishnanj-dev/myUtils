package dev.sivarj.braingame.ai

import kotlinx.serialization.json.JsonObject

/** Outcome of one model call. */
sealed interface LlmResult {
    data class Success(val text: String) : LlmResult
    data class Failure(val error: String) : LlmResult
}

/**
 * How hard the model should think. Maps to the Messages API `output_config.effort`.
 *
 * Generation runs at [MEDIUM]: a puzzle is checked by [dev.sivarj.braingame.domain.PuzzleValidator]
 * anyway, and the player is waiting. Explanation runs at [HIGH] because that is
 * the actual teaching moment and quality matters more than a few seconds.
 */
enum class Effort(val wireValue: String) {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
}

/** One completion request. */
data class LlmRequest(
    /**
     * Stable, repeated instructions. Sent with `cache_control` so back-to-back
     * puzzles in one session read it from cache at ~10% of input price — keep it
     * byte-identical across calls or the cache silently misses.
     */
    val systemPrompt: String,
    /** The varying part of the request. Never put it in [systemPrompt]. */
    val userText: String,
    /**
     * JSON Schema for `output_config.format`. When set, the response's first
     * text block is guaranteed to be valid JSON matching this schema, which is
     * what lets puzzle parsing skip defensive text extraction.
     */
    val jsonSchema: JsonObject? = null,
    val effort: Effort = Effort.MEDIUM,
    /**
     * Ceiling on thinking *plus* response text. Opus 5 thinks by default, so a
     * value sized only for the answer truncates mid-generation.
     */
    val maxTokens: Int = 8192,
)

/** A pluggable LLM backend, so the game logic never depends on one vendor's wire format. */
interface LlmProvider {
    val name: String
    val isConfigured: Boolean
    suspend fun complete(request: LlmRequest): LlmResult
}
