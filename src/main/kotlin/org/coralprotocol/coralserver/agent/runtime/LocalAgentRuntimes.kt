@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.runtime

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class RuntimeId {
    @SerialName("executable")
    EXECUTABLE,

    @SerialName("docker")
    DOCKER,

    @SerialName("function")
    FUNCTION,

    @SerialName("prototype")
    PROTOTYPE,

    @SerialName("openshell")
    OPENSHELL;

    val providesContainerIsolation: Boolean
        get() = this == DOCKER || this == OPENSHELL
}

@Serializable
@SerialName("runtime")
data class LocalAgentRuntimes(
    @SerialName("executable")
    val executableRuntime: ExecutableRuntime? = null,

    @SerialName("docker")
    val dockerRuntime: DockerRuntime? = null,

    @SerialName("function")
    val functionRuntime: FunctionRuntime? = null,

    @SerialName("prototype")
    val prototypeRuntime: PrototypeRuntime? = null,

    @SerialName("openshell")
    val openShellRuntime: OpenShellRuntime? = null,
) {
    fun getById(runtimeId: RuntimeId): AgentRuntime? =
        when (runtimeId) {
            RuntimeId.EXECUTABLE -> executableRuntime
            RuntimeId.DOCKER -> dockerRuntime
            RuntimeId.FUNCTION -> functionRuntime
            RuntimeId.PROTOTYPE -> prototypeRuntime
            RuntimeId.OPENSHELL -> openShellRuntime
        }

    fun toRuntimeIds(): List<RuntimeId> {
        return buildList {
            executableRuntime?.let { add(RuntimeId.EXECUTABLE) }
            dockerRuntime?.let { add(RuntimeId.DOCKER) }
            functionRuntime?.let { add(RuntimeId.FUNCTION) }
            prototypeRuntime?.let { add(RuntimeId.PROTOTYPE) }
            openShellRuntime?.let { add(RuntimeId.OPENSHELL) }
        }
    }
}