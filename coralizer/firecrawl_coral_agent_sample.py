import urllib.parse
from dotenv import load_dotenv
import os, json, asyncio, traceback
from langchain.chat_models import init_chat_model
from langchain.prompts import ChatPromptTemplate
from langchain_mcp_adapters.client import MultiServerMCPClient
from langchain.agents import create_tool_calling_agent, AgentExecutor

load_dotenv()

coral_base_url = "http://localhost:5555/devmode/exampleApplication/privkey/session1/sse"
coral_params = {
    "waitForAgents": 2,
    "agentId": "firecrawl",
    "agentDescription": "You are an firecrawl agent capable of scraping, crawling, and extracting data from web pages and URLs, as well as performing deep research and generating structured data for analysis."
}

query_string = urllib.parse.urlencode(coral_params)

def get_tools_description(tools):
    return "\n".join(
        f"Tool: {tool.name}, Schema: {json.dumps(tool.args).replace('{', '{{').replace('}', '}}')}"
        for tool in tools
    )

async def create_agent(coral_tools, agent_tools):
    coral_tools_description = get_tools_description(coral_tools)
    agent_tools_description = get_tools_description(agent_tools)
    combined_tools = coral_tools + agent_tools
    prompt = ChatPromptTemplate.from_messages([
        (
            "system",
            f"""You are an agent interacting with the tools from Coral Server and having your own tools. Your task is to perform any instructions coming from any agent. 
            Follow these steps in order:
            1. Call wait_for_mentions from coral tools (timeoutMs: 30000) to receive mentions from other agents.
            2. When you receive a mention, keep the thread ID and the sender ID.
            3. Take 2 seconds to think about the content (instruction) of the message and check only from the list of your tools available for you to action.
            4. Check the tool schema and make a plan in steps for the task you want to perform.
            5. Only call the tools you need to perform for each step of the plan to complete the instruction in the content.
            6. Take 3 seconds and think about the content and see if you have executed the instruction to the best of your ability and the tools. Make this your response as "answer".
            7. Use `send_message` from coral tools to send a message in the same thread ID to the sender Id you received the mention from, with content: "answer".
            8. If any error occurs, use `send_message` to send a message in the same thread ID to the sender Id you received the mention from, with content: "error".
            9. Always respond back to the sender agent even if you have no answer or error.
            9. Wait for 2 seconds and repeat the process from step 1.

            These are the list of coral tools: {coral_tools_description}
            These are the list of your tools: {agent_tools_description}"""
                ),
                ("placeholder", "{agent_scratchpad}")

    ])

    model = init_chat_model(
            model="gpt-4o-mini",
            model_provider="openai",
            api_key=os.getenv("OPENAI_API_KEY"),
            temperature=0.3,
            max_tokens=16000
        )
    agent = create_tool_calling_agent(model, combined_tools, prompt)
    return AgentExecutor(agent=agent, tools=combined_tools, verbose=True)

async def main():
	CORAL_SERVER_URL = f"{coral_base_url}?{query_string}"
	MCP_SERVER_URL = 'http://localhost:3000/sse'
	async with MultiServerMCPClient(
		connections = {
			"coral": {
				"transport": "sse",
				"url": CORAL_SERVER_URL,
				"timeout": 300,
				"sse_read_timeout": 300
			},
			"mcp": {
				"transport": "sse",
				"url": MCP_SERVER_URL,
				"timeout": 300,
				"sse_read_timeout": 300
			}
		}
    ) as multi_connection_client:
			print("Multi Server Connection Established")
			agent_tools = multi_connection_client.server_name_to_tools['mcp']
			coral_tools = multi_connection_client.server_name_to_tools['coral']
			print(f"Coral tools count: {len(coral_tools)} and agent tools count: {len(agent_tools)}")
			
			agent_executor = await create_agent(coral_tools, agent_tools)
			
			while True:
				try:
					print("Starting new agent invocation")
					await agent_executor.ainvoke({"agent_scratchpad": []})
					print("Completed agent invocation, restarting loop")
					await asyncio.sleep(1)
				except Exception as e:
					print(f"Error in agent loop: {str(e)}")
					print(traceback.format_exc())
					await asyncio.sleep(5)

if __name__ == "__main__":
    asyncio.run(main())