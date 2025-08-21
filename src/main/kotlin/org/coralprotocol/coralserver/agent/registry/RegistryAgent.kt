package org.coralprotocol.coralserver.agent.registry

import UnresolvedAgentOption
import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.runtime.AgentRuntimes
import org.coralprotocol.coralserver.agent.runtime.RuntimeId

@Serializable
data class UnresolvedRegistryAgent(
    @SerialName("agent")
    val agentInfo: AgentInfo,

    @Description("The runtimes that this agent supports")
    val runtimes: AgentRuntimes,

    @Description("The options that this agent supports, for example the API keys required for the agent to function")
    val options: Map<String, UnresolvedAgentOption>
) {
    fun resolve(): RegistryAgent = RegistryAgent(
        runtimes = runtimes,
        options = options.mapValues { (_, option) ->
            option.resolve()
        }
    )
}

@Serializable
data class RegistryAgent(
    val runtimes: AgentRuntimes,
    val options: Map<String, AgentOption>
)

@Serializable
data class PublicRegistryAgent(
    val id: String,
    val runtimes: List<RuntimeId>,
    val options: Map<String, AgentOption>
)

fun RegistryAgent.toPublic(id: String): PublicRegistryAgent = PublicRegistryAgent(
    id = id,
    runtimes = runtimes.toRuntimeIds(),
    options = options
)