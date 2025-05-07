import os
import asyncio
import traceback
from agent_generator import AgentGenerator

async def create_agent_file(agent_name: str, mcp_server_url: str):
    try:
        coralizer = AgentGenerator(
            agent_name=agent_name,
            mcp_server_url=mcp_server_url,
            mcp_connection_type="sse"
        )
        connection = await coralizer.mcp_connection()
        if not connection:
            print("Unable to connect with the mcp server")
            return None
        agent_description = coralizer.get_mcp_description()

        # Read base template
        with open('base_coralizer.py', 'r') as py_file:
            base_code = py_file.read()

        # Customize the template
        base_code = base_code.replace('"agentId": "",', f'"agentId": "{agent_name}",')
        base_code = base_code.replace("MCP_SERVER_URL = ''", f"MCP_SERVER_URL = '{mcp_server_url}'")
        base_code = base_code.replace('"agentDescription": ""', f'"agentDescription": "{agent_description}"')

        # Get project root and target directory
        current_dir = os.getcwd()
        project_root = os.path.dirname(os.path.dirname(current_dir))
        target_dir = os.path.join(project_root, "coralizedagents")

        # Create target directory if it doesn't exist
        os.makedirs(target_dir, exist_ok=True)

        # Write agent file
        filename = os.path.join(target_dir, f"{agent_name.lower()}_coral_agent.py")
        with open(filename, "w") as f:
            f.write(base_code)
        print(f"File '{filename}' created successfully.")

    except Exception as e:
        print(f'Error coralizing the agent: {str(e)}')
        print(traceback.format_exc())


if __name__ == "__main__":
    os.environ["FC_API_KEY"] = "fc-ea3085565d724f98804d08538d3f5511"
    agent_name = input("Enter the agent name: ").strip()
    mcp_server_url = input("Enter the MCP server URL: ").strip()
    asyncio.run(create_agent_file(agent_name, mcp_server_url))