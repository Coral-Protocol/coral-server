from dotenv import load_dotenv
from langchain.tools import Tool
from anyio import ClosedResourceError
from langchain_openai import ChatOpenAI
import json, asyncio, os, sys, traceback
from langchain.prompts import ChatPromptTemplate
from langchain_mcp_adapters.client import MultiServerMCPClient
from langchain.agents import create_tool_calling_agent, AgentExecutor


# Load environment variables
load_dotenv()

def get_tools_description(tools):
    formatted_tools = []
    for tool in tools:
        tool_info = (
            f"Tool Name: {tool.name}\n"
            f"Description: {tool.description}\n"
            f"Arguments: {json.dumps(tool.args_schema).replace('{', '{{').replace('}', '}}')}"
            f"Response Format: {tool.response_format}\n"
            "-------------------"
        )
        formatted_tools.append(tool_info)
    
    return "\n".join(formatted_tools)

async def create_agent(tools):

    system_prompt = """system_prompt"""
    system_prompt = system_prompt.format(
        formatted_tools_description=get_tools_description(tools)
    )
    prompt = ChatPromptTemplate.from_messages([
        ("system", system_prompt),
        ("placeholder", "{agent_scratchpad}")])
    model = ChatOpenAI(model="gpt-4o-mini", api_key=os.getenv("OPENAI_API_KEY"), temperature=0.3, max_tokens=4096)
    agent = create_tool_calling_agent(model, tools, prompt)
    print("Agent Created")
    return AgentExecutor(agent=agent, tools=tools, verbose=True)


async def main(agent_name, mcp_server_url):
    print(agent_name, mcp_server_url)
    retry_delay = 5  # seconds
    max_retries = 5
    retries = max_retries

    while retries > 0:
        try:
            async with MultiServerMCPClient(connections={
                "coral": {"transport": "sse", "url": mcp_server_url, "timeout": 5, "sse_read_timeout": 1200}
            }) as client:
                print('Connected to MCP servers')
                tools = client.get_tools()
                tool_names = [tool.name for tool in tools]
                print(f"List of available tools in a server: {tool_names}")
                retries = max_retries  
                await (await create_agent(agent_name, tools)).ainvoke({})
        except ClosedResourceError as e:
            retries -= 1
            print(f"Connection closed: {str(e)}. Retries left: {retries}. Retrying in {retry_delay} seconds...")
            if retries == 0:
                print("Max retries reached. Exiting.")
                break
            await asyncio.sleep(retry_delay)
        except Exception as e:
            retries -= 1
            print(f"Unexpected error: {str(e)}. Retries left: {retries}. Retrying in {retry_delay} seconds...")
            traceback.print_exc()
            if retries == 0:
                print("Max retries reached. Exiting.")
                break
            await asyncio.sleep(retry_delay)
