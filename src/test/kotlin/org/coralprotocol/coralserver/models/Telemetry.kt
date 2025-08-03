package org.coralprotocol.coralserver.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.coralprotocol.coralserver.models.serialization.OneOrMany

@Serializable
data class Test(val a: OneOrMany<String>, val b: String)

const val data = """
{
    "data": {
        "max_tokens": 512,
        "messages": {
            "data": [
                {
                    "content": [
                        {
                            "text": "create a thread and send 3 random messages in it",
                            "type": "text"
                        }
                    ],
                    "role": "user"
                },
                {
                    "content": [],
                    "role": "assistant",
                    "tool_calls": [
                        {
                            "function": {
                                "arguments": "{\"participantIds\":[\"coral-rust-agent\"],\"threadName\":\"Random Messages Thread 2\"}",
                                "name": "create_thread"
                            },
                            "id": "fc_688ccb410bac81919a67c392dddf7d9e02992a2194375a23",
                            "type": "function"
                        },
                        {
                            "function": {
                                "arguments": "{\"content\":\"Hello, Universe!\",\"mentions\":[\"coral-rust-agent\"],\"threadId\":\"364ef780-639c-460d-ac1a-6b3fbb3502a8\"}",
                                "name": "send_message"
                            },
                            "id": "fc_688ccb419cf48191bddf355b59ec0b7002992a2194375a23",
                            "type": "function"
                        },
                        {
                            "function": {
                                "arguments": "{\"content\":\"What's the most interesting thing you've seen today?\",\"mentions\":[\"coral-rust-agent\"],\"threadId\":\"364ef780-639c-460d-ac1a-6b3fbb3502a8\"}",
                                "name": "send_message"
                            },
                            "id": "fc_688ccb4294dc81918aee074197c2a60202992a2194375a23",
                            "type": "function"
                        },
                        {
                            "function": {
                                "arguments": "{\"content\":\"Just a quirky idea I had!\",\"mentions\":[\"coral-rust-agent\"],\"threadId\":\"364ef780-639c-460d-ac1a-6b3fbb3502a8\"}",
                                "name": "send_message"
                            },
                            "id": "fc_688ccb43adc08191bf1e1ef4a22107ed02992a2194375a23",
                            "type": "function"
                        }
                    ]
                },
                {
                    "content": [
                        {
                            "text": "Thread created successfully:\nID: f13e7ca4-bddc-4bbf-a889-1133d7d194b8\nName: Random Messages Thread 2\nCreator: coral-rust-agent\nParticipants: coral-rust-agent",
                            "type": "text"
                        }
                    ],
                    "role": "tool",
                    "tool_call_id": "fc_688ccb410bac81919a67c392dddf7d9e02992a2194375a23"
                },
                {
                    "content": [
                        {
                            "text": "Message sent successfully:\nID: 2f1dc4f4-b387-4448-ab8b-4a9de37c5331\nThread: 364ef780-639c-460d-ac1a-6b3fbb3502a8\nSender: coral-rust-agent",
                            "type": "text"
                        }
                    ],
                    "role": "tool",
                    "tool_call_id": "fc_688ccb419cf48191bddf355b59ec0b7002992a2194375a23"
                },
                {
                    "content": [
                        {
                            "text": "Message sent successfully:\nID: 518f67b7-90f5-4bdc-9762-5011287bf687\nThread: 364ef780-639c-460d-ac1a-6b3fbb3502a8\nSender: coral-rust-agent",
                            "type": "text"
                        }
                    ],
                    "role": "tool",
                    "tool_call_id": "fc_688ccb4294dc81918aee074197c2a60202992a2194375a23"
                },
                {
                    "content": [
                        {
                            "text": "Message sent successfully:\nID: aa219c28-ffe0-4a3e-84f5-ee280b58b5dd\nThread: 364ef780-639c-460d-ac1a-6b3fbb3502a8\nSender: coral-rust-agent",
                            "type": "text"
                        }
                    ],
                    "role": "tool",
                    "tool_call_id": "fc_688ccb43adc08191bf1e1ef4a22107ed02992a2194375a23"
                }
            ],
            "format": "OpenAI"
        },
        "model_description": "gpt-4o-mini",
        "preamble": "You are a unit test.",
        "resources": [
            {
                "id": "coral://localhost:5555/devmode/coral-rust-agent/public/session1/sse?agentId=coral-rust-agent",
                "text": "<threads><ResolvedThread id=\"364ef780-639c-460d-ac1a-6b3fbb3502a8\" name=\"Random Messages Thread\" creatorId=\"coral-rust-agent\" isClosed=\"false\"><participants>coral-rust-agent</participants><ResolvedMessage id=\"86173cd9-14f6-4a01-ab2a-6b80260875dc\" threadName=\"Random Messages Thread\" threadId=\"364ef780-639c-460d-ac1a-6b3fbb3502a8\" senderId=\"coral-rust-agent\" content=\"Hello, World!\" timestamp=\"1754057426791\"><mentions>coral-rust-agent</mentions></ResolvedMessage><ResolvedMessage id=\"c05a2977-e836-4617-97bb-105864c5842e\" threadName=\"Random Messages Thread\" threadId=\"364ef780-639c-460d-ac1a-6b3fbb3502a8\" senderId=\"coral-rust-agent\" content=\"How's everyone doing today?\" timestamp=\"1754057426794\"><mentions>coral-rust-agent</mentions></ResolvedMessage><ResolvedMessage id=\"13347c91-c77b-444d-9190-0793d9a1928f\" threadName=\"Random Messages Thread\" threadId=\"364ef780-639c-460d-ac1a-6b3fbb3502a8\" senderId=\"coral-rust-agent\" content=\"Just sharing a random thought!\" timestamp=\"1754057426797\"><mentions>coral-rust-agent</mentions></ResolvedMessage></ResolvedThread></threads>"
            }
        ],
        "temperature": 0.97,
        "tools": [
            {
                "id": "list_agents",
                "text": "Tool: list_agents\nDefinition: \n{\n  \"name\": \"list_agents\",\n  \"description\": \"List all registered agents in your contact.\",\n  \"parameters\": {\n    \"properties\": {\n      \"includeDetails\": {\n        \"description\": \"Whether to include agent details in the response\",\n        \"type\": \"boolean\"\n      }\n    },\n    \"required\": [\n      \"includeDetails\"\n    ],\n    \"type\": \"object\"\n  }\n}"
            },
            {
                "id": "send_message",
                "text": "Tool: send_message\nDefinition: \n{\n  \"name\": \"send_message\",\n  \"description\": \"Send a message to a thread\",\n  \"parameters\": {\n    \"properties\": {\n      \"content\": {\n        \"description\": \"Content of the message\",\n        \"type\": \"string\"\n      },\n      \"mentions\": {\n        \"description\": \"List of agent IDs to mention in the message. You *must* mention an agent for them to be made aware of the message.\",\n        \"items\": {\n          \"type\": \"string\"\n        },\n        \"type\": \"array\"\n      },\n      \"threadId\": {\n        \"description\": \"ID of the thread\",\n        \"type\": \"string\"\n      }\n    },\n    \"required\": [\n      \"threadId\",\n      \"content\",\n      \"mentions\"\n    ],\n    \"type\": \"object\"\n  }\n}"
            },
            {
                "id": "create_thread",
                "text": "Tool: create_thread\nDefinition: \n{\n  \"name\": \"create_thread\",\n  \"description\": \"Create a new thread with a list of participants\",\n  \"parameters\": {\n    \"properties\": {\n      \"participantIds\": {\n        \"description\": \"List of agent IDs to include as participants\",\n        \"items\": {\n          \"type\": \"string\"\n        },\n        \"type\": \"array\"\n      },\n      \"threadName\": {\n        \"description\": \"Name of the thread\",\n        \"type\": \"string\"\n      }\n    },\n    \"required\": [\n      \"threadName\",\n      \"participantIds\"\n    ],\n    \"type\": \"object\"\n  }\n}"
            },
            {
                "id": "wait_for_mentions",
                "text": "Tool: wait_for_mentions\nDefinition: \n{\n  \"name\": \"wait_for_mentions\",\n  \"description\": \"Wait until mentioned. Call this tool when you're done or want to wait for another agent to respond. This will block until a message is received. You will see all unread messages.\",\n  \"parameters\": {\n    \"properties\": {\n      \"timeoutMs\": {\n        \"description\": \"Timeout in milliseconds (default: 60000 ms). Must be between 0 and 60000 ms.\",\n        \"type\": \"number\"\n      }\n    },\n    \"required\": [\n      \"timeoutMs\"\n    ],\n    \"type\": \"object\"\n  }\n}"
            },
            {
                "id": "add_participant",
                "text": "Tool: add_participant\nDefinition: \n{\n  \"name\": \"add_participant\",\n  \"description\": \"Add a participant to a thread\",\n  \"parameters\": {\n    \"properties\": {\n      \"participantId\": {\n        \"description\": \"ID of the agent to add\",\n        \"type\": \"string\"\n      },\n      \"threadId\": {\n        \"description\": \"ID of the thread\",\n        \"type\": \"string\"\n      }\n    },\n    \"required\": [\n      \"threadId\",\n      \"participantId\"\n    ],\n    \"type\": \"object\"\n  }\n}"
            },
            {
                "id": "remove_participant",
                "text": "Tool: remove_participant\nDefinition: \n{\n  \"name\": \"remove_participant\",\n  \"description\": \"Remove a participant from a thread\",\n  \"parameters\": {\n    \"properties\": {\n      \"participantId\": {\n        \"description\": \"ID of the agent to remove\",\n        \"type\": \"string\"\n      },\n      \"threadId\": {\n        \"description\": \"ID of the thread\",\n        \"type\": \"string\"\n      }\n    },\n    \"required\": [\n      \"threadId\",\n      \"participantId\"\n    ],\n    \"type\": \"object\"\n  }\n}"
            },
            {
                "id": "close_thread",
                "text": "Tool: close_thread\nDefinition: \n{\n  \"name\": \"close_thread\",\n  \"description\": \"Close a thread with a summary\",\n  \"parameters\": {\n    \"properties\": {\n      \"summary\": {\n        \"description\": \"Summary of the thread\",\n        \"type\": \"string\"\n      },\n      \"threadId\": {\n        \"description\": \"ID of the thread to close\",\n        \"type\": \"string\"\n      }\n    },\n    \"required\": [\n      \"threadId\",\n      \"summary\"\n    ],\n    \"type\": \"object\"\n  }\n}"
            }
        ]
    },
    "targets": [
        {
            "message_id": "2f1dc4f4-b387-4448-ab8b-4a9de37c5331",
            "thread_id": "364ef780-639c-460d-ac1a-6b3fbb3502a8"
        },
        {
            "message_id": "518f67b7-90f5-4bdc-9762-5011287bf687",
            "thread_id": "364ef780-639c-460d-ac1a-6b3fbb3502a8"
        },
        {
            "message_id": "aa219c28-ffe0-4a3e-84f5-ee280b58b5dd",
            "thread_id": "364ef780-639c-460d-ac1a-6b3fbb3502a8"
        }
    ]
}"""

fun main() {

    val json = Json {
        encodeDefaults = true
    }

    println(json.decodeFromString<TelemetryPost>(data))
}