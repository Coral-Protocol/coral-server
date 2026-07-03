package org.coralprotocol.coralserver.modules

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import org.coralprotocol.coralserver.cloud.CloudProvisionClient
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.io.IOException

const val SANDBOX_HTTP_CLIENT = "sandboxHttpClient"

val sandboxModule = module {
    single(named(SANDBOX_HTTP_CLIENT)) {
        HttpClient(CIO) {
            install(ContentNegotiation) { json(get()) }
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 60_000
                socketTimeoutMillis = 60_000
            }
            install(HttpRequestRetry) {
                // /provision is idempotent (cloud keys on coral_session+agent_name), so retrying a
                // transient 5xx or connection failure won't create duplicate machines.
                maxRetries = 3
                retryIf { _, response -> response.status.value in 500..599 }
                retryOnExceptionIf { _, cause -> cause is IOException }
                exponentialDelay()
            }
        }
    }
    single {
        CloudProvisionClient(
            httpClient = get(named(SANDBOX_HTTP_CLIENT)),
            sandboxConfig = get(),
            cloudConfig = get(),
        )
    }
}
