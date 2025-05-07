# Setting Up and Running the Coral Server with LangChain Agents

This example demonstrates a system with two agents implemented using LangChain and Coral Server tools, working together to handle user queries. The `user_interface_agent` interacts with users and coordinates tasks, while the `world_news_agent` fetches and processes news-related information.

## Prerequisites

- Python 3.12.10
- Access to an OpenAI API key (set as `OPENAI_API_KEY` in your environment variables)
- Access to a World News API key (set as `WORLD_NEWS_API_KEY` in your environment variables)
- Basic familiarity with terminal commands and Python virtual environments

## Running the Example

### 1. Set Up a Virtual Environment

Create and activate a Python virtual environment to isolate dependencies:

```bash
python -m venv venv
source venv/bin/activate  # On Windows, use: venv\Scripts\activate
```

### 2. Install Dependencies

Install the required Python packages. You can use a `requirements.txt` file or install individually:

#### Option 1: Using requirements.txt
Create a `requirements.txt` file with the following content:
```
langchain
langchain_mcp_adapters
langchain-openai
worldnewsapi
```
Then run:
```bash
pip install -r requirements.txt
```

#### Option 2: Install Individually
```bash
pip install langchain
pip install langchain_mcp_adapters
pip install -U langchain-openai
pip install worldnewsapi
```

### 3. Configure the Environment

Ensure you have both `OPENAI_API_KEY` and `WORLD_NEWS_API_KEY` set in your environment variables for the OpenAI model and World News API used by LangChain. You can set them in your terminal:

```bash
export OPENAI_API_KEY='your-openai-api-key-here'  # On Windows, use: set OPENAI_API_KEY=your-openai-api-key-here
export WORLD_NEWS_API_KEY='your-world-news-api-key-here'  # On Windows, use: set WORLD_NEWS_API_KEY=your-world-news-api-key-here
```

### 4. Start the Coral Server

The agents communicate via a Coral Server, which provides tools for agent registration, discovery, and messaging. To start the server:

```bash
./gradlew run
```

Navigate to your project's root directory and run the above command. Gradle may show "83%" completion but will continue running. Check the terminal logs to confirm the server is active (typically at `http://localhost:5555`).

> **Note**: The server must be running before starting the agents, as they register with it upon initialization.

### 5. Run the Agents

The system includes two agents: `user_interface_agent` and `world_news_agent`. Both must be running to process queries. In separate terminal windows, run each agent script (ensure the virtual environment is activated in each terminal).

#### Run the User Interface Agent
```bash
python 0_langchain_interface.py
```

#### Run the World News Agent
```bash
python 1_langchain_world_news_agent.py
```

> **Note**: Ensure the Coral Server is running before starting the agents, as they need to register with it.

### 6. Interact with the Agents

Once both agents are running, the `user_interface_agent` will prompt you for input via STDIN, displaying:

```
How can I assist you today?
```

Enter a query, such as:

```
What's the latest news on climate change?
```

The agents will collaborate to process your query, and the `user_interface_agent` will display the results.

## How Agents Register and Communicate

### Agent Registration

Both agents register with the Coral Server upon startup, using the provided configuration parameters. The registration process involves:

- **Connection to the Coral Server**: Each agent connects to the server at the specified `base_url` (`http://localhost:5555/devmode/exampleApplication/privkey/session1/sse`).
- **Agent Parameters**: Each agent provides:
  - `agentId`: A unique identifier (`user_interface_agent` or `world_news_agent`).
  - `agentDescription`: A description of its role (e.g., "You are user_interaction_agent, responsible for engaging with users...").
  - `waitForAgents`: Set to `2`, indicating the agent waits for both agents to be available. If you want to run just one agent set this to 1 and run the single agent.
- **Server-Side Registration**: The Coral Server assigns the agent a unique decentralized identifier (DID) and registers it in its agent registry, enabling discovery by other agents. This is facilitated by Coral's tooling for cryptographically verified identities.

### Agent Communication

The agents communicate using Coral Server tools (`list_agents`, `create_thread`, `send_message`, `wait_for_mentions`, `ask_human`), following the workflows defined in their prompts. Here's how it works:

#### User Interface Agent Workflow
1. **List Agents**: Calls `list_agents` to discover other connected agents and their descriptions, identifying potential collaborators (e.g., `world_news_agent`).
2. **Prompt User**: Uses `ask_human` to ask, "How can I assist you today?" and captures the user's response.
3. **Analyze Intent**: Takes 2 seconds to interpret the user's intent and select an appropriate agent based on the `list_agents` output.
4. **Handle Coral Server Queries**: If the query is about the Coral Server, it uses server tools to fetch information and responds directly.
5. **Create Thread**: Uses `create_thread` to initiate a communication thread with the selected agent (e.g., `world_news_agent`).
6. **Send Instructions**: Formulates a task ("instruction") and uses `send_message` to send it to the selected agent in the thread.
7. **Wait for Response**: Uses `wait_for_mentions` (8-second timeout) to receive the response from the mentioned agent.
8. **Display Conversation**: Shows the entire thread conversation to the user.
9. **Follow-Up**: Waits 3 seconds, then uses `ask_human` to ask if the user needs further assistance, repeating the process if needed.

#### World News Agent Workflow
1. **Wait for Mentions**: Calls `wait_for_mentions` (8-second timeout) to listen for messages from other agents.
2. **Process Mention**: Upon receiving a mention, stores the thread ID and sender ID (e.g., `user_interface_agent`).
3. **Analyze Instruction**: Takes 2 seconds to interpret the instruction and checks available tools (e.g., `worldnewsapi` for news queries).
4. **Plan Execution**: Creates a step-by-step plan based on the tool schema to fulfill the instruction.
5. **Execute Tools**: Calls necessary tools to complete the task.
6. **Formulate Response**: Takes 3 seconds to verify the task was completed, crafting a response ("answer") or an error message if applicable.
7. **Send Response**: Uses `send_message` to reply to the sender in the same thread, ensuring the response is directed to the original sender.
8. **Repeat**: Waits 2 seconds and resumes listening for new mentions.

#### Coral Server Tools
The Coral Server provides a standardized messaging and coordination framework, enabling seamless agent interactions:
- **list_agents**: Retrieves a list of registered agents and their descriptions.
- **create_thread**: Creates a communication thread for multi-agent collaboration.
- **send_message**: Sends messages within a thread, supporting mentions to specific agents.
- **wait_for_mentions**: Listens for messages directed to the agent, with a configurable timeout.
- **ask_human**: Facilitates direct interaction with the user via STDIN.

These tools use standardized messaging formats and secure communication protocols (e.g., end-to-end encryption and decentralized identifiers) to ensure trustworthy interactions.

### Example Interaction
For the query "What's the latest news on climate change?":
1. The `user_interface_agent` lists agents, identifies `world_news_agent` as suitable, and creates a thread.
2. It sends an instruction like "Fetch the latest news on climate change" to `world_news_agent`.
3. The `world_news_agent` receives the mention, uses `worldnewsapi` to fetch news, and sends a response with the results.
4. The `user_interface_agent` displays the conversation and asks if the user needs further assistance.

## Troubleshooting

- **Agent Registration Fails**: Ensure the Coral Server is running before starting the agents. Restart the server if agents fail to connect.
- **API Key Issues**: Verify that `OPENAI_API_KEY` and `WORLD_NEWS_API_KEY` are correctly set and valid.
- **Timeout Errors**: Agents are configured with an 8-second timeout for mentions. If no response is received, they may need restarting.
- **Server Stuck at 83%**: This is normal for Gradle; check logs to confirm the server is running.
- **Agent Unregistration**: Agents are not unregistered automatically. Restart the server to clear the registry for a fresh run.

## Building on the Example

To add a new agent:
1. Copy an existing agent script (e.g., `0_langchain_interface.py`).
2. Modify the `agentId` and `agentDescription` to ensure uniqueness.
3. Update the prompt and tools as needed for the new agent's role.
4. Run the new agent alongside the others using `python new_agent.py`.

## Future Potential

This is a proof-of-concept demonstrating agent collaboration via Coral Server. Future enhancements could include:
- **Remote Mode**: Support for distributed agents across multiple servers.
- **Session Management**: Persistent sessions for long-running interactions.
- **Advanced Tooling**: Integration with additional APIs or custom tools for expanded functionality.
## Community and Support

If you have any questions, suggestions, or need assistance, feel free to join our Discord community: [Join our Discord](https://discord.gg/cDzGHnzkwD). We're here to help!
