package org.coralprotocol.coralserver.agent.runtime

import com.github.dockerjava.api.model.AccessMode
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.Capability
import com.github.dockerjava.api.model.StreamType
import com.github.dockerjava.api.model.Volume
import io.github.smiley4.schemakenerator.core.annotations.Optional
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.execution.EgressEndpoint
import org.coralprotocol.coralserver.agent.execution.EgressPolicy
import org.coralprotocol.coralserver.config.OpenShellConfig
import org.coralprotocol.coralserver.events.SessionEvent
import org.coralprotocol.coralserver.mcp.McpTransportType
import org.coralprotocol.coralserver.session.SessionAgentDisposableResource
import org.coralprotocol.coralserver.session.SessionAgentExecutionContext
import java.net.InetAddress
import java.nio.file.Files
import kotlin.io.path.writeText

@Serializable
@SerialName("openshell")
data class OpenShellRuntime(
    val image: String,
    override val transport: McpTransportType = DEFAULT_AGENT_RUNTIME_TRANSPORT,
    @Optional val command: List<String>? = null,
) : AgentRuntime {
    override suspend fun execute(
        executionContext: SessionAgentExecutionContext,
        applicationRuntimeContext: ApplicationRuntimeContext,
    ) {
        val config = executionContext.openShellConfig
        val supervisor = checkNotNull(config.supervisorPath) {
            "openshell.supervisor_path is not configured (should have been caught by ExecutionPolicyResolver)"
        }
        warnOnSupervisorVersionMismatch(config, executionContext)

        val environment = executionContext.buildEnvironment(transport)
        val coralIp = resolveCoralIp(executionContext.dockerConfig.address)
        val policyDir = writePolicyFiles(executionContext.egressPolicy, config, coralIp)
        executionContext.disposableResources += policyDir

        val mountedPolicyDir = config.policyMountPath
        val supervisorArgs = listOf(
            "--policy-rules", "$mountedPolicyDir/sandbox.rego",
            "--policy-data", "$mountedPolicyDir/policy.yaml",
            "--",
        ) + (command ?: emptyList())

        val spec = DockerContainerSpec(
            image = image,
            env = environment,
            entrypoint = listOf(config.supervisorMountPath),
            cmd = supervisorArgs,
            additionalBinds = listOf(
                Bind(supervisor.toString(), Volume(config.supervisorMountPath), AccessMode.ro),
                Bind(policyDir.path.toString(), Volume(mountedPolicyDir), AccessMode.ro),
            ),
            // SYS_ADMIN+NET_ADMIN: create netns/veth and run iptables. SYS_PTRACE: trace the agent's syscalls
            // for seccomp+Landlock enforcement. SETUID+SETGID: drop privileges from root to the sandbox UID
            // after policy is loaded. DAC_READ_SEARCH: read agent-owned files even when running as a
            // different UID during the privilege handoff.
            extraCaps = listOf(
                Capability.SYS_ADMIN,
                Capability.NET_ADMIN,
                Capability.SYS_PTRACE,
                Capability.SETUID,
                Capability.SETGID,
                Capability.DAC_READ_SEARCH,
            ),
        )

        launchDockerContainer(
            spec = spec,
            executionContext = executionContext,
            applicationRuntimeContext = applicationRuntimeContext,
            onLogLine = { stream, line -> parseEgressViolation(stream, line, executionContext) },
        )
    }

    private fun warnOnSupervisorVersionMismatch(
        config: OpenShellConfig,
        ctx: SessionAgentExecutionContext,
    ) {
        val expected = config.expectedSupervisorVersion ?: return
        val supervisor = config.supervisorPath ?: return
        val actual = runCatching {
            ProcessBuilder(supervisor.toString(), "--version")
                .redirectErrorStream(true)
                .start()
                .inputStream.bufferedReader().use { it.readText() }
                .trim()
        }.getOrNull()
        if (actual == null || !actual.contains(expected)) {
            ctx.logger.warn {
                "openshell supervisor version mismatch: expected '$expected', got '${actual ?: "unknown"}'"
            }
        }
    }

    private fun writePolicyFiles(
        policy: EgressPolicy,
        config: OpenShellConfig,
        coralIp: String?,
    ): SessionAgentDisposableResource.TemporaryDirectory {
        val dir = SessionAgentDisposableResource.TemporaryDirectory("coral-openshell-")
        dir.path.resolve("policy.yaml").writeText(renderOpenShellPolicy(policy, coralIp))
        dir.path.resolve("sandbox.rego").writeText(loadRegoTemplate(config))
        return dir
    }

    private fun loadRegoTemplate(config: OpenShellConfig): String {
        config.regoTemplatePath?.let { path ->
            return Files.readString(path)
        }
        return OpenShellRuntime::class.java.getResourceAsStream("/openshell/sandbox.rego")
            ?.bufferedReader()?.use { it.readText() }
            ?: error("Bundled sandbox.rego resource missing")
    }
}

// Resolve dockerConfig.address to a single IP for use as a /32 in the supervisor policy.  Returns null when the
// address is a Docker Desktop alias (host.docker.internal) that only resolves from inside the container — the
// caller falls back to broader RFC1918 ranges in that case.
internal fun resolveCoralIp(address: String): String? {
    val ip = runCatching { InetAddress.getByName(address) }.getOrNull() ?: return null
    if (ip.isLoopbackAddress || ip.hostAddress == address) {
        // Loopback resolutions on macOS for host.docker.internal — meaningless to the container.
        // Otherwise an IP literal that we can use directly.
        return if (ip.isLoopbackAddress) null else address
    }
    return ip.hostAddress
}

private val OCSF_DENY = Regex("""OCSF (NET:OPEN|HTTP:[A-Z]+) \[.*?] DENIED .*?-> (\S+):(\d+)""")

private fun parseEgressViolation(
    stream: StreamType,
    line: String,
    ctx: SessionAgentExecutionContext,
) {
    if (stream != StreamType.STDOUT && stream != StreamType.STDERR) return
    val match = OCSF_DENY.find(line) ?: return
    val (protocol, host, port) = match.destructured
    ctx.logger.info { "egress_policy_violation: $protocol $host:$port" }
    ctx.session.events.tryEmit(
        SessionEvent.EgressPolicyViolation(
            agentName = ctx.agent.name,
            protocol = protocol,
            host = host,
            port = port.toInt(),
        )
    )
}

private val POLICY_PRELUDE = """
    version: 1

    filesystem_policy:
      include_workdir: true
      read_only:
        - /usr
        - /lib
        - /lib64
        - /bin
        - /sbin
        - /etc
        - /proc
        - /dev/urandom
        - /var/log
      read_write:
        - /sandbox
        - /tmp

    landlock:
      compatibility: best_effort

    process:
      run_as_user: sandbox
      run_as_group: sandbox
""".trimIndent()

private val DOCKER_BRIDGE_RANGES = listOf("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "fc00::/7")

fun renderOpenShellPolicy(policy: EgressPolicy, coralIp: String? = null): String = buildString {
    append(POLICY_PRELUDE)
    appendLine()
    appendLine()
    appendLine("network_policies:")

    if (policy.coralManaged.isNotEmpty()) {
        appendPolicyEntry(
            name = "coral_api",
            endpoints = policy.coralManaged.sortedEndpoints(),
            allowedIps = coralIp?.let { listOf("$it/32") } ?: DOCKER_BRIDGE_RANGES,
        )
    }
    policy.declared.sortedEndpoints().forEach { endpoint ->
        appendPolicyEntry(
            name = "external_${sanitisePolicyName(endpoint.host)}",
            endpoints = listOf(endpoint),
            allowedIps = null,
        )
    }
}

private fun StringBuilder.appendPolicyEntry(
    name: String,
    endpoints: List<EgressEndpoint>,
    allowedIps: List<String>?,
) {
    appendLine("  $name:")
    appendLine("    name: $name")
    appendLine("    endpoints:")
    endpoints.forEach { endpoint ->
        appendLine("      - host: ${endpoint.host}")
        appendLine("        ports: [${endpoint.port}]")
        if (allowedIps != null) {
            appendLine("        allowed_ips: [${allowedIps.joinToString(", ") { "\"$it\"" }}]")
        }
    }
    appendLine("    binaries:")
    appendLine("      - path: \"/**\"")
}

private fun Set<EgressEndpoint>.sortedEndpoints(): List<EgressEndpoint> =
    sortedWith(compareBy({ it.host }, { it.port }))

private fun sanitisePolicyName(host: String): String =
    host.lowercase().map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")
