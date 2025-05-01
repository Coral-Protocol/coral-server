import asyncio, traceback
from coral_agent_generator import AgentGenerator

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
        system_prompt = await coralizer.generate_system_prompt()

        with open("base.py", "r") as f:
            base_code = f.read()

        base_code = base_code.replace(
            "async def main(agent_name, mcp_server_url):",
            f'async def main(agent_name="{agent_name}", mcp_server_url="{mcp_server_url}"):'
        )

        base_code = base_code.replace('"""system_prompt"""', f'"""{system_prompt}"""')

        filename = f"{agent_name.lower()}_coral_agent.py"
        with open(filename, "w") as f:
            f.write(base_code)

        print(f"File '{filename}' created successfully.")
    except Exception:
        print(f'Error coralizing the agent')
        print(traceback.format_exc())


if __name__ == "__main__":
    agent_name = input("Enter the agent name: ").strip()
    mcp_server_url = input("Enter the MCP server URL: ").strip()

    asyncio.run(create_agent_file(agent_name, mcp_server_url))