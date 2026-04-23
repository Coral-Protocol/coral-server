package org.coralprotocol.coralserver.agent.runtime

import com.github.dockerjava.api.model.AccessMode
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.Capability
import com.github.dockerjava.api.model.Volume
import io.github.smiley4.schemakenerator.core.annotations.Optional
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.execution.EgressEndpoint
import org.coralprotocol.coralserver.agent.execution.EgressPolicy
import org.coralprotocol.coralserver.agent.execution.ExecutionRejectedException
import org.coralprotocol.coralserver.agent.execution.ExecutionRejection
import org.coralprotocol.coralserver.config.OpenShellConfig
import org.coralprotocol.coralserver.mcp.McpTransportType
import org.coralprotocol.coralserver.session.SessionAgentDisposableResource
import org.coralprotocol.coralserver.session.SessionAgentExecutionContext
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
        val supervisor = config.supervisorPath
            ?: throw ExecutionRejectedException(
                ExecutionRejection.SandboxUnavailable("openshell.supervisor_path is not configured")
            )
        if (!supervisor.toFile().canExecute()) {
            throw ExecutionRejectedException(
                ExecutionRejection.SandboxUnavailable("openshell supervisor at $supervisor is not executable")
            )
        }

        val environment = executionContext.buildEnvironment(transport)
        val policyDir = writePolicyFiles(executionContext.egressPolicy, config)
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
            extraCaps = listOf(
                Capability.SYS_ADMIN,
                Capability.NET_ADMIN,
                Capability.SYS_PTRACE,
                Capability.SETUID,
                Capability.SETGID,
                Capability.DAC_READ_SEARCH,
            ),
        )

        DockerLauncher.launch(spec, executionContext, applicationRuntimeContext)
    }

    private fun writePolicyFiles(
        policy: EgressPolicy,
        config: OpenShellConfig,
    ): SessionAgentDisposableResource.TemporaryDirectory {
        val dir = SessionAgentDisposableResource.TemporaryDirectory("coral-openshell-")
        dir.path.resolve("policy.yaml").writeText(renderOpenShellPolicy(policy))
        dir.path.resolve("sandbox.rego").writeText(loadRegoTemplate(config))
        return dir
    }

    private fun loadRegoTemplate(config: OpenShellConfig): String {
        config.regoTemplatePath?.let { path ->
            return Files.readString(path)
        }
        // Bundled sandbox-policy.rego is sourced verbatim from
        //   NVIDIA/openshell crates/openshell-sandbox/data/sandbox-policy.rego
        //   (pinned @ 28e1ff7b, Apache-2.0, SPDX header preserved)
        // Operators requiring a different version may override with
        // OpenShellConfig.regoTemplatePath.
        return OpenShellRuntime::class.java.getResourceAsStream("/openshell/sandbox.rego")
            ?.bufferedReader()?.use { it.readText() }
            ?: error("Bundled sandbox.rego resource missing")
    }
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

fun renderOpenShellPolicy(policy: EgressPolicy): String = buildString {
    append(POLICY_PRELUDE)
    appendLine()
    appendLine()
    appendLine("network_policies:")

    if (policy.coralManaged.isNotEmpty()) {
        appendPolicyEntry(
            name = "coral_api",
            endpoints = policy.coralManaged.sortedEndpoints(),
            allowPrivateIps = true,
        )
    }
    policy.declared.sortedEndpoints().forEach { endpoint ->
        appendPolicyEntry(
            name = "external_${sanitisePolicyName(endpoint.host)}",
            endpoints = listOf(endpoint),
            allowPrivateIps = false,
        )
    }
}

private fun StringBuilder.appendPolicyEntry(
    name: String,
    endpoints: List<EgressEndpoint>,
    allowPrivateIps: Boolean,
) {
    appendLine("  $name:")
    appendLine("    name: $name")
    appendLine("    endpoints:")
    endpoints.forEach { endpoint ->
        appendLine("      - host: ${endpoint.host}")
        appendLine("        port: ${endpoint.port}")
        if (allowPrivateIps) {
            appendLine("        allowed_ips: [\"10.0.0.0/8\", \"172.16.0.0/12\", \"192.168.0.0/16\"]")
        }
    }
    appendLine("    binaries:")
    appendLine("      - path: \"/**\"")
}

private fun Set<EgressEndpoint>.sortedEndpoints(): List<EgressEndpoint> =
    sortedWith(compareBy({ it.host }, { it.port }))

private fun sanitisePolicyName(host: String): String =
    host.lowercase().map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")

