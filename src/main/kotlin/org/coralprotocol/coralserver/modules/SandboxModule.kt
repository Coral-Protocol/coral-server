package org.coralprotocol.coralserver.modules

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import org.coralprotocol.coralserver.cloud.CloudProvisionClient
import org.coralprotocol.coralserver.cloud.SandboxProvider
import org.koin.core.qualifier.named
import org.koin.dsl.module

const val SANDBOX_HTTP_CLIENT = "sandboxHttpClient"

val sandboxModule = module {
    single(named(SANDBOX_HTTP_CLIENT)) {
        HttpClient(CIO) {
            install(ContentNegotiation) { json(get()) }
            install(HttpRequestRetry) {
                maxRetries = 3
                retryIf { _, response -> response.status.value in 500..599 }
                exponentialDelay()
            }
        }
    }
    single<SandboxProvider> {
        CloudProvisionClient(
            httpClient = get(named(SANDBOX_HTTP_CLIENT)),
            sandboxConfig = get(),
            cloudConfig = get(),
        )
    }
}
