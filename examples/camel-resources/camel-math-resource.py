import asyncio
import os
import json
from camel.toolkits.mcp_toolkit import MCPClient
from camel.toolkits import MCPToolkit, MathToolkit
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
    
def convert_mcp_resource_to_langchain_blob(
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
        convert_mcp_resource_to_langchain_blob(uri, content) for content in contents_result.contents
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
        "agentId": "math_agent",
        "agentDescription": "Agent responsible for performing mathematical operations",
    }
    query_string = urllib.parse.urlencode(params_1)
    MCP_SERVER_URL_1 = f"{base_url_1}?{query_string}"
    
    coral_server = MCPClient(
        command_or_url=MCP_SERVER_URL_1,
        timeout=300.0
    )
    await coral_server.__aenter__()
    print(f"Connected to MCP server as user_interface_agent at {MCP_SERVER_URL_1}")

    try:
        resources = await get_resources(coral_server, uris=None)
        if not resources:
            agent_resorces ="NA"
            print("No resources found.")
        else:
            for blob in resources:
                print(blob.data)
                agent_resorces =blob.data
    except Exception as e:
        print(f"Error retrieving resources: {e}")

    # Initialize ChatAgent
    mcp_toolkit = MCPToolkit([coral_server])
    tools = mcp_toolkit.get_tools() + MathToolkit().get_tools()
    tools_description = await get_tools_description(tools)
    resource_sys_message = agent_resorces

    sys_msg = (
        f"""You are an math agent interacting with the tools from Coral Server and having your own math capabilities.
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
        10. Wait for 2 seconds and repeat the process from step 1.

        Use only listed tools: {tools_description}
        Your resources are: {resource_sys_message}"""
    )
            
    model = ModelFactory.create(
        model_platform=ModelPlatformType.OPENAI,
        model_type=ModelType.GPT_4O_MINI,
        api_key=os.getenv("OPENAI_API_KEY"),
        model_config_dict={"temperature": 0.3, "max_tokens": 16000},
    )
    camel_agent = ChatAgent(
        system_message=sys_msg,
        model=model,
        tools=tools,
    )
    print("ChatAgent initialized successfully!")

    # Get agent reply
    prompt = "As the user_interaction_agent on the Coral Server, initiate your workflow by listing all connected agents and asking the user how you can assist them."
    response = await camel_agent.astep(prompt)
    print("Agent Reply:")
    print(response.msgs[0].content)

if __name__ == "__main__":
    asyncio.run(main())