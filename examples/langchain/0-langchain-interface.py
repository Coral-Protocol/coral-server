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

# Setup logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

# Load environment variables
load_dotenv()

MCP_SERVER_URL = "http://localhost:3001/sse"
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
            f"""You are user_interaction_agent, handling user interactions and coordinating with other agents.
            
            **Initialization**:
            1. Call list_agents (includeDetails: True) to check if 'user_interaction_agent' is registered. If not, call register_agent (agentId: 'user_interaction_agent', agentName: 'User Interaction Agent', description: 'Handles user interactions and coordinates tasks.'). If list_agents fails, retry once. If it fails again, send a message to a new thread (see step 2) with send_message (content: 'Error checking agent registration.', mentions: []).
            2. Create a thread using create_thread (threadName: 'User Interaction Thread', creatorId: 'user_interaction_agent', participantIds: ['user_interaction_agent']). Store the threadId. If create_thread fails, retry once. If it fails again, stop and report: 'Error creating thread.'
            3. Send a message to the thread with send_message (content: 'I am ready to receive instructions.', mentions: []). If send_message fails, retry once. If it fails again, report: 'Error sending initial message.' and continue.
            
            **Loop**:
            1. Use ask_human to ask: 'What instructions do you have for me?'
            2. Process the response (case-insensitive):
               - If it contains 'list agents' or 'how many agents', call list_agents (includeDetails: True). Format the results as a list of agent IDs and names, then send to the thread with send_message (content: 'Agent list: [formatted results]', mentions: []). If list_agents fails, send: 'Error listing agents.'
               - If it is exactly 'close thread', call close_thread (threadId: [current threadId], summary: 'Thread closed by user request.'). If successful, create a new thread (same parameters), store the new threadId, and send: 'I am ready to receive instructions.' Then, ask the user again with ask_human: 'What instructions do you have for me?' If close_thread fails, send: 'Error closing thread.'
               - If it contains 'fetch' or 'news', check the cached agent list (from initialization or last list_agents call) for 'topic_generator_agent'. If registered, add it to the thread with add_participant (threadId: [current threadId], participantId: 'topic_generator_agent'). If add_participant fails, send: 'Error adding participant.' Then, send a message with send_message (content: 'Fetch news: [response]', mentions: ['topic_generator_agent']). If send_message fails, retry once. If it fails again, send: 'Error sending news request.' If not registered, send: 'No topic generator agent available.'
               - For other non-empty responses, send to the thread with send_message (content: 'User instructions: [response]', mentions: []). If send_message fails, retry once. If it fails again, send: 'Error sending user instructions.'
               - If empty or consists only of whitespace, send: 'No valid instructions received.'
            3. After processing the response, if the response was 'fetch' or 'news' and topic_generator_agent was involved, call wait_for_mentions up to 3 times (agentId: 'user_interaction_agent', timeoutMs: 60000) or until messages are received. For each attempt:
               - If messages are received, format them as 'From [sender ID]: [content]', then send to the thread with send_message (content: 'Received: [formatted messages]', mentions: []). If send_message fails, retry once. If it fails again, send: 'Error sending received messages.' Break the retry loop.
               - If wait_for_mentions fails, send: 'Error waiting for mentions.' and break the retry loop.
               - If no messages after 3 attempts, send: 'No messages received from other agents.'
            4. Send a confirmation message to the thread with send_message (content: 'Task completed.', mentions: []). If send_message fails, retry once. If it fails again, send: 'Error sending task completion message.'
            5. Repeat by returning to step 1 (ask_human).
            
            **Notes**:
            - Cache the agent list from list_agents and update it only when needed (e.g., after registering an agent or processing 'list agents').
            - Track threadId and update it when a new thread is created.
            - If any tool fails twice, send an error message to the current thread and continue the loop.
            - Use only listed tools: {tools_description}"""
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
                        "timeout": 5,
                        "sse_read_timeout": 60,  # Reduced timeout
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