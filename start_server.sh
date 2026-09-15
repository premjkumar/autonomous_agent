#!/usr/bin/env bash
set -e

PORT_OLLAMA=11434
PORT_SERVER=8000
MODEL_NAME="qwen2.5-coder:7b"

echo "=== 1. Checking Ollama Service ==="
export OLLAMA_HOST="0.0.0.0:${PORT_OLLAMA}"

if ! pgrep -x "ollama" > /dev/null; then
    echo "Starting Ollama..."
    ollama serve > ollama.log 2>&1 &
    sleep 3
fi

echo "=== 2. Configuring Environment & Dependencies ==="
if command -v firewall-cmd &> /dev/null; then
    sudo firewall-cmd --add-port=${PORT_SERVER}/tcp --permanent >/dev/null 2>&1 || true
    sudo firewall-cmd --reload >/dev/null 2>&1 || true
fi

if [ ! -d "venv" ]; then
    python3 -m venv venv
fi
source venv/bin/activate

pip install --quiet fastapi uvicorn requests langchain langchain-ollama langgraph langsmith

cat << 'PYEOF' > server.py
import os
import datetime
from typing import TypedDict, Annotated, Sequence
from fastapi import FastAPI
from pydantic import BaseModel

from langchain_core.messages import BaseMessage, HumanMessage, AIMessage, SystemMessage
from langchain_ollama import ChatOllama
from langgraph.graph import StateGraph, END

app = FastAPI()

llm = ChatOllama(
    model="qwen2.5-coder:7b",
    base_url="http://127.0.0.1:11434",
    temperature=0.3
)

class AgentState(TypedDict):
    messages: Sequence[BaseMessage]
    next_action: str
    final_response: str

def router_node(state: AgentState) -> AgentState:
    last_msg = state["messages"][-1].content.lower()
    
    if any(w in last_msg for w in ["time", "clock"]):
        now_str = datetime.datetime.now().strftime("%I:%M %p")
        return {
            "messages": state["messages"],
            "next_action": "GET_TIME",
            "final_response": f"The current system time is {now_str}."
        }
    
    if any(w in last_msg for w in ["status", "stats", "cpu"]):
        load1, _, _ = os.getloadavg()
        return {
            "messages": state["messages"],
            "next_action": "GET_STATS",
            "final_response": f"System status optimal. CPU load average is {load1:.2f}."
        }
    
    return {
        "messages": state["messages"],
        "next_action": "LLM_INFERENCE",
        "final_response": ""
    }

def llm_node(state: AgentState) -> AgentState:
    sys_prompt = SystemMessage(content="You are Robo-Qwen OS voice assistant. Keep answers short, under 2 sentences, without markdown.")
    full_prompt = [sys_prompt] + list(state["messages"])
    
    response = llm.invoke(full_prompt)
    return {
        "messages": state["messages"] + [response],
        "next_action": "NONE",
        "final_response": response.content.strip()
    }

def conditional_edge(state: AgentState):
    if state["next_action"] == "LLM_INFERENCE":
        return "llm"
    return END

workflow = StateGraph(AgentState)
workflow.add_node("router", router_node)
workflow.add_node("llm", llm_node)

workflow.set_entry_point("router")
workflow.add_conditional_edges("router", conditional_edge, {"llm": "llm", END: END})
workflow.add_edge("llm", END)

app_graph = workflow.compile()

class QueryRequest(BaseModel):
    prompt: str

@app.post("/chat")
def chat(req: QueryRequest):
    try:
        user_prompt = req.prompt.strip()
        print(f"\n[LangGraph Engine] Processing Input: '{user_prompt}'")

        initial_state = {
            "messages": [HumanMessage(content=user_prompt)],
            "next_action": "",
            "final_response": ""
        }
        
        graph_output = app_graph.invoke(initial_state)
        
        action = graph_output.get("next_action", "NONE")
        response = graph_output.get("final_response", "")

        print(f"[LangGraph Engine] Output Node Executed | Action: {action}")
        return {"response": response, "action": action}

    except Exception as e:
        print(f"[LangGraph Error]: {str(e)}")
        return {"error": str(e), "response": "LangGraph execution failed."}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
PYEOF

# Forcefully free port 8000 and wait for the OS socket to clear
if ss -tlnp 2>/dev/null | grep -q ":${PORT_SERVER} "; then
    echo "=== 3. Freeing Port ${PORT_SERVER} (Stopping previous instance) ==="
    sudo fuser -k -9 ${PORT_SERVER}/tcp || true
    sleep 2  # Give the Linux kernel time to release the socket binding
fi

LOCAL_IP=$(ip route get 1.1.1.1 2>/dev/null | awk '{print $7}')
echo "--------------------------------------------------------"
echo "🤖 RoboQwen LangGraph Backend Active!"
echo "Target Endpoint: http://${LOCAL_IP}:${PORT_SERVER}/chat"
echo "--------------------------------------------------------"

# --- 4. Setup Android ADB Reverse Tunnel ---
if command -v adb &> /dev/null && adb devices | grep -q "device$"; then
    echo "=== 4. Configuring Android ADB Reverse Tunnel ==="
    adb reverse tcp:${PORT_SERVER} tcp:${PORT_SERVER}
    echo "ADB tunnel active: localhost:${PORT_SERVER} <-> Android device"
else
    echo "=== 4. Skipping ADB tunnel (No active device detected) ==="
fi

python3 server.py
