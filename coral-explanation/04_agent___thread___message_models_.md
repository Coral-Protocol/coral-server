# Chapter 4: Agent / Thread / Message Models

In the [previous chapter](03_mcp_server___server___.md), we learned about the [MCP Server (`Server`)](03_mcp_server___server___.md), the central manager that handles communication and directs tool requests. We know the server uses [Tools](01_tool__mcp_concept__.md) like `register_agent` and `send_message`, and it expects specific [Tool Inputs (`*Input` classes)](02_tool_inputs____input__classes__.md) for each tool.

But when an agent registers, where does the server *store* its information? When a message is sent, how does the server *keep track* of the conversation and the message itself? Just handling requests isn't enough; the server needs a way to organize and remember the key pieces of information it manages.

Think about organizing information in the real world. If you meet new people, you might add them to an address book. If you have a project discussion, you might create a folder to keep all related notes together. Inside that folder, each note represents a single piece of information.

The `coral-server` needs similar organizational structures. This is where **Agent / Thread / Message Models** come in. They are like the **blueprints** or **templates** for the digital "address book entries," "project folders," and "notes" that the server uses.

## What are Models? Blueprints for Data

In software, a "model" often refers to a data structure – a defined way to hold information. The `Agent`, `Thread`, and `Message` models in `coral-server` are exactly that: blueprints defined using Kotlin's `data class` feature.

*   **`Agent` Model:** The blueprint for storing information about a single agent (a user or a bot). It defines *what* we need to know about each agent.
*   **`Thread` Model:** The blueprint for a conversation. It outlines *what* information defines a conversation, like who is involved and what messages have been sent.
*   **`Message` Model:** The blueprint for a single message within a thread. It specifies *what* details make up a message, like who sent it and what it says.

These blueprints ensure that whenever the server works with an agent, a thread, or a message, it always handles the information in a consistent and predictable way.

Let's look at each blueprint. These are defined in the `ThreadModels.kt` file.

### 1. The `Agent` Model: An Address Book Entry

This model defines what information we keep for each agent that registers with the server.

```kotlin
// From: src/main/kotlin/org/coralprotocol/agentfuzzyp2ptools/ThreadModels.kt

@Serializable // Makes it easy to convert to/from text (like JSON)
data class Agent(
    val id: String,        // Unique identifier (like a username)
    val name: String,      // Display name (how it appears)
    val description: String = "" // Optional: What the agent does
)
```

*   **`@Serializable`**: Just like with [Tool Inputs (`*Input` classes)](02_tool_inputs____input__classes__.md), this marker helps convert `Agent` objects to text formats (like JSON) and back.
*   **`data class Agent(...)`**: Defines the blueprint named `Agent`.
*   **`id: String`**: A unique text ID for the agent. This is crucial for telling agents apart.
*   **`name: String`**: A human-readable name for the agent.
*   **`description: String = ""`**: An optional text field to describe the agent's purpose. It defaults to an empty string if not provided.

So, whenever the `coral-server` needs to represent an agent, it uses this structure. For example, an agent object might look like this in memory: `Agent(id="email-bot", name="Email Sorter Bot", description="Sorts incoming emails.")`.

### 2. The `Thread` Model: A Conversation Folder

This model defines the structure for a conversation thread. Think of it like a folder containing participants and messages.

```kotlin
// From: src/main/kotlin/org/coralprotocol/agentfuzzyp2ptools/ThreadModels.kt

@Serializable
data class Thread(
    val id: String = UUID.randomUUID().toString(), // Auto-generated unique ID
    val name: String,              // Name of the conversation
    val creatorId: String,         // ID of the agent who started it
    // List of IDs of agents in the thread
    val participants: MutableList<String> = mutableListOf(),
    // List of actual Message objects (see below)
    val messages: MutableList<Message> = mutableListOf(),
    var isClosed: Boolean = false, // Has the conversation ended?
    var summary: String? = null    // Optional summary if closed
)
```

*   **`id: String = ...`**: A unique ID for the thread, automatically generated when a new thread is created.
*   **`name: String`**: The name given to this conversation (e.g., "Project Alpha Planning").
*   **`creatorId: String`**: The `id` of the `Agent` who initiated this thread.
*   **`participants: MutableList<String>`**: A *list* of agent `id`s representing everyone involved in this conversation. It's "mutable," meaning we can add or remove participants later.
*   **`messages: MutableList<Message>`**: A *list* holding all the actual `Message` objects (using the `Message` blueprint described next) sent within this thread. It's also mutable.
*   **`isClosed: Boolean`**: A flag (true/false) indicating if the thread is finished.
*   **`summary: String?`**: An optional text summary for closed threads (the `?` means it can be `null` or missing).

This structure holds everything needed to represent a complete conversation.

### 3. The `Message` Model: A Single Note

This model defines the structure for a single message sent within a `Thread`.

```kotlin
// From: src/main/kotlin/org/coralprotocol/agentfuzzyp2ptools/ThreadModels.kt

@Serializable
data class Message(
    val id: String = UUID.randomUUID().toString(), // Auto-generated unique ID
    val threadId: String,        // ID of the Thread this message belongs to
    val senderId: String,        // ID of the Agent who sent this
    val content: String,         // The actual text content of the message
    val timestamp: Long = System.currentTimeMillis(), // When it was sent
    // Optional: List of Agent IDs mentioned in the message
    val mentions: List<String> = emptyList()
)
```

*   **`id: String = ...`**: A unique ID for this specific message.
*   **`threadId: String`**: The `id` of the `Thread` this message is part of. This links the message back to its conversation.
*   **`senderId: String`**: The `id` of the `Agent` who sent the message.
*   **`content: String`**: The actual text or data of the message.
*   **`timestamp: Long = ...`**: A number representing the exact time the message was created (usually milliseconds since a standard point in time).
*   **`mentions: List<String>`**: An optional list of agent `id`s specifically mentioned or notified by this message (e.g., "@email-bot").

This blueprint ensures every message recorded by the server has these essential pieces of information.

## How are these Models Used?

These models are the fundamental building blocks for managing the server's state. They aren't typically used *directly* by clients sending requests, but they are used *internally* by the server logic, especially within the [Thread Manager (`ThreadManager`)](05_thread_manager___threadmanager__.md).

Let's revisit the `send_message` [Tool (MCP Concept)](01_tool__mcp_concept__.md):

1.  A client sends a request to the [MCP Server (`Server`)](03_mcp_server___server___.md) using the `send_message` tool name and providing arguments matching the [SendMessageInput](02_tool_inputs____input__classes__.md) class (`threadId`, `senderId`, `content`, etc.).
2.  The server routes this request to the `send_message` tool's code.
3.  The tool's code extracts the data from the `SendMessageInput` object.
4.  It then likely calls a function in the [Thread Manager (`ThreadManager`)](05_thread_manager___threadmanager__.md), like `ThreadManager.sendMessage(...)`.
5.  **Inside `ThreadManager.sendMessage`:**
    *   It uses the provided `senderId` to potentially look up an `Agent` object (using the `Agent` blueprint) to verify the sender exists.
    *   It uses the provided `threadId` to find the correct `Thread` object (using the `Thread` blueprint).
    *   It creates a *new* `Message` object using the `Message` blueprint, filling in the `threadId`, `senderId`, `content`, `mentions`, and generating a new `id` and `timestamp`.
    *   It adds this newly created `Message` object to the `messages` list *inside* the found `Thread` object.

These models provide the structure for the data that the `ThreadManager` manipulates and stores.

## Data Relationships

You can visualize the relationship between these models like this:

```mermaid
graph TD
    subgraph ServerState["Server's Memory (Managed by ThreadManager)"]
        A1[Agent (id='alice', name='Alice')]
        A2[Agent (id='bob', name='Bob')]
        A3[Agent (id='email-bot', name='Email Bot')]

        T1[Thread (id='t1', name='Project Alpha', participants=['alice', 'bob', 'email-bot'])]
        T2[Thread (id='t2', name='Quick Chat', participants=['alice', 'bob'])]

        M1[Message (id='m1', threadId='t1', senderId='alice', content='Hi team!')]
        M2[Message (id='m2', threadId='t1', senderId='bob', content='Hello!')]
        M3[Message (id='m3', threadId='t1', senderId='alice', content='@email-bot draft an email')]
        M4[Message (id='m4', threadId='t2', senderId='bob', content='Got a minute?')]

        T1 -- contains --> M1
        T1 -- contains --> M2
        T1 -- contains --> M3
        T2 -- contains --> M4

        M1 -- sent_by --> A1
        M2 -- sent_by --> A2
        M3 -- sent_by --> A1
        M4 -- sent_by --> A2

        M3 -- mentions --> A3
    end

    style ServerState fill:#f9f,stroke:#333,stroke-width:2px
```

*   The server knows about several `Agent`s.
*   It manages different `Thread`s. Each thread knows its `participants` (by their agent `id`s) and holds a list of `Message`s.
*   Each `Message` belongs to a specific `Thread` (via `threadId`) and was sent by a specific `Agent` (via `senderId`). A message can also explicitly `mention` other agents.

These models (`Agent`, `Thread`, `Message`) define the structure, while the [Thread Manager (`ThreadManager`)](05_thread_manager___threadmanager__.md) (which we'll see next) actually creates, stores, finds, and updates the *instances* of these structures.

## Conclusion

You've now learned about the core data models in `coral-server`: `Agent`, `Thread`, and `Message`. These are like blueprints or templates (`data class`es in Kotlin) that define the structure for storing information about agents, conversations, and individual messages.

*   **`Agent`**: Holds `id`, `name`, `description`.
*   **`Thread`**: Holds `id`, `name`, `creatorId`, `participants` (list of agent IDs), `messages` (list of `Message` objects), `isClosed`, `summary`.
*   **`Message`**: Holds `id`, `threadId`, `senderId`, `content`, `timestamp`, `mentions` (list of agent IDs).

These models ensure data consistency and provide the structured way the server organizes the information it manages. They are the fundamental building blocks used by the server's internal logic.

But how does the server *actually* manage all these agents, threads, and messages? How does it create new threads, add participants, store messages, and retrieve them? That's the job of the component we'll explore next!

**Next Chapter:** [Thread Manager (`ThreadManager`)](05_thread_manager___threadmanager__.md)

---
