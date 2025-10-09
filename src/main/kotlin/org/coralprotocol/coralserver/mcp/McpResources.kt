package org.coralprotocol.coralserver.mcp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class McpResources {
    @SerialName("coral://messages")
    MESSAGE_RESOURCE_URI,

    @SerialName("coral://agent/instruction")
    INSTRUCTION_RESOURCE_URI,

    @SerialName("coral://agents")
    AGENT_RESOURCE_URI;

    override fun toString(): String {
        return McpResources.serializer().descriptor.getElementName(ordinal)
    }
}
