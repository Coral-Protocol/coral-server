@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.runtime

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
enum class RuntimeId {
    @SerialName("executable")
    EXECUTABLE,

    @SerialName("docker")
    DOCKER,

    @SerialName("function")
    FUNCTION
}

@Serializable
@SerialName("runtime")
class LocalAgentRuntimes(
    @SerialName("executable")
    val executableRuntime: ExecutableRuntime? = null,

//    @SerialName("docker")
//    val dockerRuntime: DockerRuntime? = null,
//
    @SerialName("function")
    @Transient
    val functionRuntime: FunctionRuntime? = null
) {
    fun getById(runtimeId: RuntimeId): AgentRuntime? =
        when (runtimeId) {
            RuntimeId.EXECUTABLE -> executableRuntime
            RuntimeId.DOCKER -> TODO()
            RuntimeId.FUNCTION -> functionRuntime
        }

    fun toRuntimeIds(): List<RuntimeId> {
        return buildList {
            executableRuntime?.let { add(RuntimeId.EXECUTABLE) }
            //dockerRuntime?.let { add(RuntimeId.DOCKER) }
        }
    }
}