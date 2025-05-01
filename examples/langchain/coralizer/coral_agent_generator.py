import pydantic, traceback
from typing import Literal, Optional
from langchain_mcp_adapters.client import MultiServerMCPClient

class AgentGenerator:

    def __init__(
            self,
            agent_name: str,
            mcp_server_url: pydantic.HttpUrl,
            timeout: int = 5,
            read_timeout: int = 1200,
            mcp_connection_type: Literal["sse", "stdio"] = "sse"
    ):
        self.agent_name = agent_name
        self.mcp_server_url = mcp_server_url
        self.mcp_connection_type = mcp_connection_type
        self.client: Optional[MultiServerMCPClient] = None
        self.session = None
        self.timeout = timeout
        self.read_timeout = read_timeout
    
    async def generate_system_prompt(self) -> str:

        self.system_prompt = """
        You are `{agent_name}`, responding to mentions from `user_interaction_agent`.

        ### Agent Setup:
        1. Ensure `{agent_name}` is registered using `list_agents`. If it's not registered, call `register_agent` with the following parameters:
        - `agentId: '{agent_name}'`
        - `agentName: '{agent_name}'`

        ### Agent Loop:
        2. Continuously listen for mentions from `user_interaction_agent`:
        - Call `wait_for_mentions(agentId='{agent_name}', timeoutMs=10000)` to check for new mentions.
        - For any mention starting with "Fetch news:", follow these steps:
            1. Extract the query following "Fetch news:" and clean the string (convert to lowercase and remove unnecessary words like "fetch", "me", "the", "latest").
            2. Identify the appropriate tool to use based on the cleaned query. 
                - If a tool like `TopicGenerator` is needed, invoke it with the required arguments (e.g., `text: [cleaned query], source_country: 'us', language: 'en', number: 3`).
            3. Send the result or any error back to the thread using `send_message(senderId='{agent_name}', mentions=['user_interaction_agent'])`.

        ### Handling Invalid Mentions:
        - If no mentions are found or if the mention does not follow the expected format, continue looping and try again.

        ### Tool Integration:
        - You have access to the following tools:\n
        {{formatted_tools_description}}

        Each tool has a specific functionality and input schema. Use the appropriate tool depending on the user's query. Make sure to provide all necessary arguments and handle the response format accordingly.

        ### Logging:
        - Track the `threadId` from the mentions, but do not create new threads manually. Ensure all actions are logged for auditing purposes.
        """
        self.system_prompt = self.system_prompt.format(agent_name=str(self.agent_name) + 'Agent')

        return self.system_prompt

    async def mcp_connection(self) -> bool:
        try:
            async with MultiServerMCPClient(connections={
                "coral": {"transport": self.mcp_connection_type, "url": self.mcp_server_url, "timeout": self.timeout, "sse_read_timeout": self.read_timeout}
            }) as self.client:
                self.session = self.client.sessions
                print(f"Connected to MCP session for agent: {self.agent_name}")
                return True
        except Exception:
            print(f"{traceback.format_exc()}")
            return False