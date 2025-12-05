package org.coralprotocol.coralserver.mcp

import io.github.smiley4.schemakenerator.core.CoreSteps.initial
import io.github.smiley4.schemakenerator.jsonschema.JsonSchemaSteps.compileInlining
import io.github.smiley4.schemakenerator.jsonschema.JsonSchemaSteps.generateJsonSchema
import io.github.smiley4.schemakenerator.jsonschema.JsonSchemaSteps.merge
import io.github.smiley4.schemakenerator.serialization.SerializationSteps.analyzeTypeUsingKotlinxSerialization
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer
import org.coralprotocol.coralserver.logging.LoggerWithFlow
import org.coralprotocol.coralserver.mcp.tools.AddParticipantInput
import org.coralprotocol.coralserver.mcp.tools.CloseThreadInput
import org.coralprotocol.coralserver.mcp.tools.CreateThreadInput
import org.coralprotocol.coralserver.mcp.tools.CreateThreadOutput
import org.coralprotocol.coralserver.mcp.tools.RemoveParticipantInput
import org.coralprotocol.coralserver.mcp.tools.SendMessageInput
import org.coralprotocol.coralserver.mcp.tools.SendMessageOutput
import org.coralprotocol.coralserver.mcp.tools.WaitForAgentMessageInput
import org.coralprotocol.coralserver.mcp.tools.WaitForMentioningMessageInput
import org.coralprotocol.coralserver.mcp.tools.WaitForMessageOutput
import org.coralprotocol.coralserver.mcp.tools.WaitForSingleMessageInput
import org.coralprotocol.coralserver.mcp.tools.addParticipantExecutor
import org.coralprotocol.coralserver.mcp.tools.closeThreadExecutor
import org.coralprotocol.coralserver.mcp.tools.createThreadExecutor
import org.coralprotocol.coralserver.mcp.tools.optional.CloseSessionInput
import org.coralprotocol.coralserver.mcp.tools.optional.closeSessionExecutor
import org.coralprotocol.coralserver.mcp.tools.removeParticipantExecutor
import org.coralprotocol.coralserver.mcp.tools.sendMessageExecutor
import org.coralprotocol.coralserver.mcp.tools.waitForAgentMessageExecutor
import org.coralprotocol.coralserver.mcp.tools.waitForMentioningMessageExecutor
import org.coralprotocol.coralserver.mcp.tools.waitForSingleMessageExecutor
import org.coralprotocol.coralserver.session.SessionAgent
import org.coralprotocol.coralserver.util.convert

/**
 * This class should contain every tool that Coral has to offer.  The presence of tools in this manager does not guarantee
 * that they will be available to agents.  Agents can only use tools that are registered in their [SessionAgent.tools]
 * list, which can be appended to with [SessionAgent.addTool].
 *
 * The primary purpose of this class is to control when tools are built.  Building tools can throw exceptions, which
 * should really be considered compilation errors (unfortunately, though, there is no ability to do this in Kotlin/JVM)
 *
 * When this class is constructed, all tools are built.  Exceptions thrown by the construction of this class have a
 * little bit more control this way.
 */
class McpToolManager(val logger: LoggerWithFlow = LoggerWithFlow("McpToolManager")) {
    val createThreadTool = buildTool<CreateThreadInput, CreateThreadOutput>(
        name = McpToolName.CREATE_THREAD,
        description = "Creates a new Coral thread",
        requiredSnippets = setOf(McpInstructionSnippet.MESSAGING),
        executor = ::createThreadExecutor
    )

    val closeThreadTool = buildTool<CloseThreadInput, GenericSuccessOutput>(
        name = McpToolName.CLOSE_THREAD,
        description = "Closes a Coral thread",
        requiredSnippets = setOf(McpInstructionSnippet.MESSAGING),
        executor = ::closeThreadExecutor
    )

    val addParticipantTool = buildTool<AddParticipantInput, GenericSuccessOutput>(
        name = McpToolName.ADD_PARTICIPANT,
        description = "Adds a new participant to a Coral thread",
        requiredSnippets = setOf(McpInstructionSnippet.MESSAGING),
        executor = ::addParticipantExecutor
    )

    val removeParticipantTool = buildTool<RemoveParticipantInput, GenericSuccessOutput>(
        name = McpToolName.REMOVE_PARTICIPANT,
        description = "Removes a participant from a Coral thread",
        requiredSnippets = setOf(McpInstructionSnippet.MESSAGING),
        executor = ::removeParticipantExecutor
    )

    val sendMessageTool = buildTool<SendMessageInput, SendMessageOutput>(
        name = McpToolName.SEND_MESSAGE,
        description = "Posts a message into a Coral thread",
        requiredSnippets = setOf(McpInstructionSnippet.MESSAGING, McpInstructionSnippet.MENTIONS),
        executor = ::sendMessageExecutor
    )

    val waitForMessageTool = buildTool<WaitForSingleMessageInput, WaitForMessageOutput>(
        name = McpToolName.WAIT_FOR_MESSAGE,
        description = "Waits for and returns a single message from another agent in any Coral thread",
        requiredSnippets = setOf(McpInstructionSnippet.MESSAGING, McpInstructionSnippet.WAITING),
        executor = ::waitForSingleMessageExecutor
    )

    val waitForMentionTool = buildTool<WaitForMentioningMessageInput, WaitForMessageOutput>(
        name = McpToolName.WAIT_FOR_MENTION,
        description = "Waits for and returns a single message that mentions you from any agent in any Coral thread",
        requiredSnippets = setOf(McpInstructionSnippet.MESSAGING, McpInstructionSnippet.MENTIONS, McpInstructionSnippet.WAITING),
        executor = ::waitForMentioningMessageExecutor
    )

    val waitForAgentMessageTool = buildTool<WaitForAgentMessageInput, WaitForMessageOutput>(
        name = McpToolName.WAIT_FOR_AGENT,
        description = "Waits for and returns a single message from a specific agent in any Coral thread",
        requiredSnippets = setOf(McpInstructionSnippet.MESSAGING, McpInstructionSnippet.WAITING),
        executor = ::waitForAgentMessageExecutor
    )

    val closeSessionTool = buildTool<CloseSessionInput, GenericSuccessOutput>(
        name = McpToolName.CLOSE_SESSION,
        description = "Closes the session",
        executor = ::closeSessionExecutor
    )

    /**
     * Tool builder that primarily is used to generate input schemas for tools.  This function should be used for every
     * tool unless you need to generate a custom input schema.
     *
     * @param name The name of the tool, this must be a [McpToolName] so that accurate tool names are included in the
     * OpenAPI spec
     * @param description A description of the tool, this can be brief.  Advanced instructions for the tool should be
     * represented as snippets in the [requiredSnippets] parameter
     * @param requiredSnippets A set of [McpInstructionSnippet]s that are required for agent to understand the usage of
     * this tool.
     */
    private inline fun <reified In, reified Out> buildTool(
        name: McpToolName,
        description: String,
        requiredSnippets: Set<McpInstructionSnippet> = setOf(),
        noinline executor: suspend (agent: SessionAgent, arguments: In) -> Out
    ): McpTool<In, Out> {
        val generatedJsonSchema =
            initial<In>()
                .analyzeTypeUsingKotlinxSerialization()
                .generateJsonSchema()
                .compileInlining()
                .merge()
                .convert() as? JsonObject
                ?: throw IllegalArgumentException("Generated json schema for tool input is not a JsonObject")

        val required = generatedJsonSchema.getValue("required") as? JsonArray
            ?: throw IllegalArgumentException("Generated json schema is missing the 'required' array")

        val properties = generatedJsonSchema.getValue("properties") as? JsonObject
            ?: throw IllegalArgumentException("Generated json schema is missing the 'properties' object")

        if (required.size != properties.size)
            logger.warn("Generated input schema for mcp tool $name contains optional properties, this will cause problems with OpenAI's structured output and potentially other models")

        val inputSchema = Tool.Input(
            required = required.map { it.jsonPrimitive.content },
            properties = properties
        )

        return McpTool(
            name = name,
            description = description,
            requiredSnippets = requiredSnippets,
            inputSchema = inputSchema,
            executor = executor,
            inputSerializer = serializer<In>(),
            outputSerializer = serializer<Out>()
        )
    }
}