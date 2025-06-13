import asyncio
import os
import json
from camel.toolkits.mcp_toolkit import MCPClient
from camel.toolkits import HumanToolkit, MCPToolkit 
from camel.models import ModelFactory
from camel.types import ModelPlatformType, ModelType
from camel.agents import ChatAgent
import urllib.parse
import base64
from mcp import ClientSession
from mcp.types import BlobResourceContents, ResourceContents, TextResourceContents
from typing import Union, Optional, List

async def get_tools_description(tools):
    descriptions = []
    for tool in tools:
        tool_name = getattr(tool.func, '__name__', 'unknown_tool')
        schema = tool.get_openai_function_schema() or {}
        arg_names = list(schema.get('parameters', {}).get('properties', {}).keys()) if schema else []
        description = tool.get_function_description() or 'No description'
        schema_str = json.dumps(schema, default=str).replace('{', '{{').replace('}', '}}')
        descriptions.append(
            f"Tool: {tool_name}, Args: {arg_names}, Description: {description}, Schema: {schema_str}"
        )
    return "\n".join(descriptions)

class SimpleBlob:
    """A simple class to hold resource data, MIME type, and metadata."""
    def __init__(self, data: Union[str, bytes], mime_type: Optional[str], metadata: dict):
        self.data = data
        self.mime_type = mime_type
        self.metadata = metadata

    @classmethod
    def from_data(cls, data: Union[str, bytes], mime_type: Optional[str] = None, metadata: Optional[dict] = None):
        """Create a SimpleBlob from data."""
        return cls(data=data, mime_type=mime_type, metadata=metadata or {})
    
def convert_mcp_resource_to_blob(
    resource_uri: str,
    contents: ResourceContents,
) -> SimpleBlob:
    if isinstance(contents, TextResourceContents):
        data = contents.text
    elif isinstance(contents, BlobResourceContents):
        data = base64.b64decode(contents.blob)
    else:
        raise ValueError(f"Unsupported content type for URI {resource_uri}")
    return SimpleBlob.from_data(
        data=data,
        mime_type=contents.mimeType,
        metadata={"uri": resource_uri},
    )
    
async def get_mcp_resource(session: ClientSession, uri: str) -> List[SimpleBlob]:
    contents_result = await session.read_resource(uri)
    if not contents_result.contents or len(contents_result.contents) == 0:
        return []
    return [
        convert_mcp_resource_to_blob(uri, content) for content in contents_result.contents
    ]

async def load_mcp_resources(
    session: ClientSession,
    uris: Union[str, List[str], None] = None,
) -> List[SimpleBlob]:
    blobs = []
    if uris is None:
        resources_list = await session.list_resources()
        uri_list = [r.uri for r in resources_list.resources]
    elif isinstance(uris, str):
        uri_list = [uris]
    else:
        uri_list = uris
    for uri in uri_list:
        try:
            resource_blobs = await get_mcp_resource(session, uri)
            blobs.extend(resource_blobs)
        except Exception as e:
            print(f"Error fetching resource {uri}: {e}")
            continue
    return blobs

async def get_resources(
    client: MCPClient,
    uris: Union[str, List[str], None] = None
) -> List[SimpleBlob]:
    """Get resources from the MCP server.

    Args:
        client: MCPClient instance
        uris: Optional resource URI or list of URIs to load. If None, fetches all resources.

    Returns:
        A list of SimpleBlob objects
    """
    if client.session is None:
        raise RuntimeError("MCPClient is not connected or session is not initialized.")
    try:
        return await load_mcp_resources(client.session, uris)
    except Exception as e:
        raise RuntimeError(f"Error fetching resources: {e}")

async def main():
    base_url_1 = "http://localhost:5555/devmode/exampleApplication/privkey/session1/sse"
    params_1 = {
        "waitForAgents": 1,
        "agentId": "user_interface_agent",
        "agentDescription": "You are user_interaction_agent, responsible for engaging with users, processing instructions, and coordinating with other agents"
    }
    query_string = urllib.parse.urlencode(params_1)
    MCP_SERVER_URL_1 = f"{base_url_1}?{query_string}"
    
    coral_server = MCPClient(
        command_or_url=MCP_SERVER_URL_1,
        timeout=300.0
    )
    await coral_server.__aenter__()
    print(f"Connected to MCP server as user_interface_agent at {MCP_SERVER_URL_1}")

    model = ModelFactory.create(
        model_platform=ModelPlatformType.OPENAI,
        model_type=ModelType.GPT_4O_MINI,
        api_key=os.getenv("OPENAI_API_KEY"),
        model_config_dict={"temperature": 0.3, "max_tokens": 16000},
    )

    while True:
        try:
            resources = await get_resources(coral_server, uris=None)
            if not resources:
                agent_resources = "NA"
                print("No resources found.")
            else:
                agent_resources = "\n".join(str(blob.data) for blob in resources)
                print("Resources fetched:")
                # for blob in resources:
                #     print(blob.data)
        except Exception as e:
            print(f"Error retrieving resources: {e}")
            agent_resources = "NA"

        resource_sys_message = agent_resources

        mcp_toolkit = MCPToolkit([coral_server])
        tools = mcp_toolkit.get_tools() + HumanToolkit().get_tools()
        tools_description = await get_tools_description(tools)

        sys_msg = (
            f"""You are an agent interacting with the tools from Coral Server and having your own Human Tool to ask have a conversation with Human.
            Your resources, provided in `resource_sys_message`, contain thread-based conversations between agents in XML format. 
            Each thread includes details such as thread ID, participant agent IDs, message content, and timestamps. 
            Use these resources to understand past agent interactions and inform your decisions when coordinating with other agents or responding to user queries.

            Follow these steps in order:
            1. Use `list_agents` to list all connected agents and get their descriptions.
            2. Use `ask_human_via_console` to ask, "How can I assist you today?" and capture expect response.
            3. Take 2 seconds to think and understand the user's intent and decide the right agent to handle the request based on list of agents. 
            4. If the user wants any information about the coral server, use the tools to get the information and pass it to the user. Do not send any message to any other agent, just give the information and go to Step 1.
            5. Once you have the right agent, use `create_thread` to create a thread with the selected agent. If no agent is available, use the `ask_human` tool to specify the agent you want to use.
            6. Use your logic to determine the task you want that agent to perform and create a message for them which instructs the agent to perform the task called "instruction". 
            7. Use `send_message` to send a message in the thread, mentioning the selected agent, with content: "instructions".
            8. Use `wait_for_mentions` with a 30 seconds timeout to wait for a response from the agent you mentioned.
            9. Show the entire conversation in the thread to the user.
            10. Wait for 3 seconds and then use `ask_human` to ask the user if they need anything else and keep waiting for their response.
            11. If the user asks for something else, repeat the process from step 1.

            Use only listed tools: {tools_description}
            Your resources are: {resource_sys_message}"""
        )

        camel_agent = ChatAgent(
            system_message=sys_msg,
            model=model,
            tools=tools,
        )
        print("ChatAgent initialized with updated resources!")
        print("Resource System Message before agent question:")
        print(resource_sys_message)

        prompt = "As the user_interaction_agent on the Coral Server, initiate your workflow by listing all connected agents and asking the user how you can assist them."
        try:
            response = await camel_agent.astep(prompt)
            print("Agent Reply:")
            print(response.msgs[0].content)
        except Exception as e:
            print(f"Error processing agent response: {e}")

        await asyncio.sleep(3)

        continue

    await coral_server.__aexit__(None, None, None)

if __name__ == "__main__":
    asyncio.run(main())