package org.coralprotocol.coralserver.session

import com.chrynan.uri.core.Uri
import com.chrynan.uri.core.UriString
import io.ktor.http.*
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import net.pwall.json.schema.JSONSchema
import org.coralprotocol.coralserver.orchestrator.AgentType
import org.coralprotocol.coralserver.orchestrator.runtime.AgentRuntime
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp

/**
 * Data class for session creation request.
 */
@Serializable
data class CreateSessionRequest(
    val applicationId: String,
    val sessionId: String?,
    val privacyKey: String,
    val agentGraph: AgentGraphRequest?,
)

@Serializable
data class AgentGraphRequest(
    val agents: HashMap<AgentName, GraphAgentRequest>,
    val links: Set<Set<String>>,
    val tools: HashMap<String, CustomTool>,
)

object JSONSchemaSerializer : KSerializer<JSONSchemaWithRaw> {
    // Serial names of descriptors should be unique, so choose app-specific name in case some library also would declare a serializer for Date.
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("org.coralprotocol.JSONSchemaWithRaw") {
        
    }
    override fun serialize(encoder: Encoder, value: JSONSchemaWithRaw) {
        val json = encoder as? JsonEncoder ?: throw SerializationException("Can be serialized only as JSON")
        return json.encodeJsonElement(value.raw)
    }
    override fun deserialize(decoder: Decoder): JSONSchemaWithRaw {
        val jsonInput = decoder as? JsonDecoder ?: error("Can be deserialized only by JSON")
        val obj = jsonInput.decodeJsonElement().jsonObject;

        return JSONSchemaWithRaw(schema = JSONSchema.parse(obj.toString()), raw = obj)
    }
}

@Serializable(with = JSONSchemaSerializer::class)
data class JSONSchemaWithRaw(
    val schema: JSONSchema,
    val raw: JsonObject,
)

@Serializable
data class CustomTool(
    val transport: ToolTransport,
    val toolSchema: Tool,
)

fun CoralAgentIndividualMcp.addExtraTool(tool: CustomTool) {
    addTool(
        name = tool.toolSchema.name,
        description = tool.toolSchema.description ?: "",
        inputSchema = tool.toolSchema.inputSchema,
    ) {
        request -> tool.transport.handleRequest(request)
    }
}

@Serializable
sealed interface ToolTransport {
    @SerialName("http")
    @Serializable
    data class Http(val url: UriString): ToolTransport {
        override suspend fun handleRequest(request: CallToolRequest): CallToolResult {
            return CallToolResult(
                content = listOf(TextContent("the weather is a sunny 18 degrees, with light showers throughout"))
            )
        }
    }

    suspend fun handleRequest(request: CallToolRequest): CallToolResult
}

@Serializable
sealed interface GraphAgentRequest {
    val options: Map<String, JsonPrimitive>
    val blocking: Boolean?
    val tools: Set<String>

    @Serializable
    @SerialName("remote")
    data class Remote(val remote: AgentRuntime.Remote, override val options: Map<String, JsonPrimitive> = mapOf(), override val tools: Set<String> = setOf(), override val blocking: Boolean? = true) :
        GraphAgentRequest

    @Serializable
    @SerialName("local")
    data class Local(val agentType: AgentType, override val options: Map<String, JsonPrimitive> = mapOf(), override val tools: Set<String> = setOf(), override val blocking: Boolean? = true) :
        GraphAgentRequest
}

/**
 * Data class for session creation response.
 */
@Serializable
data class CreateSessionResponse(
    val sessionId: String,
    val applicationId: String,
    val privacyKey: String
)