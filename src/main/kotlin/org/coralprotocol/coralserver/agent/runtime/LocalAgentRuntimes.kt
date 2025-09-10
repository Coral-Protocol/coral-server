@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.runtime

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.coralprotocol.coralserver.EventBus

@Serializable
enum class RuntimeId {
    @SerialName("executable")
    EXECUTABLE,

    @SerialName("docker")
    DOCKER,

    @Transient
    FUNCTION
}

@Serializable
@SerialName("runtime")
class LocalAgentRuntimes(
    @SerialName("executable")
    val executableRuntime: ExecutableRuntime? = null,

    @SerialName("docker")
    val dockerRuntime: DockerRuntime? = null,

    @Transient
    val functionRuntime: FunctionRuntime? = null
) {
    fun getById(runtimeId: RuntimeId): Orchestrate? = when (runtimeId) {
        RuntimeId.EXECUTABLE -> executableRuntime
        RuntimeId.DOCKER -> dockerRuntime
        RuntimeId.FUNCTION -> functionRuntime
    }

    fun toRuntimeIds(): List<RuntimeId> {
        return buildList {
            executableRuntime?.let { add(RuntimeId.EXECUTABLE) }
            dockerRuntime?.let { add(RuntimeId.DOCKER) }
        }
    }
}