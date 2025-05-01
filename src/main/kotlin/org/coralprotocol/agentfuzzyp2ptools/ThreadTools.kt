package org.coralprotocol.agentfuzzyp2ptools

import org.coralprotocol.agentfuzzyp2ptools.Message
import org.coralprotocol.agentfuzzyp2ptools.ThreadManager

/**
 * Class containing utility functions for thread-based tools.
 */
object ThreadTools {
    /**
     * Format messages in an XML-like structure for clear presentation.
     */
    fun formatMessagesAsXml(messages: List<Message>): String {
        val sb = StringBuilder("<messages>\n")

        // Group messages by thread
        val messagesByThread = messages.groupBy { it.threadId }

        for ((threadId, threadMessages) in messagesByThread) {
            val thread = ThreadManager.getThread(threadId)
            sb.append("  <thread id=\"$threadId\" name=\"${thread?.name ?: "Unknown"}\">\n")

            // Add thread status
            if (thread?.isClosed == true) {
                sb.append("    <status>closed</status>\n")
                sb.append("    <summary>${thread.summary ?: "No summary provided"}</summary>\n")
            } else {
                sb.append("    <status>open</status>\n")
            }

            // Add participants
            sb.append("    <participants>\n")
            thread?.participants?.forEach { participantId ->
                val participant = ThreadManager.getAgent(participantId)
                sb.append("      <participant id=\"$participantId\" name=\"${participant?.name ?: "Unknown"}\" />\n")
            }
            sb.append("    </participants>\n")

            // Add messages
            sb.append("    <messages>\n")
            threadMessages.forEach { message ->
                val sender = ThreadManager.getAgent(message.senderId)
                sb.append("      <message id=\"${message.id}\" timestamp=\"${message.timestamp}\">\n")
                sb.append("        <sender id=\"${message.senderId}\" name=\"${sender?.name ?: "Unknown"}\" />\n")

                // Add mentions
                if (message.mentions.isNotEmpty()) {
                    sb.append("        <mentions>\n")
                    message.mentions.forEach { mentionId ->
                        val mentioned = ThreadManager.getAgent(mentionId)
                        sb.append("          <mention id=\"$mentionId\" name=\"${mentioned?.name ?: "Unknown"}\" />\n")
                    }
                    sb.append("        </mentions>\n")
                }

                // Add content
                sb.append("        <content>${message.content}</content>\n")
                sb.append("      </message>\n")
            }
            sb.append("    </messages>\n")
            sb.append("  </thread>\n")
        }

        sb.append("</messages>")
        return sb.toString()
    }
}
