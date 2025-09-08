package org.coralprotocol.coralserver.payment.api

import org.coralprotocol.coralserver.payment.models.BlockchainHealth
import org.coralprotocol.coralserver.payment.models.HealthResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.healthRoutes() {
    get("/health") {
        call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
    }
    
    get("/health/detailed") {
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