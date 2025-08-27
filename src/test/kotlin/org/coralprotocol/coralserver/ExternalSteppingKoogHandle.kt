package org.coralprotocol.coralserver

import ai.koog.agents.core.agent.AIAgentLoopContext
import ai.koog.agents.core.agent.ActAIAgent
import ai.koog.agents.core.agent.requestLLM
import ai.koog.prompt.executor.model.PromptExecutor
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.coralprotocol.coralserver.e2e.ExternallySteppedKoogAgent
import org.coralprotocol.coralserver.e2e.updateSystemResources
import org.coralprotocol.coralserver.models.Message
import kotlin.uuid.ExperimentalUuidApi

//class ExternalSteppingKoogCoralWrapper()
@OptIn(ExperimentalUuidApi::class)
class ExternalSteppingKoogWrapper(
    val koogAgent: ExternallySteppedKoogAgent?,
    val beforeStep: suspend AIAgentLoopContext.() -> Unit
) {

    private val channel = Channel<UserMessage>(Channel.RENDEZVOUS)

    fun getLoop(): suspend AIAgentLoopContext.() -> Unit = {
        beforeStep()
        externallySteppableLoop(channel)
    }

    suspend fun step(messages: UserMessage) {
        channel.send(messages)
    }
}

@JvmInline
value class UserMessage(val content: String)

suspend fun AIAgentLoopContext.externallySteppableLoop(channel: Channel<UserMessage>) {
    val newInputMessage = channel.receive()
    val response = requestLLM(newInputMessage.content)
    println("Initial response: $response")
}