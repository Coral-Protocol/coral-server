import asyncio
import os
import json
import logging
import re
from langchain_mcp_adapters.client import MultiServerMCPClient
from langchain.prompts import ChatPromptTemplate
from langchain_openai import ChatOpenAI
from langchain.agents import create_tool_calling_agent, AgentExecutor
from langchain_core.tools import tool
import worldnewsapi
from worldnewsapi.rest import ApiException
from dotenv import load_dotenv
from anyio import ClosedResourceError
import urllib.parse

# Setup logging
logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s")
logger = logging.getLogger(__name__)

# Load environment variables
load_dotenv()

base_url = "http://localhost:5555/devmode/exampleApplication/privkey/session1/sse"
params = {
    "waitForAgents": 2,
    "agentId": "world_news_agent",
    "agentDescription": "You are world_news_agent, responsible for fetching and generating news topics based on mentions from other agents"
}
query_string = urllib.parse.urlencode(params)
MCP_SERVER_URL = f"{base_url}?{query_string}"

AGENT_NAME = "world_news_agent"


# Configure WorldNewsAPI
news_configuration = worldnewsapi.Configuration(host="https://api.worldnewsapi.com")
news_configuration.api_key["apiKey"] = os.getenv("WORLD_NEWS_API_KEY")

# Validate API keys
if not os.getenv("OPENAI_API_KEY"):
    raise ValueError("OPENAI_API_KEY is not set in environment variables.")
if not os.getenv("WORLD_NEWS_API_KEY"):
    raise ValueError("WORLD_NEWS_API_KEY is not set in environment variables.")

def get_tools_description(tools):
    return "\n".join(f"Tool: {t.name}, Schema: {json.dumps(t.args).replace('{', '{{').replace('}', '}}')}" for t in tools)

@tool
def WorldNewsTool(
    text: str,
    text_match_indexes: str = "title,content",
    source_country: str = "us",
    language: str = "en",
    sort: str = "publish-time",
    sort_direction: str = "ASC",
    offset: int = 0,
    number: int = 3,
):
    """
    Search articles from WorldNewsAPI.

    Args:
        text: Required search query string (keywords, phrases)
        text_match_indexes: Where to search for the text (default: 'title,content')
        source_country: Country of news articles (default: 'us')
        language: Language of news articles (default: 'en')
        sort: Sorting criteria (default: 'publish-time')
        sort_direction: Sort direction (default: 'ASC')
        offset: Number of news to skip (default: 0)
        number: Number of news to return (default: 3)

    Returns:
        dict: Contains 'result' key with Markdown formatted string of articles or an error message
    """
    logger.info(f"Calling WorldNewsTool with text: {text}")
    try:
        with worldnewsapi.ApiClient(news_configuration) as api_client:
            api_instance = worldnewsapi.NewsApi(api_client)
            api_response = api_instance.search_news(
                text=text,
                text_match_indexes=text_match_indexes,
                source_country=source_country,
                language=language,
                sort=sort,
                sort_direction=sort_direction,
                offset=offset,
                number=number,
            )
            articles = api_response.news
            if not articles:
                logger.warning("No articles found for query.")
                return {"result": "No news articles found for the query."}
            news = "\n".join(
                f"""
            ### Title: {getattr(article, 'title', 'No title')}

            **URL:** [{getattr(article, 'url', 'No URL')}]({getattr(article, 'url', 'No URL')})

            **Date:** {getattr(article, 'publish_date', 'No date')}

            **Text:** {getattr(article, 'text', 'No description')}

            ------------------
            """
                for article in articles
            )
            logger.info("Successfully fetched news articles.")
            return {"result": str(news)}
    except ApiException as e:
        logger.error(f"News API error: {str(e)}")
        return {"result": f"Failed to fetch news: {str(e)}. Please check the API key or try again later."}
    except Exception as e:
        logger.error(f"Unexpected error in WorldNewsTool: {str(e)}")
        return {"result": f"Unexpected error: {str(e)}. Please try again later."}

async def create_world_news_agent(client, tools):
    prompt = ChatPromptTemplate.from_messages([
        (
            "system",
            f"""You are world_news_agent, responsible for fetching and generating news topics based on mentions from other agents.

            **Main Loop**:
            - Enter an infinite loop to continuously process mentions:
            1. Log: 'Starting loop iteration.'
            2. Call wait_for_mentions (timeoutMs: 30000) to receive mentions from other agents. On success, log: 'Waiting for mentions...' and wait for 30 seconds. If no mentions are received, log: 'No mentions received within timeout.' and proceed to step 4.
            3. For each mention:
                - Extract the threadId from the mention and store as mentionThreadId.
                - Extract the full mention content as the query.
                - Extract the sender ID from the mention and store as senderId.
                - Log: 'Processing query: [query] in thread [mentionThreadId] from sender [senderId].'
                - Analyze the query to understand its context (e.g., identify topics like technology, politics, sports).
                - Determine source_country: if the query contains 'us' (case-insensitive), set source_country to 'us'; otherwise, set source_country to 'uk'.
                - Clean the query by converting to lowercase and removing stopwords ('fetch', 'me', 'the', 'latest').
                - Call WorldNewsTool (text: [cleaned query], source_country: [determined source_country], language: 'en', number: 3, text_match_indexes: 'title,content', sort: 'publish-time', sort_direction: 'ASC', offset: 0).
                - Format results as: 'News topics: [topic1], [topic2], [topic3]' or 'Error: [error message]' if WorldNewsTool fails.
                - Send send_message (threadId: mentionThreadId, content: '[formatted results]', mentions: [senderId]). On failure, retry once, log: 'Error: Could not send news results.', and continue.
            4. Send send_message (threadId: opThreadId, content: 'Waiting for news requests.', mentions: ['world_news_agent']). On failure, retry once, log: 'Error: Could not send waiting message.', and continue.
            5. Log: 'Completed loop iteration.'
            6. **Repeat the loop**: Immediately restart the loop from step 1 without terminating.

            **Notes**:
            - Do NOT create threads except during initialization as specified.
            - Use initThreadId for initialization messages and opThreadId for loop messages unless responding to a mentionThreadId.
            - Log all actions and errors with timestamps.
            - Use only listed tools: {get_tools_description(tools)}.

            **Termination**:
            - Do not exit the loop unless explicitly instructed by an external signal (e.g., a specific mention or tool call)."""
        ),
        ("placeholder", "{agent_scratchpad}")
    ])

    model = ChatOpenAI(model="gpt-4o-mini", api_key=os.getenv("OPENAI_API_KEY"), temperature=0.3, max_tokens=4096)
    agent = create_tool_calling_agent(model, tools, prompt)
    return AgentExecutor(agent=agent, tools=tools, verbose=True)


async def main():
    async with MultiServerMCPClient(
        connections={
            "coral": {
                "transport": "sse",
                "url": MCP_SERVER_URL,
                "timeout": 30,
                "sse_read_timeout": 60,
            }
        }
    ) as client:
        logger.info(f"Connected to MCP server at {MCP_SERVER_URL}")
        tools = client.get_tools() + [WorldNewsTool]
        logger.info(f"Tools Description:\n{get_tools_description(tools)}")
        agent_executor = await create_world_news_agent(client, tools)
        
        while True:
            try:
                logger.info("Starting new agent invocation")
                await agent_executor.ainvoke({"agent_scratchpad": []})
                logger.info("Completed agent invocation, restarting loop")
                await asyncio.sleep(1)  # Prevent tight looping
            except Exception as e:
                logger.error(f"Error in agent loop: {str(e)}")
                await asyncio.sleep(5)  # Wait before retrying

if __name__ == "__main__":
    asyncio.run(main())

