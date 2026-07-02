package org.coralprotocol.coralserver.agent.runtime

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
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
import org.coralprotocol.coralserver.session.SessionAgentConnectionStatus
import org.coralprotocol.coralserver.session.SessionAgentExecutionContext
import org.coralprotocol.coralserver.session.SessionAgentStatus
import kotlin.time.Duration.Companion.minutes

private const val NANOS_PER_CPU = 1_000_000_000L
private const val BYTES_PER_MIB = 1024L * 1024L
private val AGENT_CONNECT_TIMEOUT = 2.minutes

/**
 * Off-host runtime for untrusted agents. coral-server stays private: it builds the per-agent spec and
 * delegates provisioning to coral-cloud (which owns the Fly fleet + the gateway the agent connects
 * back through). The agent's callback URLs use [AddressConsumer.EXTERNAL] → cloud's public gateway,
 * with the per-agent secret in the path.
 *
 * The author ships their own [image] (non-root). Egress is enforced by cloud's egress sidecar — a
 * separate container in the same microVM that locks the VM's egress to the declared hosts (nftables +
 * a DNS-intercepting resolver) — so the untrusted image never has to cooperate. See coral-cloud's
 * AGENT_EGRESS.md.
 */
@Serializable
@SerialName("sandbox")
data class SandboxRuntime(
    /** The author's OCI image (should be digest-pinned). Runs as the non-root agent container. */
    val image: String,
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
                image = image,
                env = environment,
                egress = Egress(declared = declared),
                resources = resources,
            )
        )
        executionContext.logger.info {
            "Provisioned off-host agent ${executionContext.agent.name} -> machine ${handle.machineId}"
        }

        // Track the agent's own MCP connection instead of blocking blindly: fail fast if it never
        // connects, and return once it disconnects (exited or crashed) so a dead off-host agent is
        // detected now rather than at session TTL. The machine is reaped by the session-end webhook +
        // Fly auto_destroy; cancellation (session teardown) propagates out of these suspends.
        val status = executionContext.agent.status
        val connected = withTimeoutOrNull(AGENT_CONNECT_TIMEOUT) {
            status.first {
                it is SessionAgentStatus.Running &&
                    it.connectionStatus is SessionAgentConnectionStatus.Connected
            }
        }
        if (connected == null) {
            executionContext.logger.warn {
                "off-host agent ${executionContext.agent.name} never connected within $AGENT_CONNECT_TIMEOUT; giving up"
            }
            return
        }
        status.first {
            it !is SessionAgentStatus.Running ||
                it.connectionStatus is SessionAgentConnectionStatus.NotConnected
        }
        executionContext.logger.info {
            "off-host agent ${executionContext.agent.name} disconnected; runtime exiting"
        }
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
