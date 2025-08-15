@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.orchestrator.runtime

import com.chrynan.uri.core.Uri
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.EventBus
import org.coralprotocol.coralserver.orchestrator.AgentOptionValue
import org.coralprotocol.coralserver.orchestrator.Orchestrate
import org.coralprotocol.coralserver.orchestrator.OrchestratorHandle
import org.coralprotocol.coralserver.orchestrator.RuntimeEvent
import org.coralprotocol.coralserver.session.SessionManager


data class RuntimeParams(
    val sessionId: String,
    val agentName: String,
    val mcpServerPort: UShort,
    val mcpServerRelativeUri: Uri,

    val systemPrompt: String?,
    val options: Map<String, AgentOptionValue>,
)

@Serializable
@SerialName("runtime")
class AgentRuntime(
    @SerialName("executable")
    val executableRuntime: Executable? = null,

    @SerialName("docker")
    val dockerRuntime: Docker? = null,
) : Orchestrate {
    override fun spawn(
        params: RuntimeParams,
        eventBus: EventBus<RuntimeEvent>,
        sessionManager: SessionManager?
    ): OrchestratorHandle {
        TODO("runtime must be selected")
    }
}