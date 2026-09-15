from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import httpx
import os

app = FastAPI(title="Robo-Qwen OS Backend", version="2.0")

# Flowise Configuration (Update via environment variable or replace directly below)
FLOWISE_BASE_URL = os.getenv("FLOWISE_BASE_URL", "http://localhost:3000")
FLOWISE_CHATFLOW_ID = os.getenv("FLOWISE_CHATFLOW_ID", "YOUR_CHATFLOW_ID_HERE")

class PromptRequest(BaseModel):
    question: str
    overrideConfig: dict = None

@app.get("/health")
async def health_check():
    return {
        "status": "active", 
        "system": "Robo-Qwen OS Fedora Backend",
        "flowise_target": f"{FLOWISE_BASE_URL}/api/v1/prediction/{FLOWISE_CHATFLOW_ID}"
    }

@app.post("/chat")
async def process_chat(request: PromptRequest):
    """
    Proxies speech/text prompts from the Android client directly into Flowise,
    where your LangChain/LangGraph agent workflow executes and monitors the trace.
    """
    prediction_url = f"{FLOWISE_BASE_URL}/api/v1/prediction/{FLOWISE_CHATFLOW_ID}"
    
    payload = {
        "question": request.question
    }
    
    if request.overrideConfig:
        payload["overrideConfig"] = request.overrideConfig

    async with httpx.AsyncClient() as client:
        try:
            response = await client.post(
                prediction_url, 
                json=payload, 
                timeout=60.0
            )
            response.raise_for_status()
            return response.json()
            
        except httpx.HTTPStatusError as e:
            raise HTTPException(
                status_code=e.response.status_code, 
                detail=f"Flowise Execution Error: {e.response.text}"
            )
        except httpx.RequestError as e:
            raise HTTPException(
                status_code=503, 
                detail=f"Failed to connect to Flowise container: {str(e)}"
            )

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("server:app", host="0.0.0.0", port=8000, reload=True)
