package org.coralprotocol.coralserver.session

import com.chrynan.uri.core.UriString
import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import net.pwall.json.schema.JSONSchema
import org.coralprotocol.coralserver.models.Agent
import org.coralprotocol.coralserver.orchestrator.AgentType
import org.coralprotocol.coralserver.orchestrator.runtime.AgentRuntime
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp
import java.net.URI
import java.net.URISyntaxException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse


private val logger = KotlinLogging.logger {}

/**
 * Data class for session creation request.
 */
@Serializable
data class CreateSessionRequest(
    val applicationId: String,
    val sessionId: String? = null,
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

fun CoralAgentIndividualMcp.addExtraTool(sessionId: String, agentId: String, tool: CustomTool) {
    addTool(
        name = tool.toolSchema.name,
        description = tool.toolSchema.description ?: "",
        inputSchema = tool.toolSchema.inputSchema,
    ) {
        request -> tool.transport.handleRequest(sessionId, agentId, request, tool.toolSchema)
    }
}


@Serializable
sealed interface ToolTransport {
    @SerialName("http")
    @Serializable
    data class Http(val url: UriString): ToolTransport {
        override suspend fun handleRequest(sessionId: String, agentId: String, request: CallToolRequest, toolSchema: Tool): CallToolResult {
            try {
                val client = HttpClient.newBuilder().build();
                // TODO (alan): maybe validate body schema before passing it on,
                // probably not worth double validating though
                val req = HttpRequest.newBuilder()
                    .POST(HttpRequest.BodyPublishers.ofString(Json.encodeToString(request.arguments)))
                    .uri(URI(url.value).resolve("./$sessionId/$agentId"))
                    .header("Content-Type", "application/json")
                    .build();

                // TODO: better thread context
                val response = withContext(Dispatchers.IO) {
                    client.send(req, HttpResponse.BodyHandlers.ofString())
                };
                val body = response.body()
                return CallToolResult(
                    content = listOf(TextContent(body))
                )
            } catch (ex: Exception) {
                logger.error { ex }
                return CallToolResult(
                    isError = true,
                    content = listOf(TextContent("Error: $ex"))
                )
            }
        }
    }

    suspend fun handleRequest(sessionId: String, agentId: String, request: CallToolRequest, toolSchema: Tool): CallToolResult
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