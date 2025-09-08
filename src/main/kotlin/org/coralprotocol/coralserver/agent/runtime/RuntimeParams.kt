package org.coralprotocol.coralserver.agent.runtime

import org.coralprotocol.coralserver.agent.registry.AgentOptionValue
import org.coralprotocol.coralserver.agent.registry.AgentRegistryIdentifier
import org.coralprotocol.coralserver.session.LocalSession
import org.coralprotocol.coralserver.session.remote.RemoteSession

sealed interface RuntimeParams {
    val agentId: AgentRegistryIdentifier
    val agentName: String
    val systemPrompt: String?
    val options: Map<String, AgentOptionValue>

    data class Local(
        val session: LocalSession,
        val applicationId: String,
        val privacyKey: String,
        override val agentId: AgentRegistryIdentifier,
        override val agentName: String,
        override val systemPrompt: String?,
        override val options: Map<String, AgentOptionValue>,
    ): RuntimeParams

    data class Remote(
        val session: RemoteSession,
        override val agentId: AgentRegistryIdentifier,
        override val agentName: String,
        override val systemPrompt: String?,
        override val options: Map<String, AgentOptionValue>,
    ): RuntimeParams
}