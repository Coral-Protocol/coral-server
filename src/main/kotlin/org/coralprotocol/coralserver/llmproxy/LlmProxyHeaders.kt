package org.coralprotocol.coralserver.llmproxy

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

object LlmProxyHeaders {
    private val HOP_BY_HOP = setOf(
        HttpHeaders.Connection,
        HttpHeaders.TransferEncoding,
        HttpHeaders.Upgrade,
        "keep-alive",
        "proxy-authenticate",
        "proxy-authorization",
        "te",
        "trailer",
    )

    private val STRIP_REQUEST = (HOP_BY_HOP + setOf(
        HttpHeaders.Authorization,
        HttpHeaders.Host,
        HttpHeaders.ContentLength,
        HttpHeaders.ContentType,
        HttpHeaders.AcceptEncoding,
        HttpHeaders.Cookie,
        "x-api-key",
    )).map { it.lowercase() }.toSet()

    private val STRIP_RESPONSE = (HOP_BY_HOP + setOf(
        HttpHeaders.ContentLength,
        HttpHeaders.ContentEncoding,
        HttpHeaders.SetCookie,
    )).map { it.lowercase() }.toSet()

    fun applyUpstream(builder: HttpRequestBuilder, call: ApplicationCall, request: LlmProxyRequest) {
        val providerConfig = request.model.providerConfig
        when (val authStyle = providerConfig.format.authStyle) {
            is LlmProviderAuthStyle.Bearer -> builder.bearerAuth(providerConfig.apiKey)
            is LlmProviderAuthStyle.Custom -> builder.header(authStyle.headerName, providerConfig.apiKey)
        }

        providerConfig.format.defaultHeaders.forEach { (name, value) -> builder.header(name, value) }

        val defaultLower = providerConfig.format.defaultHeaders.keys.map { it.lowercase() }.toSet()
        for ((name, values) in call.request.headers.entries()) {
            val lower = name.lowercase()
            if (lower in STRIP_REQUEST || lower in defaultLower) continue
            values.forEach { builder.header(name, it) }
        }
    }

    fun forwardResponseHeaders(from: HttpResponse, call: ApplicationCall) {
        for ((name, values) in from.headers.entries()) {
            if (name.lowercase() in STRIP_RESPONSE) continue
            values.forEach { call.response.header(name, it) }
        }
    }

    fun extractAgentKey(call: ApplicationCall, request: LlmProxyRequest): String? {
        return when (val authStyle = request.model.providerConfig.format.authStyle) {
            is LlmProviderAuthStyle.Bearer -> {
                val authHeader = call.request.headers[HttpHeaders.Authorization] ?: return null
                if (authHeader.startsWith("Bearer ", ignoreCase = true)) {
                    authHeader.substring(7).trim().ifEmpty { null }
                } else null
            }

            is LlmProviderAuthStyle.Custom -> {
                call.request.headers[authStyle.headerName]?.trim()?.ifEmpty { null }
            }
        }
    }
}
