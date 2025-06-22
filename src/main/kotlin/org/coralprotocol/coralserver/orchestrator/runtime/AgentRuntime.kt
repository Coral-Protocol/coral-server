package org.coralprotocol.coralserver.orchestrator.runtime

import com.chrynan.uri.core.Uri
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.orchestrator.ConfigValue
import org.coralprotocol.coralserver.orchestrator.Orchestrate
import org.coralprotocol.coralserver.orchestrator.OrchestratorHandle


@Serializable
sealed class AgentRuntime : Orchestrate {
    @Serializable
    @SerialName("remote")
    data class Remote(
        val host: String,
        val agentType: String,
        val appId: String,
        val privacyKey: String,
    ) : AgentRuntime() {
        override fun spawn(
            agentName: String,
            port: UShort,
            relativeMcpServerUri: Uri,
            options: Map<String, ConfigValue>,
            sessionId: String
        ): OrchestratorHandle {
            TODO("request agent from remote server")
        }
    }
}