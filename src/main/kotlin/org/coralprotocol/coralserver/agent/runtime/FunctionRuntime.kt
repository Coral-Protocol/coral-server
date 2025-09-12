package org.coralprotocol.coralserver.agent.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.coralprotocol.coralserver.EventBus

@Serializable(with = FunctionRuntimeSerializer::class)
class FunctionRuntime(
    @Transient
    private val function: suspend (params: RuntimeParams) -> Unit = {}
) : Orchestrate {
    override fun spawn(
        params: RuntimeParams,
        eventBus: EventBus<RuntimeEvent>,
        applicationRuntimeContext: ApplicationRuntimeContext
    ): OrchestratorHandle {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            function(params)
        }

        return object : OrchestratorHandle {
            override suspend fun destroy() {
                scope.cancel()
            }
        }
    }
}
// Plain fixed value serializer to give the serializer something to work with :3
class FunctionRuntimeSerializer : kotlinx.serialization.KSerializer<FunctionRuntime> {
    override val descriptor = kotlinx.serialization.descriptors.PrimitiveSerialDescriptor("FunctionRuntime", kotlinx.serialization.descriptors.PrimitiveKind.STRING)
    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: FunctionRuntime) {
        encoder.encodeString("function")
    }

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): FunctionRuntime {
        decoder.decodeString() // Just to consume the input
        return FunctionRuntime()
    }
}