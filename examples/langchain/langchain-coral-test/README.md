# Coral Server Test

The `langchain-coral-test.ipynb` is a Jupyter notebook that tests the Coral server using `langchain_mcp_adapters` for agent communication.

## Overview
This project tests Coral server tools by registering two agents, listing them, creating a chat thread, and exchanging messages.

### Coral Server Tools and Schemas
- **`list_agents`**: Lists connected agents.
  - **Schema**: `{"includeDetails": {"type": "boolean", "description": "Whether to include agent details in the response"}}`
- **`create_thread`**: Creates a thread with participants.
  - **Schema**: `{"threadName": {"type": "string", "description": "Name of the thread"}, "participantIds": {"type": "array", "description": "List of agent IDs to include as participants", "items": {"type": "string"}}}`
- **`add_participant`**: Adds an agent to a thread.
  - **Schema**: `{"threadId": {"type": "string", "description": "ID of the thread"}, "participantId": {"type": "string", "description": "ID of the agent to add"}}`
- **`remove_participant`**: Removes an agent from a thread.
  - **Schema**: `{"threadId": {"type": "string", "description": "ID of the thread"}, "participantId": {"type": "string", "description": "ID of the agent to remove"}}`
- **`close_thread`**: Closes a thread with a summary.
  - **Schema**: `{"threadId": {"type": "string", "description": "ID of the thread to close"}, "summary": {"type": "string", "description": "Summary of the thread"}}`
- **`send_message`**: Sends a message in a thread.
  - **Schema**: `{"threadId": {"type": "string", "description": "ID of the thread"}, "content": {"type": "string", "description": "Content of the message"}, "mentions": {"type": "array", "description": "List of agent IDs to mention in the message", "items": {"type": "string"}}}`
- **`wait_for_mentions`**: Waits for messages mentioning the agent.
  - **Schema**: `{"timeoutMs": {"type": "number", "description": "Timeout in milliseconds (default: 30000)"}}`

### Flow
1. **Register Agents**: Connect `test_agent` and `test_agent2` to the Coral server.
2. **List Agents**: Use `list_agents` to display connected agents.
3. **Create Thread**: Use `create_thread` to set up `test_thread`.
4. **Exchange Messages**: Use `send_message` and `wait_for_mentions` for `test_agent` to send a message and `test_agent2` to receive and reply.

## Prerequisites
- Python 3.8+
- Jupyter Notebook
- Coral server at `http://localhost:5555`
- `langchain_mcp_adapters` library

## Installation
1. Clone repo: `git clone <repo_url>`
2. Set up virtual environment: `python -m venv venv && source venv/bin/activate`
3. Install dependencies: `pip install langchain-mcp-adapters jupyter`
4. Start Jupyter: `jupyter notebook`

## How to Test
1. Open `langchain-coral-test.ipynb` in Jupyter.
2. Run cells in order:
   - Register `test_agent` and `test_agent2`.
   - List agents with `list_agents`.
   - Create a thread with `create_thread`.
   - Start `wait_for_mentions` for `test_agent2`, send a message with `send_message` from `test_agent`, and reply from `test_agent2`.
3. Run `wait_for_mentions` before `send_message` to capture messages.

## Troubleshooting
- **Server Issues**: Verify Coral server is running at `http://localhost:5555`.
- **Timeouts**: Run `wait_for_mentions` first or increase timeout.
- **Tool Errors**: Check tool availability in cell outputs.

## License
MIT License. See `LICENSE` for details.