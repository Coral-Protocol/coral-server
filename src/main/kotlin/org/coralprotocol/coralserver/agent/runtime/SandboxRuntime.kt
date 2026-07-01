package org.coralprotocol.coralserver.agent.runtime

import kotlinx.coroutines.awaitCancellation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
        // Env points the agent at cloud's gateway (EXTERNAL); cloud forwards it into the machine verbatim.
        val environment = executionContext.buildEnvironment(transport, AddressConsumer.EXTERNAL)

        // Only the author-declared hosts; cloud injects its own gateway endpoint + resolves IPs.
        val declared = compileEgressPolicy(
            declared = executionContext.registryAgent.execution,
            coralUrls = emptySet(),
        ).declared.map { Endpoint(host = it.host, port = it.port) }

        val resources = executionContext.executionPolicy.docker.let { trust ->
            if (trust.nanoCpus == null && trust.memoryLimitBytes == null) null
            else Resources(
                cpus = ((trust.nanoCpus ?: NANOS_PER_CPU) / NANOS_PER_CPU).toInt().coerceAtLeast(1),
                memoryMb = ((trust.memoryLimitBytes ?: (512L * BYTES_PER_MIB)) / BYTES_PER_MIB).toInt(),
            )
        }

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

        // coral-server can't observe the off-host agent's exit, so block until the session/TTL teardown
        // cancels this coroutine. The machine is reaped by the session-end webhook (cloud looks it up by
        // coral_session) + Fly auto_destroy.
        awaitCancellation()
    }
}
