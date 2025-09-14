package org.coralprotocol.coralserver.server

import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class RouteException(
    @Transient
    val status: HttpStatusCode? = null,

    @Suppress("unused")
    @SerialName("message")
    val routeExceptionMessage: String?) : Exception(routeExceptionMessage)
{
    init {
        printStackTrace()
    }
    @Suppress("unused")
    val stackTrace = super.stackTrace.map { it.toString() }.also { printStackTrace() }
}
