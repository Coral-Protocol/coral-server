import asyncio
import os
import json
import logging
from langchain_mcp_adapters.client import MultiServerMCPClient
from langchain.prompts import ChatPromptTemplate
from langchain_openai import ChatOpenAI
from langchain.agents import create_tool_calling_agent, AgentExecutor
from langchain.tools import Tool
from dotenv import load_dotenv
from anyio import ClosedResourceError
import urllib.parse

# Setup logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

# Load environment variables
load_dotenv()

base_url = "http://localhost:5555/devmode/exampleApplication/privkey/session1/sse"
params = {
    "waitForAgents": 2,
    "agentId": "user_interface_agent",
    "agentDescription": "You are user_interaction_agent, responsible for engaging with users, processing instructions, and coordinating with other agents"
}
query_string = urllib.parse.urlencode(params)
MCP_SERVER_URL = f"{base_url}?{query_string}"

AGENT_NAME = "user_interaction_agent"

def get_tools_description(tools):
    return "\n".join(
        f"Tool: {tool.name}, Schema: {json.dumps(tool.args).replace('{', '{{').replace('}', '}}')}"
        for tool in tools
    )

async def ask_human_tool(question: str) -> str:
    print(f"Agent asks: {question}")
    return input("Your response: ")

async def create_interface_agent(client, tools):
    tools_description = get_tools_description(tools)
    
    prompt = ChatPromptTemplate.from_messages([
            (
                "system",
                f"""You are user_interaction_agent, responsible for engaging with users, processing instructions, and coordinating with other agents.

                **Initialization**:
                1. Create a thread using create_thread (threadName: 'User Interaction', participantIds: ['user_interaction_agent']). Store the threadId. If create_thread fails, retry once. On failure, log: 'Fatal Error: Failed to create thread.' and stop.
                2. Send send_message (content: 'Hello! How can I assist you?', mentions: []). On failure, retry once, log: 'Error: Failed to send initial message.', and continue.

                **Loop**:
                1. Prompt with ask_human: 'How can I assist you today?'
                2. Process response (case-insensitive):
                - If contains 'list agents' or 'how many agents', call list_agents (includeDetails: True). Format as: 'Registered agents: [agentId (agentName)]'. Update cached agent list. Send send_message (content: '[formatted results]', mentions: []). On failure, send: 'Error: Could not retrieve agent list.'
                - If 'close thread', call close_thread (threadId: [current threadId], summary: 'Closed by user.'). On success, create a new thread (threadName: 'User Interaction', participantIds: ['user_interaction_agent']), store threadId, send: 'New thread created. Ready to assist.' On failure, send: 'Error: Could not close thread.'
                - If contains 'fetch' or 'news', call list_agents (includeDetails: True) to refresh the cached agent list. If 'world_news_agent' is registered, call add_participant (threadId: [current threadId], participantId: 'world_news_agent'). On failure, send: 'Error: Could not add world_news_agent.' Then, send_message (content: '[full response]', mentions: ['world_news_agent']). On send_message failure, retry once, then send: 'Error: Could not send news request.' If 'world_news_agent' is not registered, send: 'Error: World news agent not available.' and skip to step 4.
                - For other non-empty responses, send_message (content: 'Processing: [response]', mentions: []). On failure, retry once, then send: 'Error: Could not process request.'
                - If empty or whitespace, send: 'Please provide valid instructions.'
                3. If response involved 'fetch' or 'news' and 'world_news_agent' was messaged successfully, call wait_for_mentions (timeoutMs: 60000) up to 3 times:
                - If messages received, format as: 'From [senderId]: [content]' and send_message (content: '[formatted messages]', mentions: []). On failure, retry once, then send: 'Error: Could not send response.'
                - If wait_for_mentions fails, send: 'Error: Failed to check responses.'
                - If no messages after 3 attempts, send: 'No response from world_news_agent.'
                4. Send send_message (content: 'Request processed.', mentions: []). On failure, retry once, then send: 'Error: Could not send confirmation.'
                5. Return to step 1.

                **Notes**:
                - Cache agent list, updated after every list_agents call.
                - Track threadId and update for new threads.
                - Limit thread creation and participant addition to one retry.
                - Log all actions and errors with timestamps.
                - Use only listed tools: {get_tools_description(tools)}"""
            ),
            ("placeholder", "{agent_scratchpad}")
        ])

    model = ChatOpenAI(
        model="gpt-4o-mini",
        api_key=os.getenv("OPENAI_API_KEY"),
        temperature=0.3,
        max_tokens=4096
    )

    agent = create_tool_calling_agent(model, tools, prompt)
    return AgentExecutor(agent=agent, tools=tools, verbose=True)

async def main():
    max_retries = 3
    for attempt in range(max_retries):
        try:
            async with MultiServerMCPClient(
                connections={
                    "coral": {
                        "transport": "sse",
                        "url": MCP_SERVER_URL,
                        "timeout": 40,
                        "sse_read_timeout": 60,
                    }
                }
            ) as client:
                logger.info(f"Connected to MCP server at {MCP_SERVER_URL}")
                tools = client.get_tools() + [Tool(
                    name="ask_human",
                    func=None,
                    coroutine=ask_human_tool,
                    description="Ask the user a question and wait for a response."
                )]
                logger.info(f"Tools Description:\n{get_tools_description(tools)}")
                await (await create_interface_agent(client, tools)).ainvoke({})
        except ClosedResourceError as e:
            logger.error(f"ClosedResourceError on attempt {attempt + 1}: {e}")
            if attempt < max_retries - 1:
                logger.info("Retrying in 5 seconds...")
                await asyncio.sleep(5)
                continue
            else:
                logger.error("Max retries reached. Exiting.")
                raise
        except Exception as e:
            logger.error(f"Unexpected error on attempt {attempt + 1}: {e}")
            if attempt < max_retries - 1:
                logger.info("Retrying in 5 seconds...")
                await asyncio.sleep(5)
                continue
            else:
                logger.error("Max retries reached. Exiting.")
                raise

if __name__ == "__main__":
    asyncio.run(main())
