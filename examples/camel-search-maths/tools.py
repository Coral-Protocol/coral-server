import os

import requests
from camel.toolkits import BaseToolkit


class JinaBrowsingToolkit(BaseToolkit):
    def get_url_content(self, url: str) -> str:
        r"""Fetch the content of a URL using the r.jina.ai service.

        Args:
            url (str): The URL to fetch content from.

        Returns:
            str: The markdown content of the URL.
        """

        # Replace http with https and add https if not present
        if not url.startswith("https://"):
            url = "https://" + url.lstrip("https://").lstrip("http://")

        jina_url = f"https://r.jina.ai/{url}"
        headers = {}
        if os.environ.get('JINA_PROXY_URL'):
            headers['X-Proxy-Url'] = os.environ.get('JINA_PROXY_URL')

        auth_token = os.environ.get('JINA_AUTH_TOKEN')
        if auth_token:
            headers['Authorization'] = f'Bearer {auth_token}'
        try:
            response = requests.get(jina_url, headers=headers)
            response.raise_for_status()
            return response.text
        except requests.RequestException as e:
            return f"Error fetching URL content: {e!s}"

    def get_url_content_with_context(
        self,
        url: str,
        search_string: str,
        context_chars: int = 700,
        max_instances: int = 3,
    ) -> str:
        r"""Fetch the content of a URL and return context around all instances of a specific string.

        Args:
            url (str): The URL to fetch content from.
            search_string (str): The string to search for in the content.
            context_chars (int): Number of characters to return before and after each found string.
            max_instances (int): Maximum number of instances to return.

        Returns:
            str: The context around all found instances of the string, or an error message if not found.

        If there are no results, try again with a more likely search string. Start with a more likely string and only use a less likely string if the first one has too many results.
        """
        content = self.get_url_content(url)
        if content.startswith("Error fetching URL content"):
            return content

        instances = []
        start = 0
        while True:
            index = content.lower().find(search_string.lower(), start)
            if index == -1 or len(instances) >= max_instances:
                break

            context_start = max(0, index - context_chars)
            context_end = min(
                len(content), index + len(search_string) + context_chars
            )
            instance_context = content[context_start:context_end]
            instances.append(
                f"Instance {len(instances) + 1}:\n{instance_context}\n"
            )

            start = index + len(search_string)

        if instances:
            return (
                f"Found {len(instances)} instance(s) of '{search_string}':\n\n"
                + '\n'.join(instances)
            )
        else:
            return f"Search string '{search_string}' not found in the content."
