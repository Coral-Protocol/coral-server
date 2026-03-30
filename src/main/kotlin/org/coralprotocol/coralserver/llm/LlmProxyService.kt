package org.coralprotocol.coralserver.llm

import io.ktor.client.*
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.utils.io.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.coralprotocol.coralserver.config.LlmProxyConfig
import org.coralprotocol.coralserver.events.SessionEvent
import org.coralprotocol.coralserver.logging.Logger
import org.coralprotocol.coralserver.session.SessionAgent
import kotlin.coroutines.cancellation.CancellationException

private val HOP_BY_HOP_HEADERS = setOf(
    HttpHeaders.Connection,
    HttpHeaders.TransferEncoding,
    HttpHeaders.Upgrade,
    "keep-alive",
    "proxy-authenticate",
    "proxy-authorization",
    "te",
    "trailer",
)

private val STRIP_REQUEST_HEADERS = (HOP_BY_HOP_HEADERS + setOf(
    HttpHeaders.Authorization,
    HttpHeaders.Host,
    HttpHeaders.ContentLength,
    HttpHeaders.AcceptEncoding,
    "x-api-key",
)).map { it.lowercase() }.toSet()

private val STRIP_RESPONSE_HEADERS = (HOP_BY_HOP_HEADERS + setOf(
    HttpHeaders.ContentLength,
    HttpHeaders.ContentEncoding,
    HttpHeaders.SetCookie,
)).map { it.lowercase() }.toSet()

private val METHODS_WITH_BODY = setOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch)

private const val MAX_REQUEST_BODY_BYTES = 20 * 1024 * 1024 // 20 MB
private const val MAX_RESPONSE_BODY_BYTES = 80 * 1024 * 1024L // 80 MB

class LlmProxyException(message: String) : Exception(message)

class LlmProxyService(
    private val config: LlmProxyConfig,
    private val httpClient: HttpClient,
    private val json: Json,
    private val logger: Logger
) {
    suspend fun proxyRequest(
        agent: SessionAgent,
        providerName: String,
        subPath: String,
        call: ApplicationCall
    ) {
        val profile = LlmProviderProfile.fromId(providerName)
        if (profile == null) {
            call.respond(HttpStatusCode.BadRequest, "Unknown provider: $providerName")
            return
        }

        val providerConfig = config.providers[providerName]
        val serverKey = providerConfig?.apiKey
        val agentKey = extractAgentKey(call, profile)
        val apiKey = serverKey ?: agentKey
        if (apiKey == null) {
            call.respond(HttpStatusCode.Unauthorized, "No API key available for provider: $providerName (neither server-configured nor provided by agent)")
            return
        }

        val baseUrl = providerConfig?.baseUrl ?: profile.defaultBaseUrl
        val upstreamUrl = "$baseUrl/$subPath"
        val timeoutMs = ((providerConfig?.timeoutSeconds ?: config.requestTimeoutSeconds) * 1000)
        val hasBody = call.request.httpMethod in METHODS_WITH_BODY

        if (hasBody) {
            val declaredLength = call.request.contentLength()
            if (declaredLength != null && declaredLength > MAX_REQUEST_BODY_BYTES) {
                call.respond(HttpStatusCode.PayloadTooLarge, "Request body exceeds ${MAX_REQUEST_BODY_BYTES / 1024 / 1024} MB limit")
                return
            }
        }

        val requestBody = if (hasBody) call.receiveText() else ""

        if (hasBody && requestBody.encodeToByteArray().size > MAX_REQUEST_BODY_BYTES) {
            call.respond(HttpStatusCode.PayloadTooLarge, "Request body exceeds ${MAX_REQUEST_BODY_BYTES / 1024 / 1024} MB limit")
            return
        }

        val requestJson = if (hasBody) tryParseJson(requestBody) else null
        val isStreaming = requestJson?.get("stream")?.jsonPrimitive?.booleanOrNull == true
        val model = requestJson?.get("model")?.jsonPrimitive?.content

        if (profile == LlmProviderProfile.MOCK) {
            val startTime = System.currentTimeMillis()
            val mock = MockLlmProvider.generate(model, isStreaming)
            call.response.header("x-coral-mock", "true")
            call.respondText(mock.body, mock.contentType, HttpStatusCode.OK)
            val durationMs = System.currentTimeMillis() - startTime
            emitTelemetryEvent(agent, "mock", model ?: "mock-model", mock.inputTokens, mock.outputTokens, durationMs, isStreaming, true, null)
            return
        }

        val startTime = System.currentTimeMillis()

        val authSource = if (serverKey != null) "server" else "agent"
        agent.logger.info { "LLM Proxy → $providerName/$subPath model=$model streaming=$isStreaming auth=$authSource" }

        try {
            if (isStreaming) {
                proxyStreaming(agent, profile, apiKey, upstreamUrl, timeoutMs, requestBody, model, hasBody, call, startTime)
            } else {
                proxyBuffered(agent, profile, apiKey, upstreamUrl, timeoutMs, requestBody, model, hasBody, call, startTime)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val durationMs = System.currentTimeMillis() - startTime
            agent.logger.error(e) { "LLM Proxy error: $providerName/$subPath after ${durationMs}ms — ${e.message}" }
            emitTelemetryEvent(agent, providerName, model, null, null, durationMs, false, false, classifyError(e))
            if (!call.response.isCommitted) {
                call.respond(HttpStatusCode.BadGateway, "Proxy error: ${e.message}")
            }
        }
    }

    private suspend fun proxyBuffered(
        agent: SessionAgent,
        profile: LlmProviderProfile,
        apiKey: String,
        upstreamUrl: String,
        timeoutMs: Long,
        requestBody: String,
        model: String?,
        hasBody: Boolean,
        call: ApplicationCall,
        startTime: Long
    ) {
        val response = httpClient.request(upstreamUrl) {
            method = call.request.httpMethod
            timeout { requestTimeoutMillis = timeoutMs }
            applyAuth(profile, apiKey)
            applyDefaultHeaders(profile)
            forwardHeaders(call, profile)
            if (hasBody) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
        }

        val contentLength = response.contentLength()
        if (contentLength != null && contentLength > MAX_RESPONSE_BODY_BYTES) {
            val durationMs = System.currentTimeMillis() - startTime
            call.respond(HttpStatusCode.BadGateway, "Upstream response too large: $contentLength bytes")
            emitTelemetryEvent(agent, profile.providerId, model, null, null, durationMs, false, false, "response_too_large")
            return
        }

        val responseBody = readBoundedBody(response)
        val durationMs = System.currentTimeMillis() - startTime

        val upstreamContentType = response.contentType() ?: ContentType.Application.Json

        for ((name, values) in response.headers.entries()) {
            if (name.lowercase() in STRIP_RESPONSE_HEADERS) continue
            values.forEach { call.response.header(name, it) }
        }
        call.respondText(responseBody, upstreamContentType, response.status)

        val errorKind = if (response.status.isSuccess()) null else classifyHttpError(response.status)
        val (inputTokens, outputTokens) = extractTokenUsage(responseBody)

        if (response.status.isSuccess()) {
            val tokenInfo = if (inputTokens != null || outputTokens != null) " tokens=${inputTokens ?: "?"}→${outputTokens ?: "?"}" else ""
            agent.logger.info { "LLM Proxy ← ${response.status.value} ${durationMs}ms$tokenInfo" }
        } else {
            agent.logger.warn { "LLM Proxy ← ${response.status.value} ${durationMs}ms error=$errorKind" }
        }

        emitTelemetryEvent(
            agent, profile.providerId, model,
            inputTokens, outputTokens, durationMs, false, response.status.isSuccess(), errorKind
        )
    }

    private suspend fun proxyStreaming(
        agent: SessionAgent,
        profile: LlmProviderProfile,
        apiKey: String,
        upstreamUrl: String,
        timeoutMs: Long,
        requestBody: String,
        model: String?,
        hasBody: Boolean,
        call: ApplicationCall,
        startTime: Long
    ) {
        httpClient.prepareRequest(upstreamUrl) {
            method = call.request.httpMethod
            timeout {
                requestTimeoutMillis = timeoutMs
                socketTimeoutMillis = timeoutMs
            }
            applyAuth(profile, apiKey)
            applyDefaultHeaders(profile)
            forwardHeaders(call, profile)
            if (hasBody) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
        }.execute { response ->
            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                val durationMs = System.currentTimeMillis() - startTime
                val errorKind = classifyHttpError(response.status)
                agent.logger.warn { "LLM Proxy ← ${response.status.value} ${durationMs}ms error=$errorKind (streaming)" }
                val upstreamContentType = response.contentType() ?: ContentType.Application.Json
                call.respondText(errorBody, upstreamContentType, response.status)
                emitTelemetryEvent(
                    agent, profile.providerId, model,
                    null, null, durationMs, true, false, errorKind
                )
                return@execute
            }

            call.response.header(HttpHeaders.ContentType, ContentType.Text.EventStream.toString())
            call.response.header(HttpHeaders.CacheControl, "no-store")
            call.response.header("X-Accel-Buffering", "no")

            call.respondTextWriter {
                val channel = response.bodyAsChannel()

                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: break
                    write(line)
                    write("\n")
                    flush()
                }

                val durationMs = System.currentTimeMillis() - startTime
                agent.logger.info { "LLM Proxy ← stream complete ${durationMs}ms" }
                emitTelemetryEvent(
                    agent, profile.providerId, model,
                    null, null, durationMs, true, true, null
                )
            }
        }
    }

    private fun HttpRequestBuilder.applyAuth(profile: LlmProviderProfile, apiKey: String) {
        when (profile.authStyle) {
            is AuthStyle.Bearer -> header(HttpHeaders.Authorization, "Bearer $apiKey")
            is AuthStyle.Custom -> header(profile.authStyle.headerName, apiKey)
        }
    }

    private fun HttpRequestBuilder.applyDefaultHeaders(profile: LlmProviderProfile) {
        profile.defaultHeaders.forEach { (name, value) ->
            header(name, value)
        }
    }

    private fun HttpRequestBuilder.forwardHeaders(call: ApplicationCall, profile: LlmProviderProfile) {
        val defaultHeadersLower = profile.defaultHeaders.keys.map { it.lowercase() }.toSet()

        for ((name, values) in call.request.headers.entries()) {
            val nameLower = name.lowercase()
            if (nameLower in STRIP_REQUEST_HEADERS) continue
            if (nameLower in defaultHeadersLower) continue
            values.forEach { header(name, it) }
        }
    }

    private fun tryParseJson(body: String): JsonObject? {
        return try {
            json.decodeFromString<JsonObject>(body)
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun readBoundedBody(response: HttpResponse): String {
        val channel = response.bodyAsChannel()
        val buffer = StringBuilder()
        var totalBytes = 0L

        while (!channel.isClosedForRead) {
            val line = channel.readUTF8Line() ?: break
            totalBytes += line.encodeToByteArray().size + 1
            if (totalBytes > MAX_RESPONSE_BODY_BYTES) {
                channel.cancel()
                throw LlmProxyException("Upstream response exceeds ${MAX_RESPONSE_BODY_BYTES / 1024 / 1024} MB limit")
            }
            buffer.appendLine(line)
        }

        return buffer.toString().trimEnd()
    }

    private fun extractAgentKey(call: ApplicationCall, profile: LlmProviderProfile): String? {
        return when (profile.authStyle) {
            is AuthStyle.Bearer -> {
                val authHeader = call.request.headers[HttpHeaders.Authorization] ?: return null
                if (authHeader.startsWith("Bearer ", ignoreCase = true)) {
                    authHeader.substring(7).trim().ifEmpty { null }
                } else null
            }
            is AuthStyle.Custom -> {
                call.request.headers[profile.authStyle.headerName]?.trim()?.ifEmpty { null }
            }
        }
    }

    private fun extractTokenUsage(responseBody: String): Pair<Long?, Long?> {
        return try {
            val obj = json.decodeFromString<JsonObject>(responseBody)
            val usage = obj["usage"] as? JsonObject ?: return null to null
            val input = usage["prompt_tokens"]?.jsonPrimitive?.longOrNull
                ?: usage["input_tokens"]?.jsonPrimitive?.longOrNull
            val output = usage["completion_tokens"]?.jsonPrimitive?.longOrNull
                ?: usage["output_tokens"]?.jsonPrimitive?.longOrNull
            input to output
        } catch (_: Exception) {
            null to null
        }
    }

    private fun classifyHttpError(status: HttpStatusCode): String = when {
        status.value == 429 -> "rate_limited"
        status.value in 401..403 -> "credentials"
        status.value in 500..599 -> "upstream_health"
        status.value in 400..499 -> "request_error"
        else -> "unknown"
    }

    private fun classifyError(e: Exception): String = when (e) {
        is ConnectTimeoutException -> "connectivity"
        is HttpRequestTimeoutException -> "connectivity"
        is LlmProxyException -> "response_too_large"
        else -> if (e.message?.contains("timeout", ignoreCase = true) == true) "connectivity" else "unknown"
    }

    private suspend fun emitTelemetryEvent(
        agent: SessionAgent,
        provider: String,
        model: String?,
        inputTokens: Long?,
        outputTokens: Long?,
        durationMs: Long,
        streaming: Boolean,
        success: Boolean,
        errorKind: String?
    ) {
        try {
            agent.session.events.emit(
                SessionEvent.LlmProxyCall(
                    agentName = agent.name,
                    provider = provider,
                    model = model,
                    inputTokens = inputTokens,
                    outputTokens = outputTokens,
                    durationMs = durationMs,
                    streaming = streaming,
                    success = success,
                    errorKind = errorKind
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to emit LLM proxy telemetry event" }
        }
    }
}
