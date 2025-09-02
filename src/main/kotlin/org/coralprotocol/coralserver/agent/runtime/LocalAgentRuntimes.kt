@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.runtime

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.EventBus

@Serializable
enum class RuntimeId {
    @SerialName("executable")
    EXECUTABLE,

    @SerialName("docker")
    DOCKER
}

@Serializable
@SerialName("runtime")
class LocalAgentRuntimes(
    @SerialName("executable")
    private val executableRuntime: ExecutableRuntime? = null,

    @SerialName("docker")
    private val dockerRuntime: DockerRuntime? = null,
) : Orchestrate {
    override fun spawn(
        params: RuntimeParams,
        eventBus: EventBus<RuntimeEvent>,
        applicationRuntimeContext: ApplicationRuntimeContext
    ): OrchestratorHandle {
        TODO("runtime must be selected")
    }

    fun getById(runtimeId: RuntimeId): Orchestrate? = when (runtimeId) {
        RuntimeId.EXECUTABLE -> executableRuntime
        RuntimeId.DOCKER -> dockerRuntime
    }

    fun toRuntimeIds(): List<RuntimeId> {
        return buildList {
            executableRuntime?.let { add(RuntimeId.EXECUTABLE) }
            dockerRuntime?.let { add(RuntimeId.DOCKER) }
        }
    }
}