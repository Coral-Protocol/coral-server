package org.coralprotocol.coralserver.agent.registry

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.runtime.LocalAgentRuntimes

@Serializable
data class UnresolvedInlineRegistryAgent(
    @SerialName("agent")
    val agentInfo: RegistryAgentInfo,

    @Description("The runtimes that this agent supports")
    val runtimes: LocalAgentRuntimes,

    @Description("The options that this agent supports, for example the API keys required for the agent to function")
    val options: Map<String, AgentOption>,
) : UnresolvedRegistryAgent() {
    override fun resolve(context: AgentResolutionContext): List<RegistryAgent> {
        return listOf(RegistryAgent(
            info = agentInfo,
            runtimes = runtimes,
            options = options,
            unresolvedExportSettings = unresolvedExportSettings,
            path = context.path
        ))
    }
}