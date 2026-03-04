package org.coralprotocol.coralserver.routes

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class RouteException(
    @Transient
    val status: HttpStatusCode = HttpStatusCode.Companion.InternalServerError,

    @Transient
    val parentException: Throwable? = null,
) : Exception(parentException) {
    constructor(status: HttpStatusCode, message: String) : this(status, Exception(message))

    // TODO: can maybe be brought back in development mode
    @Suppress("unused")
    @Transient
    val stackTrace = super.stackTrace.map { it.toString() }

    override val message = parentException?.message ?: "Unknown error"
}