package org.coralprotocol.coralserver.agent.execution

import kotlinx.serialization.Serializable

@Serializable
enum class ExecutionTrustTier {
    TRUSTED,
    UNTRUSTED,
}

@Serializable
data class DockerExecutionTrustPolicy(
    val readOnlyRootFilesystem: Boolean,
    val noNewPrivileges: Boolean,
    val dropCapabilities: Set<String>,
    val pidsLimit: Long? = null,
    val nanoCpus: Long? = null,
    val memoryLimitBytes: Long? = null,
    val user: String? = null,
    val tmpFs: Map<String, String> = emptyMap(),
    val requireImageDigest: Boolean = false,
)

@Serializable
data class ExecutionTrustPolicy(
    val profileName: String,
    val trustTier: ExecutionTrustTier,
    val allowExecutableRuntime: Boolean,
    val docker: DockerExecutionTrustPolicy,
)
