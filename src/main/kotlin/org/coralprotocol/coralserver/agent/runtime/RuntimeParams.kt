package org.coralprotocol.coralserver.agent.runtime

import org.coralprotocol.coralserver.agent.registry.option.AgentOptionValue
import org.coralprotocol.coralserver.agent.registry.AgentRegistryIdentifier
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionWithValue
import org.coralprotocol.coralserver.session.LocalSession
import org.coralprotocol.coralserver.session.remote.RemoteSession
import java.nio.file.Path

sealed interface RuntimeParams {
    val agentId: AgentRegistryIdentifier
    val agentName: String
    val systemPrompt: String?
    val options: Map<String, AgentOptionWithValue>
    val path: Path
    val agentSecret: String

    data class Local(
        val session: LocalSession,
        val applicationId: String,
        val privacyKey: String,
        override val agentId: AgentRegistryIdentifier,
        override val agentName: String,
        override val systemPrompt: String?,
        override val options: Map<String, AgentOptionWithValue>,
        override val path: Path,
        override val agentSecret: String,
    ): RuntimeParams

    data class Remote(
        val session: RemoteSession,
        override val agentId: AgentRegistryIdentifier,
        override val agentName: String,
        override val systemPrompt: String?,
        override val options: Map<String, AgentOptionWithValue>,
        override val path: Path,
        override val agentSecret: String,
    ): RuntimeParams
}

fun RuntimeParams.getId() =
    when (this) {
        is RuntimeParams.Local -> session.id
        is RuntimeParams.Remote -> session.id
    }
