package org.coralprotocol.coralserver.modules

import com.sksamuel.hoplite.*
import com.sksamuel.hoplite.decoder.Decoder
import com.sksamuel.hoplite.fp.Validated
import com.sksamuel.hoplite.fp.invalid
import com.sksamuel.hoplite.fp.valid
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.config.*
import org.koin.dsl.module
import kotlin.reflect.KType

private object RuntimeIdDecoder : Decoder<RuntimeId> {
    override fun supports(type: KType): Boolean = type.classifier == RuntimeId::class
    override fun priority(): Int = 1
    override fun decode(node: Node, type: KType, context: DecoderContext): Validated<ConfigFailure, RuntimeId> {
        if (node !is StringNode) return ConfigFailure.DecodeError(node, type).invalid()
        return RuntimeId.values().firstOrNull { it.name.equals(node.value, ignoreCase = true) }
            ?.valid()
            ?: ConfigFailure.InvalidEnumConstant(node, type, node.value).invalid()
    }
}

@OptIn(ExperimentalHoplite::class)
val configModule = module {
    single(createdAtStart = true) {
        val loader = ConfigLoaderBuilder.default()
            .addDecoder(RuntimeIdDecoder)
            .addCommandLineSource(getOrNull<CommandLineArgs>()?.values ?: emptyArray())
            .addResourceSource("/config.toml", optional = true)
            .withExplicitSealedTypes("type")
            .addEnvironmentSource()

        val path = System.getenv("CONFIG_FILE_PATH")
        if (path != null)
            loader.addFileSource(path)

        loader.build().loadConfigOrThrow<RootConfig>()
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
    single<LlmProxyConfig>(createdAtStart = true) { get<RootConfig>().llmProxyConfig }
    single<CloudConfig>(createdAtStart = true) { get<RootConfig>().cloudConfig }
    single<ExecutionPolicyConfig>(createdAtStart = true) { get<RootConfig>().executionPolicyConfig }
    single<OpenShellConfig>(createdAtStart = true) { get<RootConfig>().openShellConfig }
}