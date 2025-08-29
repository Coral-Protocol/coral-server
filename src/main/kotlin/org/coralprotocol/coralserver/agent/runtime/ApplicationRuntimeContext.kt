package org.coralprotocol.coralserver.agent.runtime

import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import com.github.dockerjava.transport.DockerHttpClient
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import org.coralprotocol.coralserver.config.AddressConsumer
import org.coralprotocol.coralserver.config.ConfigCollection
import java.time.Duration

class ApplicationRuntimeContext(
    val app: ConfigCollection
) {
    private val dockerClientConfig: DockerClientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
        .withDockerHost(app.config.docker.socket)
        .build()

    var httpClient: DockerHttpClient = ApacheDockerHttpClient.Builder()
        .dockerHost(dockerClientConfig.dockerHost)
        .sslConfig(dockerClientConfig.sslConfig)
        .responseTimeout(Duration.ofSeconds(app.config.docker.responseTimeout))
        .connectionTimeout(Duration.ofSeconds(app.config.docker.connectionTimeout))
        .maxConnections(app.config.docker.maxConnections)
        .build()

    val dockerClient = DockerClientImpl.getInstance(dockerClientConfig, httpClient) ?:
        throw IllegalStateException("Failed to initialize Docker client")

    fun getApiUrl(addressConsumer: AddressConsumer): Url {
        return app.config.resolveBaseUrl(addressConsumer)
    }

    fun getMcpUrl(params: RuntimeParams, addressConsumer: AddressConsumer): Url {
        val builder = URLBuilder(getApiUrl(addressConsumer))
        builder.parameters.append("agentId", params.agentName)
        builder.pathSegments = listOf(
            "sse",
            "v1",
            params.applicationId,
            params.privacyKey,
            params.sessionId,
            "sse" // some libraries identify SSE by the presence of this path segment at the end :(
        )

        return builder.build()
    }
}