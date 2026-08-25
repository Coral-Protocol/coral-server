package org.coralprotocol.coralserver.agent.runtime

import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import com.github.dockerjava.transport.DockerHttpClient
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.resources.serialization.*
import org.coralprotocol.coralserver.config.AddressConsumer
import org.coralprotocol.coralserver.config.RootConfig
import org.coralprotocol.coralserver.logging.Logger
import org.coralprotocol.coralserver.mcp.McpTransportType
import org.coralprotocol.coralserver.modules.LOGGER_CONFIG
import org.coralprotocol.coralserver.routes.mcp.v1.Sse
import org.coralprotocol.coralserver.routes.mcp.v1.StreamableHttp
import org.coralprotocol.coralserver.session.SessionAgentExecutionContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import java.time.Duration

class ApplicationRuntimeContext(
    private val config: RootConfig,
) : KoinComponent {
    private val logger by inject<Logger>(named(LOGGER_CONFIG))

    val dockerClient = run {
        try {
            val dockerClientConfig: DockerClientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(config.dockerConfig.socket)
                .build()

            val httpClient: DockerHttpClient = ApacheDockerHttpClient.Builder()
                .dockerHost(dockerClientConfig.dockerHost)
                .sslConfig(dockerClientConfig.sslConfig)
                .responseTimeout(Duration.ofSeconds(config.dockerConfig.responseTimeout))
                .connectionTimeout(Duration.ofSeconds(config.dockerConfig.connectionTimeout))
                .maxConnections(config.dockerConfig.maxConnections)
                .build()

            DockerClientImpl.getInstance(dockerClientConfig, httpClient)
        } catch (e: Exception) {
            logger.error(e) { "Failed to create Docker client" }
            logger.warn { "Docker runtime will not be available" }
            null
        }
    }

    fun getApiUrl(addressConsumer: AddressConsumer): Url {
        return config.resolveBaseUrl(addressConsumer)
    }

    fun getSseUrl(executionContext: SessionAgentExecutionContext, addressConsumer: AddressConsumer): Url {
        val base = getApiUrl(addressConsumer)
        val builder = URLBuilder(base)
        href(ResourcesFormat(), Sse(agentSecret = executionContext.agent.secret), builder)

        return builder.prependBasePath(base).build()
    }

    fun getStreamableHttpUrl(executionContext: SessionAgentExecutionContext, addressConsumer: AddressConsumer): Url {
        val base = getApiUrl(addressConsumer)
        val builder = URLBuilder(base)
        href(ResourcesFormat(), StreamableHttp(agentSecret = executionContext.agent.secret), builder)

        return builder.prependBasePath(base).build()
    }

    fun getLlmProxyUrl(
        executionContext: SessionAgentExecutionContext,
        addressConsumer: AddressConsumer,
        proxyName: String
    ): Url {
        val builder = URLBuilder(getApiUrl(addressConsumer))
        builder.appendPathSegments("llm-proxy", executionContext.agent.secret)
        builder.appendPathSegments(proxyName)
        return builder.build()
    }

    fun getMcpUrl(
        transport: McpTransportType,
        executionContext: SessionAgentExecutionContext,
        addressConsumer: AddressConsumer
    ) =
        when (transport) {
            McpTransportType.SSE -> getSseUrl(executionContext, addressConsumer)
            McpTransportType.STREAMABLE_HTTP -> getStreamableHttpUrl(executionContext, addressConsumer)
        }
}

/**
 * `href` overwrites the builder's path with the resource path, dropping any base path (e.g. cloud's
 * `/sandbox` gateway prefix). Re-prepend the base path so a resource URL composes with a based
 * gateway URL. No-op when the base has no path (the local/container consumers).
 */
internal fun URLBuilder.prependBasePath(base: Url): URLBuilder {
    val basePath = base.encodedPath.trimEnd('/')
    if (basePath.isNotEmpty()) encodedPath = "$basePath/${encodedPath.trimStart('/')}"
    return this
}