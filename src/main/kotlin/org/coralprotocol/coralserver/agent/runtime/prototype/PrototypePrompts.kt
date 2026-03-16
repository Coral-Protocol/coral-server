package org.coralprotocol.coralserver.agent.runtime.prototype

import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.mcp.McpResourceName
import org.coralprotocol.coralserver.session.SessionAgentExecutionContext
import org.coralprotocol.coralserver.util.buildIndentedString

val DEFAULT_SYSTEM_PROMPT = """
You are an AI agent designed to assist users by leveraging available tools and resources. Follow the user's instructions carefully and utilize the tools at your disposal to provide accurate and helpful responses.
-- Start of messages and status --
<resource>${McpResourceName.INSTRUCTION_RESOURCE_URI}</resource>
<resource>${McpResourceName.STATE_RESOURCE_URI}</resource>
-- End of messages and status --
"""

const val DEFAULT_LOOP_INITIAL_BASE_PROMPT = """
[automated message] You are an autonomous agent designed to assist users by collaborating with other agents. 
Your goal is to fulfill user requests to the best of your ability using the tools and resources available to you.  
If no instructions are provided, consider waiting for mentions until another agent provides further direction.
""""

const val DEFAULT_LOOP_FOLLOWUP_PROMPT =
    "[automated message] Continue fulfilling your responsibilities collaboratively to the best of your ability."

@Serializable
data class PrototypeSystemPrompt(
    val base: PrototypeString = PrototypeString.Inline(DEFAULT_SYSTEM_PROMPT),
    val extra: PrototypeString? = null,
) {
    fun resolve(executionContext: SessionAgentExecutionContext) =
        buildString {
            appendLine(base.resolve(executionContext))
            appendLine()

            val extraString = extra?.resolve(executionContext) ?: ""
            if (extraString.isNotBlank()) {
                appendLine(extraString)
            }
        }
}

@Serializable
data class PrototypeLoopInitialPrompt(
    val base: PrototypeString = PrototypeString.Inline(DEFAULT_LOOP_INITIAL_BASE_PROMPT),
    val extra: PrototypeString? = null,
) {
    fun resolve(executionContext: SessionAgentExecutionContext) =
        buildIndentedString {
            appendLine(base.resolve(executionContext))
            appendLine()

            val extraString = extra?.resolve(executionContext) ?: ""
            if (extraString.isNotBlank()) {
                appendLine("Here are some additional instructions to guide your behavior:")
                withIndentedXml("specific instructions") {
                    appendLine(extraString)
                }
            }

            appendLine("Remember that 'I' am not the user, who is not directly reachable. Use tools to interact with other agents as necessary to fulfil the users needs. You will receive further automated messages this way.")
        }
}

@Serializable
data class PrototypeLoopPrompt(
    val initial: PrototypeLoopInitialPrompt = PrototypeLoopInitialPrompt(),
    val followup: PrototypeString = PrototypeString.Inline(DEFAULT_LOOP_FOLLOWUP_PROMPT),
)

@Serializable
data class PrototypePrompts(
    val system: PrototypeSystemPrompt = PrototypeSystemPrompt(),
    val loop: PrototypeLoopPrompt = PrototypeLoopPrompt(),
)