package org.coralprotocol.coralserver.agent.runtime.prototype

import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.mcp.McpResourceName
import org.coralprotocol.coralserver.mcp.McpToolName
import org.coralprotocol.coralserver.session.SessionAgentExecutionContext
import org.coralprotocol.coralserver.util.buildIndentedString

val DEFAULT_SYSTEM_PROMPT = """
You are an AI agent. Follow the instructions and serve their author's intents.
-- Start of messages and status --
<resource>${McpResourceName.INSTRUCTION_RESOURCE_URI}</resource>
<resource>${McpResourceName.STATE_RESOURCE_URI}</resource>
-- End of messages and status --
""".trimIndent()

val DEFAULT_LOOP_INITIAL_BASE_PROMPT = """
[automated message] You are an autonomous agent designed to assist users by collaborating with other agents. 
Your goal is to fulfill user requests to the best of your ability using the tools and resources available to you.  
""".trimIndent()

val DEFAULT_LOOP_FOLLOWUP_PROMPT =
    "[automated message] Continue fulfilling your responsibilities collaboratively to the best of your ability.".trimIndent()

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
                appendLine("Here are your more specific instructions that you should immediately follow.:")
                withIndentedXml("specific instructions") {
                    appendLine(extraString)
                }
            } else {
                // smaller models see considerable benefit from naming the waiting tools rather than just stating "the waiting tools"
                appendLine("No specific instructions were provided, use one of the waiting tools (${McpToolName.WAIT_FOR_MESSAGE}, ${McpToolName.WAIT_FOR_MENTION} or ${McpToolName.WAIT_FOR_AGENT}) to wait until another agent contacts you, unless your state shows that another agent has already asked something of you.")
            }

            appendLine("(Remember that 'I' am not the user, who is not directly reachable. Use tools to interact with other agents as necessary to fulfil the users needs. You will receive further automated messages this way.)")
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
