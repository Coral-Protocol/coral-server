import pydantic, traceback, json
from typing import Literal, Optional
from langchain_openai import ChatOpenAI
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
    
    def get_tools_description(self):
        tools = self.client.get_tools()
        return "\n".join(
            f"Tool: {tool.name}, Schema: {json.dumps(tool.args).replace('{', '{{').replace('}', '}}')}"
            for tool in tools
        )

    def get_mcp_description(self, agent_name):
        formatted_tools = self.get_tools_description()
        system_prompt = (
            "You are an AI system tasked with summarizing the purpose and capabilities of an agent, "
            "based solely on the tools it has access to. "
            "Below is a list of tools available to the agent:\n"
            f"{formatted_tools}\n\n"
            "Using this information, write a concise 1-2 sentence description of what this agent is capable of doing. "
            "Focus on the agent's core functionality as inferred from the tools. "
            "Your response must be a valid JSON object in the following format:\n"
            f"The description must always start with `You are an {agent_name} agent capable of...`"
            "{\"description\": \"<insert your concise summary here>\"}"
        )
        openai_helper = ChatOpenAI(
            model="gpt-4o-mini",
            temperature=0,
            response_format={"type": "json_object"}
        )

        response = openai_helper.invoke(system_prompt)
        response = response.content
        description = json.loads(response)["description"]

        return description



    async def mcp_connection(self):
        try:
            async with MultiServerMCPClient(connections={
                "mcp": {"transport": self.mcp_connection_type, "url": self.mcp_server_url, "timeout": self.timeout, "sse_read_timeout": self.read_timeout}
            }) as self.client:
                self.session = self.client.sessions
                print(f"Connected to MCP session for agent: {self.agent_name}")
                return True
        except Exception as e:
            print(f"Unable to connect with MCP server: {str(e)}")
            return False
