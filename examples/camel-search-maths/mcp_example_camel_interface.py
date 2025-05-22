import asyncio  # Manages asynchronous operations
import os  # Provide interaction with the operating system.
from time import sleep

from camel.agents import ChatAgent  # creates Agents
from camel.models import ModelFactory  # encapsulates LLM
from camel.toolkits import HumanToolkit, MCPToolkit  # import tools
from camel.toolkits.mcp_toolkit import MCPClient
from camel.types import ModelPlatformType, ModelType
from dotenv import load_dotenv

from config import PLATFORM_TYPE, MODEL_TYPE, MODEL_CONFIG, MESSAGE_WINDOW_SIZE, TOKEN_LIMIT

# load_dotenv()

from prompts import get_tools_description, get_user_message

async def main():
    # Simply add the Coral server address as a tool
    coral_url = os.getenv("CORAL_CONNECTION_URL", default = "http://localhost:5555/devmode/exampleApplication/privkey/session1/sse?waitForAgents=3&agentId=user_interaction_agent")
    server = MCPClient(coral_url, timeout=300.0)

    mcp_toolkit = MCPToolkit([server])

    async with mcp_toolkit.connection() as connected_mcp_toolkit:
        print("Connected to coral server.")
        camel_agent = await create_interface_agent(connected_mcp_toolkit)

        # Step the agent continuously
        for i in range(20):  #This should be infinite, but for testing we limit it to 20 to avoid accidental API fees
            resp = await camel_agent.astep(get_user_message())
            msgzero = resp.msgs[0]
            msgzerojson = msgzero.to_dict()
            print(msgzerojson)
            sleep(10)

async def create_interface_agent(connected_mcp_toolkit):
    tools = connected_mcp_toolkit.get_tools()
    sys_msg = (
        f"""
            You are a helpful assistant responsible for interacting with the user and working with other agents to meet the user's requests. You can interact with other agents using the chat tools.
            User interaction is your speciality. You identify as "user_interaction_agent".
            
            As the user_interaction_agent, only you can interact with the user. An agent will message you when a user has a request.
            
            If you are yet to receive any instructions from the user, wait for mentions from an agent.
            
            Make sure that all information comes from reliable sources and that all calculations are done using the appropriate tools by the appropriate agents. Make sure your responses are much more reliable than guesses! You should make sure no agents are guessing too, by suggesting the relevant agents to do each part of a task to the agents you are working with. Do a refresh of the available agents and new messages before asking the user for input.
            
            Make sure to put the name of the agent you are talking to in the mentions field of the send message tool.
            
            After working with other agents, when you are confident that you have all the information for a final answer/response, reply in the same thread as the original request message. Only do this when you are confident you have the FINAL response. Do not use that thread to send partial answers, guesses or updates. At least 2 messages from other agents should be seen before you send the final response.
            
            Here are the guidelines for using the communication tools:
            ${get_tools_description()}
            """
    )
    model = ModelFactory.create(
        model_platform=ModelPlatformType[PLATFORM_TYPE],
        model_type=ModelType[MODEL_TYPE],
        api_key=os.getenv("API_KEY"),
        model_config_dict=MODEL_CONFIG,
    )
    camel_agent = ChatAgent(  # create agent with our mcp tools
        system_message=sys_msg,
        model=model,
        tools=tools,
        message_window_size=MESSAGE_WINDOW_SIZE,
        token_limit=TOKEN_LIMIT
    )
    camel_agent.reset()
    camel_agent.memory.clear()
    return camel_agent


if __name__ == "__main__":
    asyncio.run(main())
