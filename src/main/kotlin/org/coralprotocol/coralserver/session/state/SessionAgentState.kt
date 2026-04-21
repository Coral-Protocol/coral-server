package org.coralprotocol.coralserver.session.state

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.execution.ExecutionConfig
import org.coralprotocol.coralserver.agent.execution.ExecutionTrustTier
import org.coralprotocol.coralserver.agent.graph.UniqueAgentName
import org.coralprotocol.coralserver.agent.registry.RegistryAgentIdentifier
import org.coralprotocol.coralserver.llmproxy.TokenUsage
import org.coralprotocol.coralserver.session.SessionAgentStatus
import org.coralprotocol.coralserver.session.SessionResource

@Serializable
@Description("The state of an agent running in a session")
data class SessionAgentState(
    @Description("The name given for this agent in the AgentGraph, this is unique in the graph")
    val name: UniqueAgentName,

    @Description("The identifier for this agent's registry entry.  See RegistryAgent for more information")
    val registryAgentIdentifier: RegistryAgentIdentifier,

    @Description("Nested status state for this agent, running -> connected -> thinking/waiting/sleeping")
    val status: SessionAgentStatus,

    @Description("The description of this agent, given to other agents in the graph")
    val description: String?,

    @Description("A list of agents that this agent is aware of, constructed from agent groups in the AgentGraph")
    val links: Set<UniqueAgentName>,

    override val annotations: Map<String, String>,

    @Description("Resolved execution profile applied to this agent")
    val executionProfile: String,

    @Description("Resolved trust tier applied to this agent")
    val trustTier: ExecutionTrustTier,

    @Description("Execution needs declared in the agent manifest; null if the agent did not declare any")
    val declaredExecution: ExecutionConfig?,

    @Description("Token usage broken down by provider/model (e.g. 'openai/gpt-4.1')")
    val tokensByModel: Map<String, TokenUsage> = emptyMap(),
) : SessionResource
