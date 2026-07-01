package org.coralprotocol.coralserver.agent.runtime

import kotlinx.coroutines.awaitCancellation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.execution.DockerExecutionTrustPolicy
import org.coralprotocol.coralserver.agent.execution.compileEgressPolicy
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
 * Off-host runtime for untrusted agents. coral-server stays private: it builds the per-agent spec and
 * delegates provisioning to coral-cloud (which owns the Fly fleet + the gateway the agent connects
 * back through). The agent's callback URLs use [AddressConsumer.EXTERNAL] → cloud's public gateway,
 * with the per-agent secret in the path.
 *
 * There is deliberately no author-supplied image: cloud runs its own base image, whose entrypoint
 * applies the egress firewall before dropping to the agent. Running an arbitrary author image while
 * still enforcing egress via that entrypoint is unsolved (needs platform-level egress or an init
 * wrapper), so it is out of scope for the MVP.
 */
@Serializable
@SerialName("sandbox")
data class SandboxRuntime(
    override val transport: McpTransportType = DEFAULT_AGENT_RUNTIME_TRANSPORT,
) : AgentRuntime {
    override suspend fun execute(
        executionContext: SessionAgentExecutionContext,
        applicationRuntimeContext: ApplicationRuntimeContext,
    ) {
        // Env points the agent at cloud's gateway (EXTERNAL); cloud forwards it into the machine verbatim.
        val environment = executionContext.buildEnvironment(transport, AddressConsumer.EXTERNAL)

        // Only the author-declared hosts; cloud injects its own gateway endpoint + resolves IPs.
        val declared = compileEgressPolicy(
            declared = executionContext.registryAgent.execution,
            coralUrls = emptySet(),
        ).declared.map { Endpoint(host = it.host, port = it.port) }

        val resources = sandboxResources(executionContext.executionPolicy.docker)

        val handle = executionContext.sandboxProvider.provision(
            ProvisionRequest(
                agentName = executionContext.agent.name,
                coralSession = executionContext.agent.session.id,
                env = environment,
                egress = Egress(declared = declared),
                resources = resources,
            )
        )
        executionContext.logger.info {
            "Provisioned off-host agent ${executionContext.agent.name} -> machine ${handle.machineId}"
        }

        // coral-server can't observe the off-host agent's exit, so block until the session/TTL teardown
        // cancels this coroutine. The machine is reaped by the session-end webhook (cloud looks it up by
        // coral_session) + Fly auto_destroy.
        awaitCancellation()
    }
}

/**
 * Maps the trust profile's Docker CPU/memory limits to Fly guest sizing, or null when the profile
 * sets no limits (cloud then applies its own defaults). Lossy by design: integer division floors, and
 * a sub-1 vCPU limit is raised to the 1-vCPU minimum Fly requires.
 */
internal fun sandboxResources(docker: DockerExecutionTrustPolicy): Resources? =
    if (docker.nanoCpus == null && docker.memoryLimitBytes == null) null
    else Resources(
        cpus = ((docker.nanoCpus ?: NANOS_PER_CPU) / NANOS_PER_CPU).toInt().coerceAtLeast(1),
        memoryMb = ((docker.memoryLimitBytes ?: (512L * BYTES_PER_MIB)) / BYTES_PER_MIB).toInt(),
    )
