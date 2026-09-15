import json
import os
import requests

# 1. Configuration
FLOWISE_URL = "http://localhost:3000"  # Update if hosted elsewhere
JSON_FILE_PATH = "YOUR_DOWNLOADED_FILE.json"  # Path to your Flowise JSON file
FLOW_NAME = "My Local Automated Flow"  # Name it will appear as in the GUI

def push_flow():
    if not os.path.exists(JSON_FILE_PATH):
        print(f"Error: File '{JSON_FILE_PATH}' not found.")
        return

    # Load the flow export JSON file
    with open(JSON_FILE_PATH, "r", encoding="utf-8") as f:
        raw_data = json.load(f)

    # Flowise expects the internal node graph data as a stringified JSON object
    flow_data_content = raw_data.get("flowData", raw_data)
    if isinstance(flow_data_content, dict):
        flow_data_str = json.dumps(flow_data_content)
    else:
        flow_data_str = flow_data_content

    # Payload matching Flowise Chatflows API schema
    payload = {
        "name": FLOW_NAME,
        "flowData": flow_data_str,
        "deployed": True,
        "isPublic": True,
    }

    endpoint = f"{FLOWISE_URL}/api/v1/chatflows"

    print(f"Pushing flow to Flowise at {endpoint}...")
    response = requests.post(endpoint, json=payload)

    if response.status_code in [200, 201]:
        res_json = response.json()
        print("\nSuccess! Flow pushed and loaded into Flowise GUI.")
        print(f"Flow ID: {res_json.get('id')}")
        print(f"Flow Name: {res_json.get('name')}")
        print("Refresh your browser tab at http://localhost:3000 to see it on your canvas.")
    else:
        print(f"\nFailed to push flow. Status Code: {response.status_code}")
        print(response.text)

if __name__ == "__main__":
    push_flow()
