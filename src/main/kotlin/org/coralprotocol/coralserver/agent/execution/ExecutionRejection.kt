package org.coralprotocol.coralserver.agent.execution

import org.coralprotocol.coralserver.agent.runtime.RuntimeId

sealed class ExecutionRejection {
    abstract val reason: String

    data class IsolationUnsupported(
        val required: MinIsolation,
        val maxSupported: MinIsolation,
    ) : ExecutionRejection() {
        override val reason: String
            get() = "declared isolation '$required' exceeds operator-supported '$maxSupported'"
    }

    data class IsolationIncompatibleWithRuntime(
        val required: MinIsolation,
        val runtime: RuntimeId,
    ) : ExecutionRejection() {
        override val reason: String
            get() = "runtime '$runtime' cannot provide declared isolation '$required'"
    }

    data class HostDenied(val host: String) : ExecutionRejection() {
        override val reason: String
            get() = "external host '$host' is not allowed by operator policy"
    }

    data class SandboxUnavailable(val detail: String) : ExecutionRejection() {
        override val reason: String
            get() = "sandbox backend unavailable: $detail"
    }

    data class SandboxFileTransportUnsupported(val options: Set<String>) : ExecutionRejection() {
        override val reason: String
            get() = "sandbox runtime runs off-host and cannot deliver file-system options: " +
                options.sorted().joinToString()
    }

    data class RuntimeIncompatibleWithTrust(
        val runtime: RuntimeId,
        val profileName: String,
        val detail: String,
    ) : ExecutionRejection() {
        override val reason: String
            get() = "runtime '$runtime' cannot run under trust profile '$profileName': $detail"
    }

    data class RuntimeDisabled(
        val runtime: RuntimeId,
        val profileName: String,
        val allowedRuntimes: Set<RuntimeId>,
    ) : ExecutionRejection() {
        override val reason: String
            get() = "runtime '$runtime' is not in the allowed set " +
                "${allowedRuntimes.map { it.name.lowercase() }.sorted()} " +
                "for profile '$profileName'"
    }
}
