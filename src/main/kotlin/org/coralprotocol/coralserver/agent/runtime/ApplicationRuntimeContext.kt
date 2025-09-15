package org.coralprotocol.coralserver.agent.runtime

import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import com.github.dockerjava.transport.DockerHttpClient
import io.ktor.http.*
import org.coralprotocol.coralserver.config.AddressConsumer
import org.coralprotocol.coralserver.config.Config
import java.time.Duration

class ApplicationRuntimeContext(
    val config: Config
) {
    private val dockerClientConfig: DockerClientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
        .withDockerHost(config.dockerConfig.socket)
        .build()

    var httpClient: DockerHttpClient = ApacheDockerHttpClient.Builder()
        .dockerHost(dockerClientConfig.dockerHost)
        .sslConfig(dockerClientConfig.sslConfig)
        .responseTimeout(Duration.ofSeconds(config.dockerConfig.responseTimeout))
        .connectionTimeout(Duration.ofSeconds(config.dockerConfig.connectionTimeout))
        .maxConnections(config.dockerConfig.maxConnections)
        .build()

    val dockerClient = DockerClientImpl.getInstance(dockerClientConfig, httpClient) ?:
        throw IllegalStateException("Failed to initialize Docker client")

    fun getApiUrl(addressConsumer: AddressConsumer): Url {
        // todo: remote!
        return config.resolveBaseUrl(addressConsumer)
    }

    fun getMcpUrl(params: RuntimeParams, addressConsumer: AddressConsumer): Url {
        val builder = URLBuilder(getApiUrl(addressConsumer))
        builder.parameters.append("agentId", params.agentName)

        // some libraries identify SSE by the presence of this path segment at the end :(
        when (params) {
            is RuntimeParams.Local -> {
                builder.pathSegments = listOf(
                    "sse",
                    "v1",
                    params.applicationId,
                    params.privacyKey,
                    params.session.id,
                    "sse"
                )
            }
            is RuntimeParams.Remote -> {
                builder.pathSegments = listOf(
                    "sse",
                    "v1",
                    "export",
                    params.session.id,
                    "sse"
                )
            }
        }

        return builder.build()
    }
}