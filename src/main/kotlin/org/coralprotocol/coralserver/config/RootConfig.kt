@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.config

import com.sksamuel.hoplite.ConfigAlias
import io.ktor.http.*
import kotlinx.serialization.ExperimentalSerializationApi

data class RootConfig(
    @param:ConfigAlias("payment")
    val paymentConfig: PaymentConfig = PaymentConfig(),

    @param:ConfigAlias("network")
    val networkConfig: NetworkConfig = NetworkConfig(),

    @param:ConfigAlias("docker")
    val dockerConfig: DockerConfig = DockerConfig(),

    @param:ConfigAlias("registry")
    val registryConfig: RegistryConfig = RegistryConfig(),

    @param:ConfigAlias("cache")
    val cacheConfig: CacheConfig = CacheConfig(),

    @param:ConfigAlias("security")
    val securityConfig: SecurityConfig = SecurityConfig,

    @param:ConfigAlias("auth")
    val authConfig: AuthConfig = AuthConfig(),

    @param:ConfigAlias("debug")
    val debugConfig: DebugConfig = DebugConfig(),

    @param:ConfigAlias("session")
    val sessionConfig: SessionConfig = SessionConfig(),

    @param:ConfigAlias("logging")
    val loggingConfig: LoggingConfig = LoggingConfig(),

    @param:ConfigAlias("console")
    val consoleConfig: ConsoleConfig = ConsoleConfig(),

    @param:ConfigAlias("llm-proxy")
    val llmProxyConfig: LlmProxyConfig = LlmProxyConfig(),

    @param:ConfigAlias("cloud")
    val cloudConfig: CloudConfig = CloudConfig(),

    @param:ConfigAlias("execution")
    val executionPolicyConfig: ExecutionPolicyConfig = ExecutionPolicyConfig(),

    @param:ConfigAlias("openshell")
    val openShellConfig: OpenShellConfig = OpenShellConfig(),

    @param:ConfigAlias("sandbox")
    val sandboxConfig: SandboxConfig = SandboxConfig(),
) {
    /**
     * Calculates the address required to access the server for a given consumer.
     */
    fun resolveAddress(consumer: AddressConsumer): String {
        return when (consumer) {
            AddressConsumer.EXTERNAL -> networkConfig.externalAddress
            AddressConsumer.CONTAINER -> dockerConfig.address
            AddressConsumer.LOCAL -> "localhost"
        }
    }

    /**
     * Calculates the base URL required to access the server for a given consumer.
     *
     * [AddressConsumer.EXTERNAL] is the off-host sandbox agent: it reaches coral-server through cloud's
     * public gateway (`sandbox.agent_gateway_url`, e.g. `https://cloud/sandbox`), not the local bind
     * port. This deliberately differs from [resolveAddress], which returns the server's own external
     * host — the sandbox agent never talks to that host directly.
     */
    fun resolveBaseUrl(consumer: AddressConsumer): Url =
        when (consumer) {
            AddressConsumer.EXTERNAL ->
                sandboxConfig.agentGatewayUrl?.let { gateway ->
                    runCatching { Url(gateway) }.getOrElse {
                        throw IllegalStateException(
                            "sandbox.agent_gateway_url is not a valid URL: '$gateway'", it
                        )
                    }
                } ?: URLBuilder(
                    protocol = URLProtocol.HTTPS,
                    host = networkConfig.externalAddress,
                    port = 443,
                ).build()

            else -> URLBuilder(
                protocol = URLProtocol.HTTP,
                host = resolveAddress(consumer),
                port = networkConfig.bindPort.toInt()
            ).build()
        }
}

enum class AddressConsumer {
    /**
     * Another computer/server
     */
    EXTERNAL,

    /**
     * A container ran on the same machine as the server
     */
    CONTAINER,

    /**
     * A process running on the same machine as the server
     */
    LOCAL
}