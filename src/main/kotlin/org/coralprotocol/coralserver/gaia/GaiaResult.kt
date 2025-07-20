package org.coralprotocol.coralserver.gaia

import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.models.ResolvedThread

@Serializable
data class GaiaResult(
    val question: GaiaQuestion,
    val answerAttempt: GaiaAnswerAttempt,
    val threads: List<ResolvedThread>? = null
) {
    val isCorrect: Boolean = checkCorrectness()

    private fun checkCorrectness(): Boolean = when {
        question.finalAnswer == "?" -> { // Test set has unknown answers
            println("Total confidence: ${answerAttempt.certaintyPercentage}%")
            !answerAttempt.answer.contains("give up", ignoreCase = true)
        }

        else -> question.finalAnswer.lowercase() == answerAttempt.answer.lowercase()
    }
}