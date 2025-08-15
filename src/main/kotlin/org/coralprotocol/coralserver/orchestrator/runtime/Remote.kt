package org.coralprotocol.coralserver.orchestrator.runtime

import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.EventBus
import org.coralprotocol.coralserver.orchestrator.Orchestrate
import org.coralprotocol.coralserver.orchestrator.OrchestratorHandle
import org.coralprotocol.coralserver.orchestrator.RuntimeEvent
import org.coralprotocol.coralserver.session.SessionManager

@Serializable
data class Remote(
    val host: String,
    val agentType: String,
    val appId: String,
    val privacyKey: String,
) : Orchestrate {
    override fun spawn(
        params: RuntimeParams,
        bus: EventBus<RuntimeEvent>,
        sessionManager: SessionManager?,
    ): OrchestratorHandle {
        TODO("request agent from remote server")
    }
}