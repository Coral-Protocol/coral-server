package org.coralprotocol.coralserver.utils

import ai.koog.agents.core.agent.AIAgentLoopContext
import ai.koog.agents.core.agent.ActAIAgent
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.Message
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi

// A private class to bundle the message and its acknowledgement
data class StepResult(
    /**
     * The latest [Prompt] read after the step completed. This contains all messages up to date.
     */
    val latestPrompt: Prompt,
    /**
     * All messages generated since the last step, including tool calls and tool results.
     *
     * These are gauged by their timestamp.
     *
     * If an earlier message is updated with a new timestamp, it will be included and the history may be non-linear.
     */
    val messagesSinceLastStep: List<Message>,
    /**
     * The new assistant message generated in this step.
     * There should always be exactly one new assistant message per step.
     */
    val newAssistantMessage: Message.Assistant
) {
    fun content(): String = newAssistantMessage.content
}

internal data class StepRequest(
    val message: UserMessage,
    val resultDeferred: CompletableDeferred<StepResult>
)

@JvmInline
@OptIn(ExperimentalUuidApi::class)
value class KoogAgentWithExternallySteppingLoop internal constructor(val koogAgent: ActAIAgent<Nothing?, Unit>)

object AGENT_SET

class ExternalSteppingKoogBuilder<S> private constructor(
    private val beforeLoopStep: (suspend AIAgentLoopContext.() -> Unit)? = null,
    private val loopStep: (suspend AIAgentLoopContext.(newInputMessage: UserMessage) -> Unit),
    private val maxLoopSteps: Int = 10,
) {

    private val loop: (suspend AIAgentLoopContext.(Nothing?) -> Unit) = test@{
        repeat(maxLoopSteps) {
            beforeLoopStep?.invoke(this)
            val request = channel.receive()
            val beforeTimestamp = Clock.System.now()
            try {
                loopStep.invoke(this, request.message)
            } finally {
                val afterPrompt = llm.readSession { prompt }
                val newMessages = afterPrompt.messages.filter { it.metaInfo.timestamp > beforeTimestamp }
                request.resultDeferred.complete(
                    StepResult(
                        afterPrompt,
                        newMessages,
                        newMessages.filterIsInstance<Message.Assistant>().last()
                    )
                )
                println("new messages: $newMessages")
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private var koogAgent: ActAIAgent<Nothing?, Unit>? = null
    private val channel = Channel<StepRequest>(Channel.RENDEZVOUS)

    @OptIn(ExperimentalUuidApi::class)
    fun withKoogAgent(loopProvider: (suspend AIAgentLoopContext.(Nothing?) -> Unit) -> ActAIAgent<Nothing?, Unit>): ExternalSteppingKoogBuilder<AGENT_SET> {
        koogAgent = loopProvider(loop)
        @Suppress("UNCHECKED_CAST") return this as ExternalSteppingKoogBuilder<AGENT_SET>
    }

    companion object {
        operator fun invoke(
            beforeLoopStep: (suspend AIAgentLoopContext.() -> Unit)? = null,
            loopStep: (suspend AIAgentLoopContext.(newInputMessage: UserMessage) -> Unit),
            maxLoopSteps: Int = 10
        ): ExternalSteppingKoogBuilder<Unit> =
            ExternalSteppingKoogBuilder(beforeLoopStep, loopStep, maxLoopSteps)

        @OptIn(ExperimentalUuidApi::class)
        fun ExternalSteppingKoogBuilder<AGENT_SET>.build(): ExternalSteppingKoog {
            val koogAgent = this.koogAgent ?: throw IllegalStateException("Koog agent not set")
            return ExternalSteppingKoog(KoogAgentWithExternallySteppingLoop(koogAgent), channel)
        }
    }
}


@OptIn(ExperimentalUuidApi::class)
class ExternalSteppingKoog internal constructor(
    val koogAgent: KoogAgentWithExternallySteppingLoop,
    internal val channel: Channel<StepRequest>,
) {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var hasRun = false
    suspend fun step(newUserMessage: UserMessage): StepResult {
        if (!hasRun) {
            hasRun = true
            scope.launch {
                koogAgent.koogAgent.run(null)
            }
        }
        val resultDeferred = CompletableDeferred<StepResult>()
        channel.send(StepRequest(newUserMessage, resultDeferred))
        return resultDeferred.await()
    }
}


@JvmInline
value class UserMessage(val content: String)
