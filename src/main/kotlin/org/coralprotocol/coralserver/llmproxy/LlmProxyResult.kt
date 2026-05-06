package org.coralprotocol.coralserver.llmproxy

import io.ktor.http.*
import kotlin.time.Clock
import kotlin.time.Instant

sealed interface LlmProxyResult {
    val request: LlmProxyRequest
    val endTime: Instant

    val duration
        get() = endTime - request.startTime

    data class Buffered(
        override val request: LlmProxyRequest,
        val statusCode: HttpStatusCode,
        val usage: LlmUsage? = null,
        override val endTime: Instant = Clock.System.now(),
    ) : LlmProxyResult

    data class Streamed(
        override val request: LlmProxyRequest,
        val statusCode: HttpStatusCode,
        val chunkCount: Int,
        val usage: LlmUsage? = null,
        override val endTime: Instant = Clock.System.now(),
    ) : LlmProxyResult

    data class Exception(
        override val request: LlmProxyRequest,
        val error: Throwable,
        override val endTime: Instant = Clock.System.now(),
    ) : LlmProxyResult
}