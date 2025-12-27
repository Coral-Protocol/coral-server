package org.coralprotocol.coralserver.utils.dsl

import org.coralprotocol.coralserver.agent.registry.*
import org.coralprotocol.coralserver.agent.registry.option.AgentOption
import org.coralprotocol.coralserver.agent.runtime.*

@TestDsl
class RegistryAgentBuilder(
    var name: String,
) {
    var description: String? = null
    var version: String = "1.0.0"
    var registrySourceId: AgentRegistrySourceIdentifier = AgentRegistrySourceIdentifier.Local
    var runtimes: LocalAgentRuntimes = LocalAgentRuntimes()

    private val capabilities: MutableSet<AgentCapability> = mutableSetOf()
    private val options: MutableMap<String, AgentOption> = mutableMapOf()
    private val unresolvedExportSettings: MutableMap<RuntimeId, UnresolvedAgentExportSettings> = mutableMapOf()

    fun capability(capability: AgentCapability) {
        capabilities.add(capability)
    }

    fun option(key: String, value: AgentOption) {
        options[key] = value
    }

    fun exportSetting(runtime: RuntimeId, value: UnresolvedAgentExportSettings) {
        unresolvedExportSettings[runtime] = value
    }

    fun runtime(functionRuntime: FunctionRuntime) {
        runtimes = LocalAgentRuntimes(
            executableRuntime = runtimes.executableRuntime,
            dockerRuntime = runtimes.dockerRuntime,
            functionRuntime = functionRuntime
        )
    }

    fun runtime(dockerRuntime: DockerRuntime) {
        runtimes = LocalAgentRuntimes(
            executableRuntime = runtimes.executableRuntime,
            dockerRuntime = dockerRuntime,
            functionRuntime = runtimes.functionRuntime
        )
    }

    fun runtime(executableRuntime: ExecutableRuntime) {
        runtimes = LocalAgentRuntimes(
            executableRuntime = executableRuntime,
            dockerRuntime = runtimes.dockerRuntime,
            functionRuntime = runtimes.functionRuntime
        )
    }

    fun build(): RegistryAgent {
        return RegistryAgent(
            info = RegistryAgentInfo(
                description = description,
                capabilities = capabilities,
                identifier = RegistryAgentIdentifier(
                    name = name,
                    version = version,
                    registrySourceId = registrySourceId
                )
            ),
            runtimes = runtimes,
            options = options,
            path = null,
            unresolvedExportSettings = unresolvedExportSettings
        )
    }
}

fun registryAgent(name: String, block: RegistryAgentBuilder.() -> Unit = {}): RegistryAgent =
    RegistryAgentBuilder(name).apply(block).build()