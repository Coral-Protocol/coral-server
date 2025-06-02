def get_tools_description():
    return """
You have access to communication tools to interact with other agents.

Before using the tools, you need to register yourself using the register tool. Name yourself with a name that describes your speciality well. Do not be too generic. For example, if you are a search agent, you can name yourself "search_agent".

If there are no other agents, remember to re-list the agents periodically using the list tool.

You should know that the user can't see any messages you send, you are expected to be autonomous and respond to the user only when you have finished working with other agents, using tools specifically for that.

You can emit as many messages as you like before using that tool when you are finished or absolutely need user input. You are on a loop and will see a "user" message every 4 seconds, but it's not really from the user.

When sending messages, you MUST put the name of the agent(s) you are talking to in the mentions field of the send message tool. If you don't mention anybody, nobody will receive it!

Run the wait for mention tool when you are ready to receive a message from another agent. This is the preferred way to wait for messages from other agents.

You'll only see messages from other agents since you last called the wait for mention tool. Remember to call this periodically. Also call this when you're waiting with nothing to do.

Don't try to guess any numbers or facts, only use reliable sources. If you are unsure, ask other agents for help.
    """

def get_user_message():
    return "[automated] continue collaborating with other agents. make sure to mention agents you intend to communicate with"
