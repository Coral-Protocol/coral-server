package org.coralprotocol.coralserver.agent.runtime

import io.github.smiley4.schemakenerator.core.annotations.Optional
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.mcp.McpTransportType
import org.coralprotocol.coralserver.session.SessionAgentExecutionContext

@Serializable
@SerialName("docker")
data class DockerRuntime(
    val image: String,
    override val transport: McpTransportType = DEFAULT_AGENT_RUNTIME_TRANSPORT,
    @Optional val command: List<String>? = null
) : AgentRuntime {
    override suspend fun execute(
        executionContext: SessionAgentExecutionContext,
        applicationRuntimeContext: ApplicationRuntimeContext
    ) {
        launchDockerContainer(
            spec = DockerContainerSpec(
                image = image,
                env = executionContext.buildEnvironment(transport),
                cmd = command,
            ),
            executionContext = executionContext,
            applicationRuntimeContext = applicationRuntimeContext,
        )
    }
}
