package org.coralprotocol.coralserver.agent.runtime

import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.EventBus
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