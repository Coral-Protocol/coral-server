package org.coralprotocol.coralserver.agent.execution

import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.runtime.RuntimeId

@Serializable
sealed class ExecutionRejection {
    abstract val reason: String

    @Serializable
    data class IsolationUnsupported(
        val required: MinIsolation,
        val maxSupported: MinIsolation,
    ) : ExecutionRejection() {
        override val reason: String
            get() = "declared isolation '$required' exceeds operator-supported '$maxSupported'"
    }

    @Serializable
    data class IsolationIncompatibleWithRuntime(
        val required: MinIsolation,
        val runtime: RuntimeId,
    ) : ExecutionRejection() {
        override val reason: String
            get() = "runtime '$runtime' cannot provide declared isolation '$required'"
    }

    @Serializable
    data class HostDenied(val host: String) : ExecutionRejection() {
        override val reason: String
            get() = "external host '$host' is not allowed by operator policy"
    }

    @Serializable
    data class SandboxUnavailable(val detail: String) : ExecutionRejection() {
        override val reason: String
            get() = "sandbox backend unavailable: $detail"
    }
}

class ExecutionRejectedException(val rejection: ExecutionRejection) :
    RuntimeException(rejection.reason)

