package org.coralprotocol.coralserver.server

import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class RouteException(
    @Transient
    val status: HttpStatusCode = throw IllegalArgumentException("class cannot be deserialized"),

    @Suppress("unused")
    @SerialName("message")
    val routeExceptionMessage: String?) : Exception(routeExceptionMessage)
{
    @Suppress("unused")
    val stackTrace = super.stackTrace.map { it.toString() }
}