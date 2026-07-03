package org.coralprotocol.coralserver.agent.runtime

import kotlinx.coroutines.awaitCancellation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.execution.DockerExecutionTrustPolicy
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
 * Off-host runtime for untrusted agents. coral-server stays private: it builds the per-agent spec and
 * delegates provisioning to coral-cloud (which owns the Fly fleet + the gateway the agent connects
 * back through). The agent's callback URLs use [AddressConsumer.EXTERNAL] → cloud's public gateway,
 * with the per-agent secret in the path.
 *
 * The author ships their own [image] (non-root, digest-pinned per the trust profile). Egress is
 * enforced by cloud's egress sidecar — a separate container in the same microVM that locks the VM's
 * egress to the declared hosts (nftables + a DNS-intercepting resolver) — so the untrusted image never
 * has to cooperate. See coral-cloud's AGENT_EGRESS.md.
 */
@Serializable
@SerialName("sandbox")
data class SandboxRuntime(
    /** The author's OCI image. Digest-pinning is enforced per the trust profile at launch. */
    val image: String,
    override val transport: McpTransportType = DEFAULT_AGENT_RUNTIME_TRANSPORT,
) : AgentRuntime {
    override suspend fun execute(
        executionContext: SessionAgentExecutionContext,
        applicationRuntimeContext: ApplicationRuntimeContext,
    ) {
        // Env points the agent at cloud's gateway (EXTERNAL); cloud forwards it into the machine verbatim.
        val environment = executionContext.buildEnvironment(transport, AddressConsumer.EXTERNAL)

        // Same digest-pinning the Docker/OpenShell tiers apply to untrusted images.
        val pinnedImage = executionContext.executionPolicy.docker.sanitizeImage(
            imageName = image,
            id = executionContext.registryAgent.identifier,
            profileName = executionContext.executionPolicy.profileName,
            logger = executionContext.logger,
        )

        // Only the author-declared hosts; cloud injects its own gateway endpoint + resolves IPs.
        val declared = compileEgressPolicy(
            declared = executionContext.registryAgent.execution,
            coralUrls = emptySet(),
        ).declared.map { Endpoint(host = it.host) }

        val handle = executionContext.sandboxProvider.provision(
            ProvisionRequest(
                agentName = executionContext.agent.name,
                coralSession = executionContext.agent.session.id,
                image = pinnedImage,
                env = environment,
                egress = Egress(declared = declared),
                resources = sandboxResources(executionContext.executionPolicy.docker),
            )
        )
        executionContext.logger.info {
            "Provisioned off-host agent ${executionContext.agent.name} -> machine ${handle.machineId}"
        }

        // Fail fast if the agent never phones home; otherwise stay alive until session teardown cancels
        // us (cancellation propagates out). coral-server holds no machine handle and issues no
        // deprovision: cloud reaps the machine on the session-end webhook, with its orphan sweeper as the
        // backstop (Fly auto_destroy does not fire while the always-on egress sidecar keeps the VM up).
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
