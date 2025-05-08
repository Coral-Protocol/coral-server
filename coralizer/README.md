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
- Coral Server running (typically at `http://localhost:5555`)

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
/usr/lib/python3.12/asyncio/events.py:88: UserWarning: WARNING! response_format is not default parameter.
                response_format was transferred to model_kwargs.
                Please confirm that response_format is what you intended.
  self._context.run(self._callback, *self._args)
File 'firecrawl_coral_agent.py' created successfully.
```

### 6. Verify the Agent Configuration

After running the Coralizer, the created agent must be verified to ensure it integrates correctly with the Coral Server. Check the following configuration parameters:

```python
coral_base_url = "http://localhost:5555/devmode/exampleApplication/privkey/session1/sse"
coral_params = {
    "waitForAgents": 2,
    "agentId": "firecrawl",
    "agentDescription": "You are an firecrawl agent capable of scraping, crawling, and extracting data from web pages and URLs, as well as performing deep research and generating structured data for analysis."
}
```

- **coral_base_url**: Ensure this matches the Coral Server's URL (typically `http://localhost:5555/devmode/exampleApplication/privkey/session1/sse`).
- **waitForAgents**: Set to `2` if running in a multi-agent system with two agents (e.g., alongside the `user_interface_agent`). Adjust to `1` if running the Firecrawl agent alone.
- **agentId**: Must be unique (`firecrawl` in this case).
- **agentDescription**: Verify it aligns with your multi-agent system's requirements. The provided description reflects the Firecrawl agent's capabilities.

### 7. Start the Coral Server

The Coralizer connects to the Coral Server, which must be running. In a separate terminal, navigate to your project's root directory and start the server:

```bash
./gradlew run
```

Gradle may show "83%" completion but will continue running. Check the terminal logs to confirm the server is active at `http://localhost:5555`.

> **Note**: The Coral Server must be running before starting the Coralizer, as the agent registers with it upon initialization.

## How the Coralizer Works

### Agent Creation and Registration

The Coralizer simplifies the process of turning an SSE-compatible MCP into a Coral agent:

1. **Connection to the MCP**: The Coralizer connects to the Firecrawl MCP's SSE endpoint (e.g., `http://localhost:3000/sse`).
2. **Agent Registration**: Upon running `coralizer.py`, it registers the agent with the Coral Server using the provided `agentId` (`firecrawl`) and `agentDescription`. The Coral Server assigns a unique decentralized identifier (DID) to the agent, enabling discovery and interaction within the Coral network.
3. **Tool Invocation**: The Coralizer enables the Firecrawl MCP to receive inputs from the Coral network and invoke its tools (e.g., web scraping, crawling, and data extraction) as needed.

### Integration with Coral Network

Once coralized, the Firecrawl agent can:
- Collaborate with other Coral agents (e.g., `user_interface_agent` or `world_news_agent`) via the Coral Server's messaging tools (`list_agents`, `create_thread`, `send_message`, `wait_for_mentions`).
- Process queries related to web scraping, crawling-generation) into Coral workflows.

## Community and Support

For questions, suggestions, or assistance, join our Discord community: [Join our Discord](https://discord.gg/cDzGHnzkwD). We're here to help!