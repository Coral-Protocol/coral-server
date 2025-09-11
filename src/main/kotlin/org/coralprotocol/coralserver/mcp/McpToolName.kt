package org.coralprotocol.coralserver.mcp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The names of the actual enums here don't really matter, the SerialName is used when registering it as a tool and
 * when exporting the OpenAPI spec
 */
@Serializable
enum class McpToolName {
    @SerialName("coral_add_participant")
    ADD_PARTICIPANT,

    @SerialName("coral_close_thread")
    CLOSE_THREAD,

    @SerialName("coral_create_thread")
    CREATE_THREAD,

    @SerialName("coral_list_agents")
    LIST_AGENTS,

    @SerialName("coral_remove_participant")
    REMOVE_PARTICIPANT,

    @SerialName("coral_send_message")
    SEND_MESSAGE,

    @SerialName("coral_wait_for_mentions")
    WAIT_FOR_MENTIONS,

    @SerialName("coral_close_session")
    CLOSE_SESSION;

    override fun toString(): String {
        return McpToolName.serializer().descriptor.getElementName(ordinal)
    }
}