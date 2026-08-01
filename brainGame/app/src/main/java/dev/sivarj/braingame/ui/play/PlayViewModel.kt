package dev.sivarj.braingame.ui.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.sivarj.braingame.ai.LlmResult
import dev.sivarj.braingame.ai.PuzzleRequestResult
import dev.sivarj.braingame.data.ActivePuzzle
import dev.sivarj.braingame.data.GameRepository
import dev.sivarj.braingame.data.PuzzleOutcome
import dev.sivarj.braingame.domain.AnswerKind
import dev.sivarj.braingame.domain.Attempt
import dev.sivarj.braingame.domain.SkillRating
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Transient UI state layered over the persisted puzzle. */
data class PlayUiState(
    val generating: Boolean = false,
    val error: String? = null,
    /** Set when a puzzle has just finished, driving the result sheet. */
    val outcome: PuzzleOutcome? = null,
    val explanation: String? = null,
    val explaining: Boolean = false,
    /** Feedback for a wrong answer that left the puzzle playable. */
    val wrongAnswerNotice: String? = null,
)

class PlayViewModel(private val repository: GameRepository) : ViewModel() {

    private val _ui = MutableStateFlow(PlayUiState())
    val ui: StateFlow<PlayUiState> = _ui.asStateFlow()

    /** Survives process death because it is read from Room, not held in memory. */
    val activePuzzle: StateFlow<ActivePuzzle?> = repository.activePuzzle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val ratings: StateFlow<List<SkillRating>> = repository.ratings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Locally held so typing doesn't wait on a database write. */
    private val _draftAnswer = MutableStateFlow("")
    val draftAnswer: StateFlow<String> = _draftAnswer.asStateFlow()

    /** Working arrangement for ORDERING puzzles, as indices into `options`. */
    private val _workingOrder = MutableStateFlow<List<Int>>(emptyList())
    val workingOrder: StateFlow<List<Int>> = _workingOrder.asStateFlow()

    private var hydratedPuzzleId: String? = null

    /**
     * Restores draft state when a puzzle is loaded or resumed. Guarded by id so
     * that later Room emissions (a hint reveal, say) don't overwrite what the
     * player is currently typing.
     */
    fun hydrateFrom(active: ActivePuzzle) {
        if (hydratedPuzzleId == active.id) return
        hydratedPuzzleId = active.id
        _draftAnswer.value = active.draftAnswer
        _workingOrder.value = active.draftOrder.ifEmpty {
            // First open of an ORDERING puzzle: start from the shuffled order as presented.
            if (active.puzzle.answerKind == AnswerKind.ORDERING) active.puzzle.options.indices.toList()
            else emptyList()
        }
        _ui.value = _ui.value.copy(outcome = null, explanation = null, wrongAnswerNotice = null)
    }

    fun onAnswerChanged(value: String) {
        _draftAnswer.value = value
        _ui.value = _ui.value.copy(wrongAnswerNotice = null)
        hydratedPuzzleId?.let { id ->
            viewModelScope.launch { repository.saveDraft(id, answer = value) }
        }
    }

    /** Moves an item in an ORDERING puzzle and persists the new arrangement. */
    fun moveItem(from: Int, to: Int) {
        val current = _workingOrder.value.toMutableList()
        if (from !in current.indices || to !in current.indices) return
        current.add(to, current.removeAt(from))
        _workingOrder.value = current
        _ui.value = _ui.value.copy(wrongAnswerNotice = null)
        hydratedPuzzleId?.let { id ->
            viewModelScope.launch { repository.saveDraft(id, order = current) }
        }
    }

    fun newPuzzle() {
        if (_ui.value.generating) return
        viewModelScope.launch {
            _ui.value = PlayUiState(generating = true)
            hydratedPuzzleId = null
            _draftAnswer.value = ""
            _workingOrder.value = emptyList()

            when (val result = repository.startNewPuzzle()) {
                is PuzzleRequestResult.Failure ->
                    _ui.value = PlayUiState(generating = false, error = result.error)
                is PuzzleRequestResult.Success ->
                    _ui.value = PlayUiState(generating = false)
            }
        }
    }

    fun revealHint() {
        val id = hydratedPuzzleId ?: return
        viewModelScope.launch { repository.revealHint(id) }
    }

    /** Grades the current input. Wrong answers keep the puzzle playable. */
    fun submit() {
        val active = activePuzzle.value ?: return
        val id = active.id
        val attempt = buildAttempt(active) ?: run {
            _ui.value = _ui.value.copy(wrongAnswerNotice = "Enter a number to submit")
            return
        }

        viewModelScope.launch {
            val outcome = repository.submit(id, attempt, displayOf(active, attempt))
            if (outcome != null) {
                _ui.value = _ui.value.copy(outcome = outcome, wrongAnswerNotice = null)
            } else {
                _ui.value = _ui.value.copy(
                    wrongAnswerNotice = "Not right — try again, or take a hint.",
                )
            }
        }
    }

    fun giveUp() {
        val id = hydratedPuzzleId ?: return
        viewModelScope.launch {
            repository.giveUp(id)?.let { outcome ->
                _ui.value = _ui.value.copy(outcome = outcome)
                // The explanation is the point of failing, so fetch it without being asked.
                fetchExplanation(id)
            }
        }
    }

    /** Requests the tutor explanation for the finished puzzle. */
    fun explainCurrent() {
        val id = hydratedPuzzleId ?: return
        viewModelScope.launch { fetchExplanation(id) }
    }

    private suspend fun fetchExplanation(id: String) {
        _ui.value = _ui.value.copy(explaining = true)
        _ui.value = when (val result = repository.explain(id)) {
            is LlmResult.Success -> _ui.value.copy(explaining = false, explanation = result.text)
            is LlmResult.Failure -> _ui.value.copy(explaining = false, error = result.error)
        }
    }

    fun dismissOutcome() {
        _ui.value = PlayUiState()
        hydratedPuzzleId = null
    }

    fun clearError() {
        _ui.value = _ui.value.copy(error = null)
    }

    /** Maps the current input to the attempt shape the puzzle expects. */
    private fun buildAttempt(active: ActivePuzzle): Attempt? =
        when (active.puzzle.answerKind) {
            AnswerKind.INTEGER, AnswerKind.APPROXIMATE ->
                parseNumber(_draftAnswer.value)?.let { Attempt.Number(it) }

            AnswerKind.MULTIPLE_CHOICE ->
                _draftAnswer.value.toIntOrNull()?.let { Attempt.Choice(it) }

            AnswerKind.ORDERING ->
                _workingOrder.value.takeIf { it.isNotEmpty() }?.let { Attempt.Order(it) }
        }

    /**
     * Accepts the shapes people actually type for large numbers — thousands
     * separators, a leading currency-free "~", and scientific notation — so a
     * correct answer isn't marked wrong over formatting.
     */
    private fun parseNumber(raw: String): Double? {
        val cleaned = raw.trim().removePrefix("~").replace(",", "").replace(" ", "")
        return cleaned.toDoubleOrNull()
    }

    /** How the attempt is recorded for the explainer, in the player's own terms. */
    private fun displayOf(active: ActivePuzzle, attempt: Attempt): String = when (attempt) {
        is Attempt.Number -> _draftAnswer.value.trim()
        is Attempt.Choice ->
            active.puzzle.options.getOrNull(attempt.index)
                ?.let { "option ${attempt.index + 1}: $it" }
                ?: "option ${attempt.index + 1}"
        is Attempt.Order ->
            attempt.order.mapNotNull { active.puzzle.options.getOrNull(it) }.joinToString(" → ")
    }

    class Factory(private val repository: GameRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PlayViewModel(repository) as T
    }
}
