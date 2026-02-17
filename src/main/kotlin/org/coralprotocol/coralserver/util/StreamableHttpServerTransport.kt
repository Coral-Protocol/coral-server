package org.coralprotocol.coralserver.util

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.utils.io.*
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import org.coralprotocol.coralserver.logging.LoggingInterface
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration

const val MCP_SESSION_ID_HEADER_NAME = "mcp-session-id"

/**
 * This transport aims to implement the new Streamable HTTP connection.
 *
 * In streamable HTTP:
 * 1. There is one endpoint, usually ending in /mcp that can support GET, POST and DELETE requests
 * 2. A GET request to /mcp will provide an SSE stream similarly to how the old /sse endpoint worked
 * 3. A POST request to /mcp accepts a similar message structure to what was required in the old SSE schema, except this
 *    endpoint should return the responses to the messages that were sent.  The returned responses may be sent as JSON
 *    or as an SSE stream.
 *
 * Streamable HTTP also optionally supports sessions and stream resumability.  These features are optional and are not
 * yet implemented in this transport.
 *
 * NOTE: The spec for streamable HTTP says that messages should not be duplicated across streams.  The only ingress for
 * messages is the POST endpoint.  The POST endpoint is expected to return responses to the messages sent as in the
 * response body of the request.  This means that in a basic MCP server (like Coral's is at the moment) where only tools
 * and resources are used, there is never a reason to open an SSE stream.
 */
class StreamableHttpServerTransport(
    messageQueueCapacity: Int,
    val responseTimeout: Duration,
    val transportSessionId: String,
    val logger: LoggingInterface
) :
    AbstractTransport() {
    /**
     * A map of requests that are waiting for responses.  If a message sent in [send] does not match an item in this
     * map, it will instead be sent to [messageQueue], which must be depleted in a SSE stream
     */
    private val pendingResponses = ConcurrentHashMap<String, CompletableDeferred<JSONRPCMessage>>()

    /**
     * A queue of messages sent in [send] that did not match an item in [pendingResponses].  This queue is expected to
     * be drained by a client SSE stream.  If the client does not read messages from this channel fast enough (such
     * that the channel contains more than [messageQueueCapacity], messages will be dropped)
     */
    private val messageQueue =
        Channel<JSONRPCMessage>(capacity = messageQueueCapacity, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /**
     * Responds to an [ApplicationCall] with an SSE stream
     */
    suspend fun sseStream(call: ApplicationCall) {
        call.response.header(HttpHeaders.CacheControl, "no-store")
        call.response.header(HttpHeaders.Connection, "keep-alive")
        call.response.header("X-Accel-Buffering", "no")
        call.respondTextWriter(ContentType.Text.EventStream) {
            try {
                while (true) {
                    val msg = messageQueue.receiveCatching().getOrNull() ?: break
                    write("event: message\n")
                    write("data: ${McpJson.encodeToString(msg)}\n\n")
                    flush()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(e) { "SSE stream terminated for session $transportSessionId" }
            } finally {
                logger.debug { "SSE stream closed for session $transportSessionId" }
            }
        }
    }

    suspend fun handlePost(call: ApplicationCall) {
        val msg = try {
            call.receive<JSONRPCMessage>()
        } catch (e: SerializationException) {
            call.respondText(
                McpJson.encodeToString(
                    JSONRPCError(
                        RequestId(0),
                        RPCError(RPCError.ErrorCode.INTERNAL_ERROR, "Invalid request: ${e.message}"),
                    ),
                ),
                ContentType.Application.Json,
                HttpStatusCode.InternalServerError,
            )
            return
        }

        when (msg) {
            is JSONRPCRequest -> {
                val id = when (val id = msg.id) {
                    is RequestId.NumberId -> id.value.toString()
                    is RequestId.StringId -> id.value
                }

                // a response is expected for this request, wait for one to be sent
                val deferredResponse = CompletableDeferred<JSONRPCMessage>()
                pendingResponses[id] = deferredResponse

                try {
                    _onMessage.invoke(msg)

                    val response = withTimeoutOrNull(responseTimeout) { deferredResponse.await() }
                    if (response != null) {
                        call.response.header(MCP_SESSION_ID_HEADER_NAME, transportSessionId)
                        call.respondText(McpJson.encodeToString(response), ContentType.Application.Json)
                    } else {
                        logger.warn { "Timeout of $responseTimeout reached for request $id in transport with ID: $transportSessionId" }

                        if (!call.response.isCommitted) {
                            call.respondText(
                                McpJson.encodeToString(
                                    JSONRPCError(
                                        msg.id,
                                        RPCError(RPCError.ErrorCode.REQUEST_TIMEOUT, "Request timed out"),
                                    ),
                                ),
                                ContentType.Application.Json,
                            )
                        }
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Error processing JSON-RPC request $id in transport with ID: $transportSessionId" }

                    if (!call.response.isCommitted) {
                        call.respondText(
                            McpJson.encodeToString(
                                JSONRPCError(
                                    msg.id,
                                    RPCError(RPCError.ErrorCode.INTERNAL_ERROR, "Error: ${e.message}"),
                                ),
                            ),
                            ContentType.Application.Json,
                        )
                    }
                } finally {
                    pendingResponses.remove(id)
                }
            }

            // TODO: what is the appropriate handling of non-request type requests?
            else -> call.respond(HttpStatusCode.Accepted)
        }
    }

    override suspend fun start() {
        logger.debug { "Starting streamable HTTP transport with ID: $transportSessionId" }
    }

    override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
        when (message) {
            is JSONRPCResponse -> {
                val id = when (val id = message.id) {
                    is RequestId.NumberId -> id.value.toString()
                    is RequestId.StringId -> id.value
                }

                pendingResponses[id]?.complete(message) ?: run {
                    logger.warn { "Sending unexpected response $id: $message" }
                    messageQueue.send(message)
                }
            }

            else -> messageQueue.send(message)
        }
    }

    override suspend fun close() {
        logger.debug { "Closing streamable HTTP transport with ID: $transportSessionId" }
        messageQueue.close()
        pendingResponses.clear()
        _onClose.invoke()
    }
}