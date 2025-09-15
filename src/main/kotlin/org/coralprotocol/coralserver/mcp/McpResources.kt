package org.coralprotocol.coralserver.mcp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class McpResources {
    @SerialName("Message.resource")
    MESSAGE_RESOURCE_URI,

    @SerialName("Instruction.resource")
    INSTRUCTION_RESOURCE_URI,

    @SerialName("Agent.resource")
    AGENT_RESOURCE_URI;

    override fun toString(): String {
        return McpResources.serializer().descriptor.getElementName(ordinal)
    }
}