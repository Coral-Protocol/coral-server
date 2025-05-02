import json, asyncio, os, sys, traceback
from dotenv import load_dotenv
from langchain.tools import Tool
from anyio import ClosedResourceError
from langchain_openai import ChatOpenAI
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
    system_prompt = """
        You are `firecrawl_agentAgent`, responding to mentions from `user_interaction_agent`.

        ### Agent Setup:
        1. Ensure `firecrawl_agentAgent` is registered using `list_agents`. If it's not registered, call `register_agent` with the following parameters:
        - `agentId: 'firecrawl_agentAgent'`
        - `agentName: 'firecrawl_agentAgent'`

        ### Agent Loop:
        2. Continuously listen for mentions from `user_interaction_agent`:
        - Call `wait_for_mentions(agentId='firecrawl_agentAgent', timeoutMs=10000)` to check for new mentions.
        - For any mention starting with "Fetch news:", follow these steps:
            1. Extract the query following "Fetch news:" and clean the string (convert to lowercase and remove unnecessary words like "fetch", "me", "the", "latest").
            2. Identify the appropriate tool to use based on the cleaned query. 
                - If a tool like `TopicGenerator` is needed, invoke it with the required arguments (e.g., `text: [cleaned query], source_country: 'us', language: 'en', number: 3`).
            3. Send the result or any error back to the thread using `send_message(senderId='firecrawl_agentAgent', mentions=['user_interaction_agent'])`.

        ### Handling Invalid Mentions:
        - If no mentions are found or if the mention does not follow the expected format, continue looping and try again.

        ### Tool Integration:
        - You have access to the following tools:

        {formatted_tools_description}

        Each tool has a specific functionality and input schema. Use the appropriate tool depending on the user's query. Make sure to provide all necessary arguments and handle the response format accordingly.

        ### Logging:
        - Track the `threadId` from the mentions, but do not create new threads manually. Ensure all actions are logged for auditing purposes.
        """
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

async def main(agent_name="firecrawl_agent", mcp_server_url="http://localhost:3000/sse"):
    print(agent_name, mcp_server_url)
    retry_delay = 5  # seconds
    max_retries = 5
    retries = max_retries
    coral_server_url = "http://localhost:3001/sse"  # second coral server

    while retries > 0:
        try:
            async with MultiServerMCPClient(connections={
                "mcp_server_connection": {"transport": "sse", "url": mcp_server_url, "timeout": 5, "sse_read_timeout": 1200},
                "coral_server_connection": {"transport": "sse", "url": coral_server_url, "timeout": 5, "sse_read_timeout": 1200}
            }) as mcp_server:
                print('Connected to MCP servers')
                # Get tools from both servers
                tools = mcp_server.get_tools()
                tool_names = [tool.name for tool in tools]
                print(f"List of available tools in servers: {tool_names}")
                retries = max_retries  
                # await (await create_agent(tools)).ainvoke({})
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

if __name__ == "__main__":
    asyncio.run(main())