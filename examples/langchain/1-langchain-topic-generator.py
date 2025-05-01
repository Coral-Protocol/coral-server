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

# Setup logging
logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s")
logger = logging.getLogger(__name__)

# Load environment variables
load_dotenv()

MCP_SERVER_URL = "http://localhost:3001/sse" #coral
AGENT_NAME = "topic_generator_agent"


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
def TopicGenerator(
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
    logger.info(f"Calling TopicGenerator with text: {text}")
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
        logger.error(f"Unexpected error in TopicGenerator: {str(e)}")
        return {"result": f"Unexpected error: {str(e)}. Please try again later."}

async def create_topic_generator_agent(client, tools):
    prompt = ChatPromptTemplate.from_messages([
        ("system", f"""You are topic_generator_agent, responding to mentions from user_interaction_agent.
        1. Ensure 'topic_generator_agent' is registered using list_agents. If not, register with register_agent (agentId: 'topic_generator_agent', agentName: 'Topic Generator Agent', description: 'Generates news topics.').
        2. Loop:
           - Call wait_for_mentions ONCE (agentId: 'topic_generator_agent', timeoutMs: 10000).
           - For mentions from user_interaction_agent starting with 'Fetch news:', extract the query after 'Fetch news: '. Clean it by converting to lowercase and removing 'fetch', 'me', 'the', 'latest'. Call TopicGenerator (text: [cleaned query], source_country: 'us', language: 'en', number: 3).
           - Send results or error to the mentioned thread via send_message (senderId: 'topic_generator_agent', mentions: ['user_interaction_agent']).
           - If no mentions or invalid format, continue looping.
        Track threadId from mentions. Do not create threads. Log all actions. Tools: {get_tools_description(tools)}"""),
        ("placeholder", "{agent_scratchpad}")
    ])
    # prompt = ChatPromptTemplate.from_messages([
    #         ("system", f"""You are topic_generator_agent, responding to mentions from user_interaction_agent.
    #         1. Ensure 'topic_generator_agent' is registered using list_agents. If not, register with register_agent (agentId: 'topic_generator_agent', agentName: 'Topic Generator Agent', description: 'Generates news topics.').
    #         2. Loop:
    #         - Call wait_for_mentions ONCE (agentId: 'topic_generator_agent', timeoutMs: 30000).
    #         - For mentions from user_interaction_agent, extract the query. Clean it by converting to lowercase. Call TopicGenerator (text: [cleaned query], source_country: 'us', language: 'en', number: 3).
    #         - Send results or error to the mentioned thread via send_message (senderId: 'topic_generator_agent', mentions: ['user_interaction_agent']).
    #         - If no mentions or invalid format, continue looping.
    #         Track threadId from mentions. Do not create threads. Log all actions. Tools: {get_tools_description(tools)}"""),
    #         ("placeholder", "{agent_scratchpad}")
    #     ])

    model = ChatOpenAI(model="gpt-4o-mini", api_key=os.getenv("OPENAI_API_KEY"), temperature=0.3, max_tokens=4096)
    agent = create_tool_calling_agent(model, tools, prompt)
    return AgentExecutor(agent=agent, tools=tools, verbose=True)

async def main():
    retry_delay = 5  # seconds
    max_retries = 5
    retries = max_retries

    while retries > 0:
        try:
            async with MultiServerMCPClient(connections={
                "coral": {"transport": "sse", "url": MCP_SERVER_URL, "timeout": 5, "sse_read_timeout": 1200}
            }) as client:
                tools = client.get_tools() + [TopicGenerator]
                logger.info(f"Connected to MCP server. Tools:\n{get_tools_description(tools)}")
                retries = max_retries  # Reset retries on successful connection
                await (await create_topic_generator_agent(client, tools)).ainvoke({})
        except ClosedResourceError as e:
            retries -= 1
            logger.error(f"Connection closed: {str(e)}. Retries left: {retries}. Retrying in {retry_delay} seconds...")
            if retries == 0:
                logger.error("Max retries reached. Exiting.")
                break
            await asyncio.sleep(retry_delay)
        except Exception as e:
            retries -= 1
            logger.error(f"Unexpected error: {str(e)}. Retries left: {retries}. Retrying in {retry_delay} seconds...")
            if retries == 0:
                logger.error("Max retries reached. Exiting.")
                break
            await asyncio.sleep(retry_delay)

if __name__ == "__main__":
    asyncio.run(main())