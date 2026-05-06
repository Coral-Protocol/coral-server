package org.coralprotocol.coralserver.llmproxy

import org.coralprotocol.coralserver.logging.LoggingInterface
import org.coralprotocol.coralserver.session.SessionAgent
import kotlin.time.Instant

data class LlmProxyRequest(
    val logger: LoggingInterface,
    val proxyRequestName: String,
    val model: LlmProxiedModel,
    val agent: SessionAgent,
    val upstreamUrl: String,
    val requestBody: String,
    val hasBody: Boolean,
    val startTime: Instant
)