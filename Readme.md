Markdown

# Robo-Qwen OS 🤖📱

Robo-Qwen OS is an autonomous, high-performance agent backend designed for Android, powered locally by a native Python stack consisting of **FastAPI**, **LangGraph**, and **Ollama (`qwen2.5-coder:7b`)**. 

This architecture replaces unstable container setups (like Flowise) with a fast, reliable, and fully programmable local Python backend.

---

## 📂 Complete Project File Manifest

### 🧠 Backend & Agent Files
| File Name | Description |
| :--- | :--- |
| **`server.py`** | Dynamically generated **FastAPI web server** running on port `8000`. Exposes `/chat` and `/health` endpoints for the Android client and manages the LangGraph execution flow. |
| **`agent_graph.py`** | The core **LangGraph state machine definition**. Manages conversation states, router logic, and local LLM execution. |
| **`langgraph.json`** | Configuration file for **LangGraph Studio / CLI** to enable visual graph debugging and workflow inspection. |
| **`start_server.sh`** | The unified **automation startup script**. Automatically handles Ollama checks, firewall permissions, virtual environment creation, dependency installation, port clearing, ADB tunneling, and launches the server. |

### 📱 Android Client Structure (`app/`, Gradle, etc.)
| File/Directory | Description |
| :--- | :--- |
| **`app/`** | Android source directory containing your Jetpack Compose UI, clients, and network integration logic. |
| **`build.gradle.kts` / `settings.gradle.kts`** | Gradle build configurations for compiling the Android client package. |
| **`gradlew` / `gradlew.bat`** | Gradle command-line wrappers for building the Android application. |

---

## 🏗️ Architecture Evolution: Flowise vs. Native Python

1. **The Flowise Era (Legacy):** Initially, the project utilized Flowise (Docker container on port `3000`) for visual node building. However, frequent Node.js package resolution errors and subpath export conflicts led to its complete removal.
2. **The Native Python Backend (Current):** The project now relies on a **100% native Python backend**. LangGraph handles deterministic routing and agent orchestration cleanly in code, eliminating container overhead and enabling lightning-fast local execution with Ollama.

---

## 🚀 Getting Started & Running the Services

The entire backend lifecycle is automated through a single script.

### 1. Launch the Server & ADB Bridge
Simply run:
```bash
./start_server.sh

This script will:

    Verify and boot Ollama (qwen2.5-coder:7b).

    Configure Fedora firewall rules for port 8000.

    Setup the venv virtual environment and install requirements (fastapi, uvicorn, langchain, langgraph, langollama).

    Automatically generate/refresh server.py.

    Clear any lingering processes on port 8000 to prevent binding conflicts.

    Establish an Android ADB reverse tunnel (adb reverse tcp:8000 tcp:8000) if a physical device/emulator is connected.

    Boot the FastAPI server.

2. (Optional) Launch Visual LangGraph Studio

To inspect your graph states visually on an interactive web canvas:
Bash

langgraph dev

🔌 API Endpoints & Testing
Chat Request (POST /chat)

Test your active backend via curl:
Bash

curl -X POST "http://localhost:8000/chat" \
     -H "Content-Type: application/json" \
     -d '{"prompt": "What time is it?"}'

Python Client Integration Example
Python

import requests

API_URL = "http://localhost:8000/chat"

def query_agent(prompt: str):
    response = requests.post(API_URL, json={"prompt": prompt})
    return response.json()

result = query_agent("System status check, Qwen.")
print(result)