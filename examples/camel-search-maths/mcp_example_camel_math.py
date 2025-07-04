import asyncio
import os
from time import sleep

from camel.agents import ChatAgent
from camel.models import ModelFactory
from camel.toolkits import MCPToolkit, MathToolkit
from camel.utils.mcp_client import ServerConfig
from camel.toolkits.mcp_toolkit import MCPClient
from camel.types import ModelPlatformType, ModelType
from prompts import get_tools_description, get_user_message
from dotenv import load_dotenv
from config import PLATFORM_TYPE, MODEL_TYPE, MODEL_CONFIG, MESSAGE_WINDOW_SIZE, TOKEN_LIMIT

# load_dotenv()

async def main():
    # Simply add the Coral server address as a tool
    print("Starting MCP client...")
    coral_url = os.getenv("CORAL_CONNECTION_URL", default = "http://localhost:5555/devmode/exampleApplication/privkey/session1/sse?agentId=math_agent")
    server = MCPClient(ServerConfig(url=coral_url, timeout=3000000.0, sse_read_timeout=3000000.0, terminate_on_close=True, prefer_sse=True), timeout=3000000.0)
    mcp_toolkit = MCPToolkit([server])

    async with mcp_toolkit as connected_mcp_toolkit:
        tools = connected_mcp_toolkit.get_tools() + MathToolkit().get_tools()
        camel_agent = await create_math_agent(tools)

        # Step the agent continuously
        for i in range(20):  #This should be infinite, but for testing we limit it to 20 to avoid accidental API fees
            resp = await camel_agent.astep(get_user_message())
            msgzero = resp.msgs[0]
            msgzerojson = msgzero.to_dict()
            print(msgzerojson)
            sleep(10)


async def create_math_agent(tools):
    sys_msg = (
        f"""
            You are a helpful assistant responsible for doing maths 
            operations. You can interact with other agents using the chat tools.
            Mathematics are your speciality.  You identify as "math_agent".
            
            If you have no tasks yet, call the wait for mentions tool. Don't ask agents for tasks, wait for them to ask you.
            {os.getenv("CORAL_PROMPT_SYSTEM", default = "")}
            
            Here are the guidelines for using the communication tools:
            {get_tools_description()}
            """
    )
    model = ModelFactory.create(
        model_platform=ModelPlatformType[PLATFORM_TYPE],
        model_type=ModelType[MODEL_TYPE],
        api_key=os.getenv("API_KEY"),
        model_config_dict=MODEL_CONFIG,
    )
    camel_agent = ChatAgent(
        system_message=sys_msg,
        model=model,
        tools=tools,
        message_window_size=MESSAGE_WINDOW_SIZE,
        token_limit=TOKEN_LIMIT
    )
    return camel_agent


if __name__ == "__main__":
    asyncio.run(main())
