package org.coralprotocol.coralserver.agent.runtime

import org.coralprotocol.coralserver.agent.registry.AgentRegistryIdentifier
import org.coralprotocol.coralserver.session.LocalSession
import org.coralprotocol.coralserver.session.remote.RemoteSession

sealed interface RuntimeParams {
    val agentId: AgentRegistryIdentifier

    data class Local(
        val session: LocalSession,
        override val agentId: AgentRegistryIdentifier,
    ): RuntimeParams

    data class Remote(
        val session: RemoteSession,
        override val agentId: AgentRegistryIdentifier,
    ): RuntimeParams
}

fun RuntimeParams.getId() =
    when (this) {
        is RuntimeParams.Local -> session.id
        is RuntimeParams.Remote -> session.id
    }
