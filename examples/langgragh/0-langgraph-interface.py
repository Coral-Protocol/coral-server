import asyncio
import os
from typing import List, Dict, Any
from langchain_mcp_adapters.client import MultiServerMCPClient
from langchain.prompts import ChatPromptTemplate
from langchain_openai import ChatOpenAI
from langgraph.prebuilt import create_react_agent
from langgraph.checkpoint.memory import MemorySaver
from langchain_core.messages import AIMessage, HumanMessage, ToolMessage
from langchain.tools import Tool
import json

MCP_SERVER_URL = "http://localhost:3001/sse"
AGENT_NAME = "user_interaction_agent"

def get_tools_description(tools):
    result = [{"Tool": tool.name, "schema": tool.args} for tool in tools]
    return "\n".join(
        [f"Tool: {item['Tool']}, Schema: {json.dumps(item['schema']).replace('{', '{{').replace('}', '}}')}" 
         for item in result]
    )

async def ask_human_tool(question: str) -> str:
    print(f"Agent asks: {question}")
    return input("Your response: ")

async def main():
    client = None
    try:
        client = MultiServerMCPClient(
            connections={
                "coral": {
                    "transport": "sse",
                    "url": MCP_SERVER_URL,
                    "timeout": 5,
                    "sse_read_timeout": 300,
                }
            }
        )
        async with client:
            print(f"Connected to MCP server at {MCP_SERVER_URL}")
            
            # Get MCP tools and add ask_human tool
            tools = client.get_tools()
            tools.append(Tool(
                name="ask_human",
                func=None,
                coroutine=ask_human_tool,
                description="Ask the user a question and wait for a response."
            ))
            
            tools_description = get_tools_description(tools)
            print(f"Tools Description:\n{tools_description}")
            
            # Initialize LangGraph agent
            memory = MemorySaver()
            agent = await create_interface_agent(client, tools, memory)
            
            # Continuous loop for user interaction
            thread_id = "user_interaction_thread"
            current_thread_id = None
            initialized = False
            
            while True:
                try:
                    # Initial setup: Register agent and create thread
                    if not initialized:
                        response = await agent.ainvoke(
                            input={
                                "messages": [HumanMessage(content="Initialize agent and thread")]
                            },
                            config={"configurable": {"thread_id": thread_id, "recursion_limit": 50}}
                        )
                        messages = response["messages"]
                        for msg in messages[-1:]:
                            if isinstance(msg, AIMessage) and msg.content and "Thread created with ID" in msg.content:
                                current_thread_id = msg.content.split("ID: ")[-1].strip()
                                initialized = True
                                print(f"Initialized with thread ID: {current_thread_id}")
                    
                    # Ask user for instructions
                    response = await agent.ainvoke(
                        input={
                            "messages": [HumanMessage(content=f"Ask user for instructions in thread {current_thread_id}")]
                        },
                        config={"configurable": {"thread_id": thread_id, "recursion_limit": 50}}
                    )
                    
                    # Process response
                    messages = response["messages"]
                    last_message = messages[-1]
                    if isinstance(last_message, AIMessage) and last_message.content:
                        print(f"Agent response: {last_message.content}")
                        # Check if a new thread was created (e.g., after close_thread)
                        if "Thread created with ID" in last_message.content:
                            current_thread_id = last_message.content.split("ID: ")[-1].strip()
                            print(f"Updated thread ID: {current_thread_id}")
                    
                except KeyboardInterrupt:
                    print("Terminating agent loop.")
                    break
                except Exception as e:
                    print(f"Error in agent loop: {str(e)}")
                    if "recursion_limit" in str(e).lower():
                        print("Recursion limit reached. Resetting agent state.")
                        initialized = False  # Reinitialize to avoid stuck state
                    await asyncio.sleep(1)  # Prevent tight loop on errors
    
    except Exception as e:
        print(f"Failed to initialize MCP client: {str(e)}")
    finally:
        if client:
            print("Cleaning up MCP client...")
            # Ensure client is closed properly
            await client.aclose()

async def create_interface_agent(client, tools, memory):
    tools_description = get_tools_description(tools)
    
    # Simplified system prompt to avoid internal looping
    prompt = ChatPromptTemplate.from_messages([
        (
            "system",
            f"""You are a helpful assistant named 'user_interaction_agent'. Your role is to interact with users and coordinate with other agents via threads. 

            For each input, perform the following steps exactly as instructed, without looping internally:

            1. If the input is "Initialize agent and thread":
               - Use list_agents to check if 'user_interaction_agent' is registered.
               - If not registered, call register_agent with:
                 - agentId: 'user_interaction_agent'
                 - agentName: 'User Interaction Agent'
                 - description: 'Responsible for interacting with users and coordinating tasks with other agents.'
               - Call create_thread with:
                 - threadName: 'User Interaction Thread'
                 - creatorId: 'user_interaction_agent'
                 - participantIds: ['user_interaction_agent']
               - Send a message to the thread using send_message with:
                 - content: 'I am ready to receive instructions.'
                 - mentions: []
               - Return: 'Thread created with ID: [threadId]'

            2. If the input starts with "Ask user for instructions":
               - Extract the current threadId from the input (e.g., 'Ask user for instructions in thread [threadId]').
               - Use ask_human to ask: 'What instructions do you have for me?'
               - Process the user response:
                 - If empty or invalid, call send_message with:
                   - threadId: [current threadId]
                   - senderId: 'user_interaction_agent'
                   - content: 'No valid instructions received from user.'
                   - mentions: []
                 - If the response contains 'list agents' or 'how many agents' (case-insensitive), call list_agents with includeDetails: True, then send_message with:
                   - threadId: [current threadId]
                   - senderId: 'user_interaction_agent'
                   - content: 'Agent list: [results]'
                   - mentions: []
                 - If the response is exactly 'close thread', call close_thread with:
                   - threadId: [current threadId]
                   - summary: 'Thread closed by user request.'
                   Then, call create_thread with:
                   - threadName: 'User Interaction Thread'
                   - creatorId: 'user_interaction_agent'
                   - participantIds: ['user_interaction_agent']
                   Then, send_message with:
                   - content: 'I am ready to receive instructions.'
                   - mentions: []
                   Return: 'Thread created with ID: [new threadId]'
                 - For any other response, call send_message with:
                   - threadId: [current threadId]
                   - senderId: 'user_interaction_agent'
                   - content: 'User instructions: [response]'
                   - mentions: []
               - Call wait_for_mentions ONCE with:
                 - agentId: 'user_interaction_agent'
                 - timeoutMs: 10000
               - If no mentions are received, call send_message with:
                 - threadId: [current threadId]
                 - senderId: 'user_interaction_agent'
                 - content: 'No messages received from other agents.'
                 - mentions: []
               - Return: 'Processed user instructions and checked mentions.'

            Do NOT loop internally or call ask_human multiple times in a single invocation. Do NOT call wait_for_mentions more than once per invocation. Only use the tools listed below. Ensure all tool calls include the current threadId where required.

            Available tools:
            {tools_description}"""
        ),
        (
            "placeholder",
            "{messages}"
        )
    ])

    model = ChatOpenAI(
        model="gpt-4o-mini",
        api_key=os.getenv("OPENAI_API_KEY"),
        temperature=0.3,
        max_tokens=4096
    )

    # Create ReAct agent with increased recursion limit
    agent = create_react_agent(
        model=model,
        tools=tools,
        checkpointer=memory,
        prompt=prompt
    )

    return agent

if __name__ == "__main__":
    asyncio.run(main())
