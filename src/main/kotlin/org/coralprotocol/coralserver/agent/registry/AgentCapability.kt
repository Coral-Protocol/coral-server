package org.coralprotocol.coralserver.agent.registry

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AgentCapability {
    @Description("The ability to refresh MCP resources before each AI model completion.  Full Coral server functionality requires this capability")
    @SerialName("resources")
    RESOURCES,

    @Description("The ability to refresh MCP tooling before each AI model completion")
    @SerialName("tool_refreshing")
    TOOL_REFRESHING,
}