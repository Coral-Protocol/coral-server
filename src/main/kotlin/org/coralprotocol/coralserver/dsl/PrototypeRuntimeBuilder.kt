package org.coralprotocol.coralserver.dsl

import ai.koog.agents.core.agent.session.AIAgentLLMReadSession
import org.coralprotocol.coralserver.agent.runtime.PrototypeRuntime
import org.coralprotocol.coralserver.agent.runtime.prototype.*

@CoralDsl
class PrototypeRuntimeBuilder(
    val proxyName: String,
) {
    var volatile: Boolean = false
    var iterationCount: Int = 20
    var iterationDelay: Int = 0
    var client: PrototypeClient? = null
    var postRequestToLLMCallback: (context: AIAgentLLMReadSession) -> Unit = {}

    private var prompts = PrototypePrompts()
    private val toolServers = mutableListOf<PrototypeToolServer>()

    fun prompts(block: PrototypePromptsBuilder.() -> Unit) {
        prompts = PrototypePromptsBuilder().apply(block).build()
    }

    fun toolServer(toolServer: PrototypeToolServer) {
        toolServers.add(toolServer)
    }

    fun build() = PrototypeRuntime(
        volatile = volatile,
        proxyName = PrototypeString.Inline(proxyName),
        client = client,
        iterationCount = iterationCount,
        iterationDelay = iterationDelay,
        prompts = prompts,
        toolServers = toolServers.toList(),
        postRequestToLLMCallback = postRequestToLLMCallback
    )
}

@CoralDsl
class PrototypePromptsBuilder {
    private var system = PrototypeSystemPrompt()
    private var loop = PrototypeLoopPrompt()

    fun system(block: PrototypeSystemPromptBuilder.() -> Unit) {
        system = PrototypeSystemPromptBuilder().apply(block).build()
    }

    fun loop(block: PrototypeLoopPromptBuilder.() -> Unit) {
        loop = PrototypeLoopPromptBuilder().apply(block).build()
    }

    fun build() = PrototypePrompts(
        system = system,
        loop = loop
    )
}

@CoralDsl
class PrototypeSystemPromptBuilder {
    var base: String = DEFAULT_SYSTEM_PROMPT
    var extra: String? = null

    fun build() = PrototypeSystemPrompt(
        base = PrototypeString.Inline(base),
        extra = extra?.let { PrototypeString.Inline(it) }
    )
}

@CoralDsl
class PrototypeLoopPromptBuilder {
    private var initial = PrototypeLoopInitialPrompt()
    private var followup: PrototypeString = PrototypeString.Inline(DEFAULT_LOOP_FOLLOWUP_PROMPT)

    fun initial(block: PrototypeLoopInitialPromptBuilder.() -> Unit) {
        initial = PrototypeLoopInitialPromptBuilder().apply(block).build()
    }

    fun followup(value: String) {
        followup = PrototypeString.Inline(value)
    }

    fun build() = PrototypeLoopPrompt(
        initial = initial,
        followup = followup
    )
}

@CoralDsl
class PrototypeLoopInitialPromptBuilder {
    var base: String = DEFAULT_LOOP_INITIAL_BASE_PROMPT
    var extra: String? = null

    fun build() = PrototypeLoopInitialPrompt(
        base = PrototypeString.Inline(base),
        extra = extra?.let { PrototypeString.Inline(it) }
    )
}
