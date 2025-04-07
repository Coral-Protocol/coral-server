In this example we have 3 agents implemented with CAMEL working together to answer a user query.

To run it, you need to have the dependencies installed:


# 1. Install the dependencies
```bash
pip install -r requirements.txt
```



# Running the example
## 2. Start the server
```bash
cd ../../
./gradlew run
```


## 3. Run the agents
Ensure you have an OPENAI_API_KEY set in your environment variables (or change to a different model in the agents)

```bash
python mcp_example_camel_interface.py
```

```bash
python mcp_example_camel_math.py
```

```bash
python mcp_example_camel_search.py
```


## 4. Interact with the agents

You will eventually see the interface agent asking for your query via STDIN. Write your query and hit enter. 
The society will then work together to address your query, and the interface agent will share their findings with you.

---

# Build on the example 
Now that you've got your society running, you can build on it.

Adding another agent is as simple as copying and pasting one of these agent files and running it too.
Don't forget to prompt it to assume a different name.


# Future potential
At the time of writing, this is a proof of concept. Server and agent lifecycle questions remain.
The scope of this project includes answering these questions with remote mode and sessions.