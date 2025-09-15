package org.coralprotocol.coralserver.routes.api.v1

import io.github.smiley4.ktoropenapi.resources.get
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
    val mode: String,
    val blockchain: BlockchainHealth
)

@Serializable
data class BlockchainHealth(
    val connected: Boolean,
    val rpcUrl: String,
    val lastBlock: Long? = null
)

@Resource("/health")
class Health

@Resource("/health/detailed")
class HealthDetailed

fun Route.healthRoutes() {
    get<Health> {
        call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
    }

    get<HealthDetailed> {
        // TODO: Add actual blockchain connectivity check
        val health = HealthResponse(
            status = "healthy",
            mode = call.application.environment.config.property("server.mode").getString(),
            blockchain = BlockchainHealth(
                connected = true, // Would need actual check
                rpcUrl = call.application.environment.config.property("blockchain.rpc_url").getString(),
                lastBlock = null // Would need actual query
            )
        )
        call.respond(health)
    }
}