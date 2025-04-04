# Coral Server - Agent Fuzzy A2A (Agent to Agent) Communication MCP Tools

An implementation of the Coral protocol that acts as an MCP server providing tools for agents to communicate with each other.

## Project Description

This project implements a Model Context Protocol (MCP) server that facilitates communication between AI agents through a thread-based messaging system. 


Currently, it provides a set of tools that allow agents to:

- Register themselves in the system
- Create and manage conversation threads
- Send messages to threads
- Mention other agents in messages
- Receive notifications when mentioned

The server can be run in different modes (stdio, SSE) to support various integration scenarios.

## How to Run

The project can be run in several modes:

### Using Gradle

```bash
# Run with SSE server using Ktor plugin (default, port 3001)
./gradlew run

# Run with custom arguments
./gradlew run --args="--stdio"
./gradlew run --args="--sse-server-ktor 8080"
./gradlew run --args="--sse-server 8080"
```

### Using Java

```bash
# Build the project first
./gradlew build

# Run the JAR file
java -jar build/libs/agent-fuzzy-p2p-tools-1.0-SNAPSHOT.jar
```

### Run Modes

- `--stdio`: Runs an MCP server using standard input/output
- `--sse-server-ktor <port>`: Runs an SSE MCP server using Ktor plugin (default if no argument is provided)
- `--sse-server <port>`: Runs an SSE MCP server with a plain configuration

## Available Tools

The server provides the following tools for agent communication:

### Agent Management
- `register_agent`: Register an agent in the system
- `list_agents`: List all registered agents

### Thread Management
- `create_thread`: Create a new thread with participants
- `add_participant`: Add a participant to a thread
- `remove_participant`: Remove a participant from a thread
- `close_thread`: Close a thread with a summary

### Messaging
- `send_message`: Send a message to a thread
- `wait_for_mentions`: Wait for new messages mentioning an agent

## Connecting to the Server

### Using the MCP Inspector

When running in SSE mode, you can connect to the server using the MCP Inspector:
- SSE Server URL: `http://localhost:<port>/sse`

## Philosophy

Open infrastructure for the Society of AI Agents

It's a strange concept; we believe that much of what we now consider work will be handled by a different kind of society—a Society of AI Agents.

To bridge this gap, Coral Protocol was built as the connective tissue of this society. Coral is designed to enable agents to discover one another, communicate securely, exchange value, and scale their collaborative efforts from any framework.

We theorize that not only will this fix many problems with the composability of multi-agent systems, but it will also unlock their full potential to be much more capable and safe, this is due to the graph-like structure that prevents any one agent from holding too much power or becoming overwhelmed with too much responsibility. 

## Contribution Guidelines

We welcome contributions! Email us at [hello@coralprotocol.org](mailto:hello@coralprotocol.org) or join our Discord [here](https://discord.gg/rMQc2uWXhj) to connect with the developer team. Feel free to open issues or submit pull requests.

Thanks for checking out the project, we hope you like it!
