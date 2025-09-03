package org.coralprotocol.coralserver.mcp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The names of the actual enums here don't really matter, the SerialName is used when registering it as a tool and
 * when exporting the OpenAPI spec
 */
@Serializable
enum class McpTooling {
    @SerialName("coral_add_participant")
    ADD_PARTICIPANT_TOOL_NAME,

    @SerialName("coral_close_thread")
    CLOSE_THREAD_TOOL_NAME,

    @SerialName("coral_create_thread")
    CREATE_THREAD_TOOL_NAME,

    @SerialName("coral_list_agents")
    LIST_AGENTS_TOOL_NAME,

    @SerialName("coral_remove_participant")
    REMOVE_PARTICIPANT_TOOL_NAME,

    @SerialName("coral_send_message")
    SEND_MESSAGE_TOOL_NAME,

    @SerialName("coral_wait_for_mentions")
    WAIT_FOR_MENTIONS_TOOL_NAME;

    override fun toString(): String {
        return McpTooling.serializer().descriptor.getElementName(ordinal)
    }
}