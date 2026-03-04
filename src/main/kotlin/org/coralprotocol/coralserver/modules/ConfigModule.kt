package org.coralprotocol.coralserver.modules

import com.sksamuel.hoplite.*
import org.coralprotocol.coralserver.config.*
import org.koin.dsl.module

@OptIn(ExperimentalHoplite::class)
val configModule = module {
    single(createdAtStart = true) {
        val loader = ConfigLoaderBuilder.default()
            .addResourceSource("/config.toml", optional = true)
            .withExplicitSealedTypes("type")
            .addEnvironmentSource()

        val path = System.getenv("CONFIG_FILE_PATH")
        if (path != null)
            loader.addFileSource(path)

        val config = loader.build().loadConfigOrThrow<RootConfig>()
        config
    }
}

val configModuleParts = module {
    single<AuthConfig>(createdAtStart = true) { get<RootConfig>().authConfig }
    single<CacheConfig>(createdAtStart = true) { get<RootConfig>().cacheConfig }
    single<DebugConfig>(createdAtStart = true) { get<RootConfig>().debugConfig }
    single<DockerConfig>(createdAtStart = true) { get<RootConfig>().dockerConfig }
    single<NetworkConfig>(createdAtStart = true) { get<RootConfig>().networkConfig }
    single<PaymentConfig>(createdAtStart = true) { get<RootConfig>().paymentConfig }
    single<RegistryConfig>(createdAtStart = true) { get<RootConfig>().registryConfig }
    single<SecurityConfig>(createdAtStart = true) { get<RootConfig>().securityConfig }
    single<SessionConfig>(createdAtStart = true) { get<RootConfig>().sessionConfig }
    single<LoggingConfig>(createdAtStart = true) { get<RootConfig>().loggingConfig }
    single<ConsoleConfig>(createdAtStart = true) { get<RootConfig>().consoleConfig }
}