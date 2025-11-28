package org.coralprotocol.coralserver.server

import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class RouteException(
    @Transient
    val status: HttpStatusCode? = null,

    @Transient
    val parentException: Throwable? = null,
    ) : Exception(parentException)
{
    constructor(status: HttpStatusCode, message: String) : this(status, Exception(message))

    @Suppress("unused")
    val stackTrace = super.stackTrace.map { it.toString() }
}
