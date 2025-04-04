In this example we have 3 agents implemented with CAMEL.

To run it, you need to have the dependencies installed:

```bash
pip install -r requirements.txt
```
Ensure you have an OPENAI_API_KEY set in your environment variables (or change to a different model in the agents)

To run this test, launch each of the agents in a separate terminal:

```bash
python mcp_example_camel_interface.py
```

```bash
python mcp_example_camel_math.py
```

```bash
python mcp_example_camel_search.py
```

You will eventually see the interface agent asking for your query via STDIN. Write your query and hit enter. 
The society will then work together to address your query.