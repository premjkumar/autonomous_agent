#!/usr/bin/env python3
import sys
import requests

SERVER_URL = "http://127.0.0.1:8000/chat"

def test_prompt(prompt_text):
    print(f"\n==========================================")
    print(f"📡 Testing LangGraph Pipeline: '{prompt_text}'")
    print(f"==========================================")
    try:
        res = requests.post(SERVER_URL, json={"prompt": prompt_text}, timeout=30)
        data = res.json()
        print(f"📥 Graph Response : {data.get('response')}")
        print(f"⚙️ Graph Action   : {data.get('action')}")
    except Exception as e:
        print(f"❌ Connection Error: {e}")

if __name__ == "__main__":
    prompt = sys.argv[1] if len(sys.argv) > 1 else "what time is it"
    test_prompt(prompt)
