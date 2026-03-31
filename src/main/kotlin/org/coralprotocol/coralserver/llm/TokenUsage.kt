package org.coralprotocol.coralserver.llm

import kotlinx.serialization.Serializable
import java.util.concurrent.atomic.AtomicLong

@Serializable
data class TokenUsage(
    val inputTokens: Long = 0,
    val outputTokens: Long = 0
)

class AtomicTokenUsage {
    private val input = AtomicLong(0)
    private val output = AtomicLong(0)

    fun add(inputTokens: Long?, outputTokens: Long?) {
        if (inputTokens != null) input.addAndGet(inputTokens)
        if (outputTokens != null) output.addAndGet(outputTokens)
    }

    fun snapshot(): TokenUsage = TokenUsage(input.get(), output.get())
}
