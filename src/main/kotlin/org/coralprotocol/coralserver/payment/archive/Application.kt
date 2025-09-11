//package org.coralprotocol.coralserver.payment
//
//import org.coralprotocol.coralserver.escrow.blockchain.BlockchainService
//import org.coralprotocol.coralserver.escrow.blockchain.BlockchainServiceImpl
//import org.coralprotocol.coralserver.payment.api.configureRouting
//import org.coralprotocol.coralserver.payment.config.PaymentServerConfig
//import com.sksamuel.hoplite.ConfigLoaderBuilder
//import com.sksamuel.hoplite.addResourceSource
//import io.ktor.serialization.kotlinx.json.*
//import io.ktor.server.application.*
//import io.ktor.server.engine.*
//import io.ktor.server.netty.*
//import io.ktor.server.plugins.callloging.*
//import io.ktor.server.plugins.contentnegotiation.*
//import io.ktor.server.plugins.statuspages.*
//import io.ktor.server.response.*
//import io.ktor.http.*
//import kotlinx.serialization.json.Json
//import io.github.oshai.kotlinlogging.KotlinLogging
//import org.coralprotocol.coralserver.payment.models.ErrorResponse
//import org.coralprotocol.coralserver.payment.config.SignerConfigDecoder
//import kotlin.system.exitProcess
//
//private val logger = KotlinLogging.logger {}
//
//fun main(args: Array<String>) {
//    // Load configuration
//    val configPath = System.getenv("CONFIG_PATH") ?: "/application.yaml"
//    val config = try {
//        ConfigLoaderBuilder.default()
//            .addDecoder(SignerConfigDecoder())
//            .addResourceSource(configPath)
//            .build()
//            .loadConfigOrThrow<PaymentServerConfig>()
//    } catch (e: Exception) {
//        logger.error { "Failed to load configuration from $configPath: ${e.message}" }
//        throw e
//    }
//
//    // Initialize blockchain service
//    val blockchainService = BlockchainServiceImpl(
//        rpcUrl = config.blockchain.rpcUrl,
//        commitment = config.blockchain.commitment,
//        signerConfig = config.blockchain.signer
//    )
//
//    // Log startup info based on what's configured
//    val mode = when {
//        config.app != null && config.agent != null -> "HYBRID (App + Agent)"
//        config.app != null -> "APP"
//        config.agent != null -> "AGENT"
//        else -> "COMMON-ONLY"
//    }
//
//    // Register shutdown hook for cleanup
//    Runtime.getRuntime().addShutdownHook(Thread {
//        logger.info { "Shutting down Payment API..." }
//        try {
//            //todo
//            //blockchainService.close()
//            logger.info { "Resources cleaned up successfully" }
//        } catch (e: Exception) {
//            logger.error(e) { "Error during shutdown cleanup" }
//        }
//    })
//
//    // Start server
//    logger.info { "Starting Payment API on port ${config.server.port} in $mode mode" }
//    embeddedServer(Netty, port = config.server.port) {
//        configureServer(blockchainService, config)
//    }.start(wait = true)
//}
//
//fun Application.configureServer(
//    blockchainService: BlockchainService,
//    config: PaymentServerConfig
//) {
//    install(ContentNegotiation) {
//        json(Json {
//            prettyPrint = true
//            ignoreUnknownKeys = true
//        })
//    }
//
//    install(CallLogging) {
//        level = org.slf4j.event.Level.INFO
//    }
//
//    install(StatusPages) {
//        exception<IllegalArgumentException> { call, cause ->
//            // TODO: Sanitize error messages before production to avoid information leak
//            call.respond(HttpStatusCode.BadRequest, ErrorResponse(
//                error = cause.message ?: "Invalid request"
//            ))
//        }
//        exception<Throwable> { call, cause ->
//            logger.error(cause) { "Unhandled exception" }
//            // TODO: Replace with generic error message in production to avoid information leak
//            // For now, returning "Internal server error" without details
//            call.respond(HttpStatusCode.InternalServerError, ErrorResponse(
//                error = "Internal server error"
//            ))
//        }
//    }
//
//    configureRouting(blockchainService, config)
//}