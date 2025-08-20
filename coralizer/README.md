# Coralizer: Integrating Firecrawl MCP with Coral Server

## What is Coralizer?

Coralizer is a powerful tool designed to seamlessly integrate any SSE-compatible MCP (Multi-Agent Coordination Platform) server with the Coral Server. By hosting your MCP and running `coralizer.py` with the agent name and SSE address, Coralizer handles the integration process, creating a Coral-compatible agent ready to interact within the Coral network. This eliminates the need for custom wiring or complex setup, making your MCP agent instantly usable and production-ready.

In this demo, we are **coralizing the Firecrawl MCP**, enabling it to operate as a Coral agent capable of scraping, crawling, and extracting data from web pages and URLs, as well as performing deep research and generating structured data for analysis.

## Why Coralizer?

Coralizer streamlines the adoption of Coral in MCP-based projects. Once connected, your MCP agent can receive inputs from the Coral network and invoke its tools as needed. This makes your multi-agent system more efficient, scalable, and ready for production use without additional configuration.

## Prerequisites

- Python 3.12.10
- Access to an OpenAI API key (set as `OPENAI_API_KEY` in your environment variables)
- Access to a Firecrawl API key (set as `FIRECRAWL_API_KEY` in your environment variables)
- Node.js and npm installed (for running the Firecrawl MCP)
- Basic familiarity with terminal commands and Python virtual environments
- Coral Server running (typically at `http://localhost:5555`) if you intend to run the coralized agent

## Setting Up and Running the Coralizer

### 1. Set Up the Virtual Environment

Create and activate a Python virtual environment to isolate dependencies:

```bash
python -m venv venv
source venv/bin/activate  # On Windows, use: venv\Scripts\activate
```

### 2. Install Dependencies

Install the required Python packages for the Coralizer:

```bash
pip install pydantic
pip install langchain_openai
pip install langchain_mcp_adapters
```

### 3. Navigate to the Coralizer Directory

Change to the Coralizer directory within your Coral Server project:

```bash
cd coral-server/coralizer
```

### 4. Run the Firecrawl MCP

The Firecrawl MCP is used as the SSE-compatible MCP in this demo. For more information, visit the [Firecrawl MCP Server GitHub](https://github.com/mendableai/firecrawl-mcp-server). Follow these steps to set it up:

1. Obtain a Firecrawl API key from [Firecrawl](https://www.firecrawl.dev/).
2. In a terminal (outside the Python virtual environment), run the Firecrawl MCP with the following command, replacing `fc-YOUR_API_KEY` with your Firecrawl API key:

```bash
env SSE_LOCAL=true FIRECRAWL_API_KEY=fc-YOUR_API_KEY npx -y firecrawl-mcp
```

3. Copy the SSE endpoint displayed in the terminal (e.g., `http://localhost:3000/sse`). This address may vary depending on the MCP configuration.

### 5. Run the Coralizer

In a new terminal, ensure the Python virtual environment is activated, then configure and run the Coralizer:

1. Set the OpenAI API key in your environment variables:

```bash
export OPENAI_API_KEY='your-openai-api-key-here'  # On Windows, use: set OPENAI_API_KEY=your-openai-api-key-here
```

2. Run the Coralizer script:

```bash
python utils/coralizer.py
```

3. When prompted, provide the following inputs:
   - **Enter the agent name**: `firecrawl`
   - **Enter the MCP server URL**: `http://localhost:3000/sse` (use the SSE endpoint copied from the Firecrawl MCP terminal)

4. A successful run of the Coralizer should produce output similar to the following:

```bash
(coralizer) suman@DESKTOP-47QSFPT:~/projects/coral_protocol/v2/coral-server/coralizer$ python3 utils/coralizer.py
Enter the agent name: firecrawl
Enter the MCP server URL: http://localhost:3000/sse    
Connected to MCP session for agent: firecrawl
File 'firecrawl_coral_agent.py' created successfully.
```

> **Note**: The Coralizer operates independently of the Coral Server and does not require it to be running to create the coralized agent. However, the Coral Server must be active to run the coralized agent (e.g., `firecrawl_coral_agent.py`) for registration and interaction within the Coral network.

### 6. Verify the Agent Configuration and Prompt

After running the Coralizer, the created agent must be verified to ensure it integrates correctly with the Coral Server when run. Check the following configuration parameters:

```python
coral_base_url = "http://localhost:5555/devmode/exampleApplication/privkey/session1/sse"
coral_params = {
    "waitForAgents": 2,
    "agentId": "firecrawl",
    "agentDescription": "You are an firecrawl agent capable of scraping, crawling, and extracting data from web pages and URLs, as well as performing deep research and generating structured data for analysis."
}
```

- **coral_base_url**: Ensure this matches the Coral Server's URL (typically `http://localhost:5555/devmode/exampleApplication/privkey/session1/sse`).
- **waitForAgents**: Set to `2` if running in a multi-agent system with two agents (e.g., alongside the `langchain_interface_agent`). Adjust to `1` if running the Firecrawl agent alone.
- **agentId**: Must be unique (`firecrawl` in this case).
- **agentDescription**: Verify it aligns with your multi-agent system's requirements. The provided description reflects the Firecrawl agent's capabilities.

Additionally, verify the prompt of the coralized MCP agent to ensure it correctly defines the agent's behavior. The prompt is designed for a generic agent, but you can update it according to your specific task requirements. The default prompt should match the following:

```python
prompt = ChatPromptTemplate.from_messages([
    (
        "system",
        f"""You are an agent interacting with the tools from Coral Server and having your own tools. Your task is to perform any instructions coming from any agent. 
        Follow these steps in order:
        1. Call wait_for_mentions from coral tools (timeoutMs: 30000) to receive mentions from other agents.
        2. When you receive a mention, keep the thread ID and the sender ID.
        3. Take 2 seconds to think about the content (instruction) of the message and check only from the list of your tools available for you to action.
        4. Check the tool schema and make a plan in steps for the task you want to perform.
        5. Only call the tools you need to perform for each step of the plan to complete the instruction in the content.
        6. Take 3 seconds and think about the content and see if you have executed the instruction to the best of your ability and the tools. Make this your response as "answer".
        7. Use `send_message` from coral tools to send a message in the same thread ID to the sender Id you received the mention from, with content: "answer".
        8. If any error occurs, use `send_message` to send a message in the same thread ID to the sender Id you received the mention from, with content: "error".
        9. Always respond back to the sender agent even if you have no answer or error.
        9. Wait for 2 seconds and repeat the process from step 1.

        These are the list of coral tools: {coral_tools_description}
        These are the list of your tools: {agent_tools_description}"""
    ),
    ("placeholder", "{agent_scratchpad}")
])
```

This prompt ensures the Firecrawl agent:
- Listens for mentions from other agents using Coral Server tools.
- Processes instructions systematically, leveraging its tools (e.g., web scraping and crawling capabilities).
- Responds appropriately to the sender, either with the result ("answer") or an error message.
- Continuously loops to handle new instructions.

If you want the Firecrawl agent to perform specific tasks (e.g., advanced data structuring or targeted web crawling), you can update the prompt to include those requirements while maintaining the same structure.

## How to Run the Coralized Firecrawl MCP Agent Within the Coral Network

To enable the coralized Firecrawl MCP agent to interact within the Coral network, follow these steps:

1. **Start the Coral Server**:
   In a terminal, navigate to your project's root directory and start the Coral Server:
   ```bash
   ./gradlew run
   ```
   Gradle may show "83%" completion but will continue running. Check the terminal logs to confirm the server is active at `http://localhost:5555`.

2. **Run the User Interface Agent and Firecrawl Agent**:
   To interact with the coralized Firecrawl agent, run the interface agent and the newly created Firecrawl agent in separate terminals (ensure the Python virtual environment is activated in each terminal):
   - **Run the User Interface Agent**:
     ```bash
     cd coral-server/examples/langchain
     python 0_langchain_interface.py
     ```
   - **Run the Firecrawl Agent**:
     ```bash
     cd coral-server/coralizer
     python firecrawl_coral_agent.py
     ```
   Once both agents are running, they will register with the Coral Server, enabling interaction within the Coral network. The `langchain_interface_agent` will prompt for input, allowing you to send queries to the Firecrawl agent.

> **Note**: The Coral Server must be running to run the coralized agent (e.g., `firecrawl_coral_agent.py`) and the interface agent, as they register with the Coral Server upon initialization.

## How the Coralizer Works

### Agent Creation and Registration

The Coralizer simplifies the process of turning an SSE-compatible MCP into a Coral agent:

1. **Connection to the MCP SSE**: The Coralizer connects to the Firecrawl MCP's SSE endpoint (e.g., `http://localhost:3000/sse`).
2. **Requires an Agent Name by the User**: The user provides a unique agent name (e.g., `firecrawl`) when running `coralizer.py`.
3. **Identifies the Tools and Their Description of the MCP**: The Coralizer analyzes the MCP to identify its available tools and their descriptions (e.g., web scraping and crawling capabilities for Firecrawl).
4. **Creates an Agent Description Using the Tools**: Based on the identified tools, the Coralizer generates an appropriate agent description (e.g., "You are an firecrawl agent capable of scraping, crawling, and extracting data...").
5. **Takes a Sample Prompt Within the Utils to Generate the New Agent on LangChain Framework**: The Coralizer uses a sample prompt from the `utils` directory to create a new agent within the LangChain framework, ensuring compatibility with the Coral Server.
6. **Agent Registration**: Upon running `coralizer.py`, it registers the agent with the Coral Server (when the agent is run) using the provided `agentId` (`firecrawl`) and `agentDescription`. The Coral Server assigns a unique decentralized identifier (DID) to the agent, enabling discovery and interaction within the Coral network.

### Integration with Coral Network

Once coralized, the Firecrawl agent can:
- Collaborate with other Coral agents (e.g., `langchain_interface_agent`) via the Coral Server's messaging tools (`list_agents`, `create_thread`, `send_message`, `wait_for_mentions`).
- Process queries related to web scraping, crawling, or data extraction, leveraging Firecrawl's capabilities.
- Participate in multi-agent workflows, enhancing the system's overall functionality.

## Example Interaction

If paired with a `langchain_interface_agent` (as described in the [Coral Server with LangChain Agents README](https://github.com/Coral-Protocol/coral-server/tree/master/examples/langchain)), you can interact with the Firecrawl agent by entering a query like:

```
Scrape the latest articles from a news website.
```

The `langchain_interface_agent` will identify the `firecrawl` agent, create a thread, and send the instruction. The Firecrawl agent will use its tools to scrape the requested data and return the results via the Coral Server.

## Troubleshooting

- **Invalid API Keys**: Verify that `OPENAI_API_KEY` and `FIRECRAWL_API_KEY` are correctly set and valid.
- **SSE Endpoint Issues**: Confirm the Firecrawl MCP is running and the SSE endpoint (e.g., `http://localhost:3000/sse`) is correct.
- **Agent Registration Fails**: Check the `coral_base_url` and `coral_params` for accuracy. Ensure `waitForAgents` matches the number of agents in your system.

## Building on the Example

To coralize a different MCP:
1. Identify the SSE-compatible MCP and obtain its SSE endpoint.
2. Run `coralizer.py` with the new agent name and SSE endpoint.
3. Adjust `waitForAgents` based on the number of agents in your system.

## Future Potential

This demo showcases the Coralizer's ability to integrate the Firecrawl MCP with Coral. Future enhancements could include:
- **Support for Additional MCPs**: Coralizing other SSE-compatible platforms for broader use cases.
- **Automated Configuration**: Simplifying agent parameter setup for non-technical users.
- **Enhanced Tooling**: Integrating Firecrawl's advanced features (e.g., structured data generation) into Coral workflows.

## Community and Support

For questions, suggestions, or assistance, join our Discord community: [Join our Discord](https://discord.gg/cDzGHnzkwD). We're here to help!