In this example we have 3 agents implemented with CAMEL working together to answer a user query.

To run it, you need to have the dependencies installed:

# Running the example

# 1. Install the dependencies
```bash
pip install -r requirements.txt
```

You will need to install CAMEL with all optional dependencies until they fix the minimal requirements version.

```bash
pip install "camel-ai[all]"
```

## 2. Start the server
Cd to this project's root directory and run the server.
```bash
./gradlew run
```

Note that gradle will show "83"% forever, but it is actually running. You can check the logs in the terminal to see if it is up and running.

## 3. Run the agents
Ensure you have an OPENAI_API_KEY set in your environment variables (or change to a different model in the agents)

Before running the agents, you can configure the model settings in `config.py`:
```python
# Model Configuration
PLATFORM_TYPE = "OPENAI"  # Change the model provider
MODEL_TYPE = "GPT_4O"    # Change the model type

# Model Settings
MODEL_CONFIG = {
    "temperature": 0.3,  # Adjust model parameters
    "max_tokens": 4096,
}
```
For available model providers and types, refer to the [CAMEL model types documentation](https://github.com/camel-ai/camel/blob/master/camel/types/enums.py).

In a separate terminal, run the agents. They all need to be running for this example to work.

```bash
python mcp_example_camel_math.py
```

```bash
python mcp_example_camel_search.py
```

```bash
python mcp_example_camel_interface.py
```


## 4. Interact with the agents

You will eventually see the interface agent asking for your query via STDIN. Write your query and hit enter. 
Try asking for example:

```
What is the square root of the area of Konstanz?
```

The society will then work together to address your query, and the interface agent will share their findings with you.


## Troubleshooting
The agents are limited to iterate only 20 times to prevent accidental API expenses, so they might need restarting if they've been alive too long.

Also right now the agents will not be unregistered, so make sure to restart the server if you want to run them again.

This is very early, so we welcome any questions no matter how silly they might seem so we can improve the documentation and Dev Experience!

Come by our Discord for any questions or suggestions: https://discord.gg/cDzGHnzkwD

---


# Build on the example 
Now that you've got your society running, you can build on it.

Adding another agent is as simple as copying and pasting one of these agent files and running it too.
Don't forget to prompt it to assume a different name.


# Future potential
At the time of writing, this is a proof of concept. Server and agent lifecycle questions remain.
The scope of this project includes answering these questions with remote mode and sessions.