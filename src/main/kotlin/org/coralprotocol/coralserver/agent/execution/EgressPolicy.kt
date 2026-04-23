package org.coralprotocol.coralserver.agent.execution

import io.github.smiley4.schemakenerator.core.annotations.Description
import io.ktor.http.*
import kotlinx.serialization.Serializable

@Serializable
@Description("Host and port the sandboxed agent is permitted to reach")
data class EgressEndpoint(
    val host: String,
    val port: Int,
)

@Serializable
data class EgressPolicy(
    val declared: Set<EgressEndpoint>,
    val coralManaged: Set<EgressEndpoint>,
) {
    fun allAllowed(): Set<EgressEndpoint> = declared + coralManaged
}

private const val DEFAULT_EXTERNAL_PORT = 443

object EgressCompiler {
    fun compile(
        declared: ExecutionConfig?,
        coralUrls: Set<Url>,
    ): EgressPolicy = EgressPolicy(
        declared = declared?.network?.externalHosts.orEmpty()
            .map { host -> EgressEndpoint(host, DEFAULT_EXTERNAL_PORT) }
            .toSet(),
        coralManaged = coralUrls.map { EgressEndpoint(it.host, it.port) }.toSet(),
    )
}
