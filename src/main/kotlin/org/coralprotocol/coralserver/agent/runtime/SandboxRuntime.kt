package org.coralprotocol.coralserver.agent.runtime

import kotlinx.coroutines.awaitCancellation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.execution.DockerExecutionTrustPolicy
import org.coralprotocol.coralserver.agent.execution.ExecutionConfig
import org.coralprotocol.coralserver.agent.execution.compileEgressPolicy
import org.coralprotocol.coralserver.agent.execution.sanitizeImage
import org.coralprotocol.coralserver.cloud.Egress
import org.coralprotocol.coralserver.cloud.Endpoint
import org.coralprotocol.coralserver.cloud.ProvisionRequest
import org.coralprotocol.coralserver.cloud.Resources
import org.coralprotocol.coralserver.config.AddressConsumer
import org.coralprotocol.coralserver.mcp.McpTransportType
import org.coralprotocol.coralserver.session.SessionAgentExecutionContext

private const val NANOS_PER_CPU = 1_000_000_000L
private const val BYTES_PER_MIB = 1024L * 1024L

/**
 * Off-host runtime for untrusted agents: coral-server stays private and delegates provisioning to
 * coral-cloud (Fly fleet + gateway). Cloud's sidecar enforces egress; see coral-cloud's AGENT_EGRESS.md.
 */
@Serializable
@SerialName("sandbox")
data class SandboxRuntime(
    val image: String,
    override val transport: McpTransportType = DEFAULT_AGENT_RUNTIME_TRANSPORT,
) : AgentRuntime {
    override suspend fun execute(
        executionContext: SessionAgentExecutionContext,
        applicationRuntimeContext: ApplicationRuntimeContext,
    ) {
        val environment = executionContext.buildEnvironment(transport, AddressConsumer.EXTERNAL)

        val pinnedImage = executionContext.executionPolicy.docker.sanitizeImage(
            imageName = image,
            id = executionContext.registryAgent.identifier,
            profileName = executionContext.executionPolicy.profileName,
            logger = executionContext.logger,
        )

        val handle = executionContext.sandboxClient.provision(
            ProvisionRequest(
                agentName = executionContext.agent.name,
                coralSession = executionContext.agent.session.id,
                image = pinnedImage,
                env = environment,
                egress = Egress(declared = authorDeclaredEndpoints(executionContext.registryAgent.execution)),
                resources = sandboxResources(executionContext.executionPolicy.docker),
            )
        )
        executionContext.logger.info {
            "Provisioned off-host agent ${executionContext.agent.name} -> machine ${handle.machineId}"
        }

        // No deprovision here: cloud reaps via the session-end webhook + orphan sweeper.
        val connectTimeout = executionContext.sandboxConfig.connectTimeout
        if (!executionContext.agent.waitForMcpConnection(connectTimeout)) {
            executionContext.logger.warn {
                "off-host agent ${executionContext.agent.name} never connected within $connectTimeout; giving up"
            }
            return
        }
        awaitCancellation()
    }
}

private fun authorDeclaredEndpoints(execution: ExecutionConfig?): List<Endpoint> =
    compileEgressPolicy(declared = execution, coralUrls = emptySet())
        .declared.map { Endpoint(host = it.host) }

/** Null when the profile sets no limits (cloud defaults then). Lossy: floors; sub-1 vCPU → 1. */
internal fun sandboxResources(docker: DockerExecutionTrustPolicy): Resources? =
    if (docker.nanoCpus == null && docker.memoryLimitBytes == null) null
    else Resources(
        cpus = ((docker.nanoCpus ?: NANOS_PER_CPU) / NANOS_PER_CPU).toInt().coerceAtLeast(1),
        memoryMb = ((docker.memoryLimitBytes ?: (512L * BYTES_PER_MIB)) / BYTES_PER_MIB).toInt(),
    )
