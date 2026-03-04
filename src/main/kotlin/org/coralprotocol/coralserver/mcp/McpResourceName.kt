package org.coralprotocol.coralserver.mcp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class McpResourceName {
    @SerialName("coral://state")
    STATE_RESOURCE_URI,

    @SerialName("coral://instruction")
    INSTRUCTION_RESOURCE_URI;

    override fun toString(): String {
        return McpResourceName.serializer().descriptor.getElementName(ordinal)
    }
}
