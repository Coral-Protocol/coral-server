# Coral Server - Agent Fuzzy A2A (Agent to Agent) Communication MCP Tools

An implementation of the Coral protocol that acts as an MCP server providing tools for agents to communicate with each other.

![header](https://github.com/user-attachments/assets/2b74074e-42c2-4abd-9827-ea3c68b75c99)

## Project Description

This project implements a Model Context Protocol (MCP) server that facilitates communication between AI agents through a thread-based messaging system. 


Currently, it provides a set of tools that allow agents to:

- Register themselves in the system
- Create and manage conversation threads
- Send messages to threads
- Mention other agents in messages
- Receive notifications when mentioned

![demostration graph](https://github.com/user-attachments/assets/a5227d18-8c57-48b9-877f-97859b176957)

### Development status
This project is in its early stages and is not yet production-ready. Right now, this is in local mode only, but we are working on a remote mode that will allow agents to communicate over the internet.

Please don't hesitate to reach out if you want to be involved in coordinating any truly necessary standard changes or new standards with us.

## How to Run

### Quick example
This repository contains a full multi-agent example that can be found [here](/examples/camel-search-maths).

Alternatively, for a step-by-step guide to building agentic applications from scratch, you can follow [this tutorial](https://github.com/Coral-Protocol/existing-agent-sessions-tutorial-private-temp)

### Demo Video

[![Coral Server Demo](https://github.com/user-attachments/assets/13a52dda-ee46-4aad-98df-e31fb905d68c)](https://youtu.be/MyokByTzY90)
*Click the image above to watch the demo video*

### Setup using Gradle

```bash
# Run with SSE server using Ktor plugin (default, port 5555)
./gradlew run

# Run with custom arguments
./gradlew run --args="--stdio"
./gradlew run --args="--sse-server 5555"
```


### Setup using Docker

Install [Docker](https://docs.docker.com/desktop/)

```bash
# Build the Docker Image
docker build -t coral-server .

# Run the Docker Container
docker run -p 5555:5555 -v /path/to/your/coral-server/src/main/resources:/config coral-server
```

### Run Modes

- `--stdio`: Runs an MCP server using standard input/output
- `--sse-server-ktor <port>`: Runs an SSE MCP server using Ktor plugin (default if no argument is provided)
- `--sse-server <port>`: Runs an SSE MCP server with a plain configuration

## Available Tools

The server provides the following tools for agent communication:

### Agent Management
- `list_agents`: List all registered agents

### Thread Management
- `create_thread`: Create a new thread with participants
- `add_participant`: Add a participant to a thread
- `remove_participant`: Remove a participant from a thread
- `close_thread`: Close a thread with a summary

### Messaging
- `send_message`: Send a message to a thread
- `wait_for_mentions`: Wait for new messages mentioning an agent

## Connections (SSE Mode)

### Coral Server
You can connect to the server on:  

```bash
http://localhost:5555/devmode/exampleApplication/privkey/session1/sse
```

### MCP Inspector
You can connect to the server using the MCP Inspector command:

```bash
npx @modelcontextprotocol/inspector sse --url http://localhost:5555/devmode/exampleApplication/privkey/session1/sse
```
### Register an Agent
You can register an agent to the Coral Server (also can be registered on MCP inspector) on:

```bash
http://localhost:5555/devmode/exampleApplication/privkey/session1/sse?agentId=test_agent
```

## Contribution Guidelines

We welcome contributions! Email us at [hello@coralprotocol.org](mailto:hello@coralprotocol.org) or join our Discord [here](https://discord.gg/rMQc2uWXhj) to connect with the developer team. Feel free to open issues or submit pull requests.

Thanks for checking out the project, we hope you like it!

