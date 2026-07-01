package org.coralprotocol.coralserver.agent.execution

import io.ktor.http.*

data class EgressEndpoint(
    val host: String,
    val port: Int,
)

data class EgressPolicy(
    val declared: Set<EgressEndpoint>,
    val coralManaged: Set<EgressEndpoint>,
)

private const val DEFAULT_EXTERNAL_PORT = 443

fun compileEgressPolicy(
    declared: ExecutionConfig?,
    coralUrls: Set<Url>,
): EgressPolicy = EgressPolicy(
    declared = declared?.externalHosts.orEmpty().map { it.toEgressEndpoint() }.toSet(),
    coralManaged = coralUrls.map { EgressEndpoint(it.host, it.port) }.toSet(),
)

private fun String.toEgressEndpoint(): EgressEndpoint = EgressEndpoint(
    host = substringBeforeLast(':', this),
    port = substringAfterLast(':', "").toIntOrNull() ?: DEFAULT_EXTERNAL_PORT,
)
