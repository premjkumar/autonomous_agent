from langchain_ollama import ChatOllama
from langgraph.graph import StateGraph, MessagesState, START, END
import os

# Initialize Ollama with local Qwen model
llm = ChatOllama(
    model="qwen2.5-coder:7b",
    base_url=os.getenv("OLLAMA_BASE_URL", "http://localhost:11434"),
    temperature=0.3
)

def call_model(state: MessagesState):
    messages = state["messages"]
    print(f"\n--- [LangGraph Node] Processing {len(messages)} messages ---")
    response = llm.invoke(messages)
    return {"messages": [response]}

# Compile the LangGraph workflow graph
workflow = StateGraph(MessagesState)
workflow.add_node("agent", call_model)
workflow.add_edge(START, "agent")
workflow.add_edge("agent", END)

# This graph object is what LangGraph Studio looks for
graph = workflow.compile()
