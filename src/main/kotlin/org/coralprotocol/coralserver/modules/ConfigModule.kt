package org.coralprotocol.coralserver.modules

import com.sksamuel.hoplite.*
import org.coralprotocol.coralserver.config.*
import org.koin.dsl.module

@OptIn(ExperimentalHoplite::class)
val configModule = module {
    single {
        val loader = ConfigLoaderBuilder.default()
            .addResourceSource("/config.toml")
            .withExplicitSealedTypes("type")
            .addEnvironmentSource()

        val path = System.getenv("CONFIG_FILE_PATH")
        if (path != null)
            loader.addFileSource(path)

        val config = loader.build().loadConfigOrThrow<RootConfig>()
        config
    }

    single<AuthConfig> { get<RootConfig>().authConfig }
    single<CacheConfig> { get<RootConfig>().cacheConfig }
    single<DebugConfig> { get<RootConfig>().debugConfig }
    single<DockerConfig> { get<RootConfig>().dockerConfig }
    single<NetworkConfig> { get<RootConfig>().networkConfig }
    single<PaymentConfig> { get<RootConfig>().paymentConfig }
    single<RegistryConfig> { get<RootConfig>().registryConfig }
    single<SecurityConfig> { get<RootConfig>().securityConfig }
    single<SessionConfig> { get<RootConfig>().sessionConfig }
}