from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List
import json
import os
from datetime import datetime

app = FastAPI(title="Smart Assistant Mock Server")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

class Vertex(BaseModel):
    x: int
    y: int

class ZoneRequest(BaseModel):
    zoneName: str
    vertices: List[Vertex]

class Zone(ZoneRequest):
    createdAt: str

ZONES_FILE = os.path.join(os.path.dirname(__file__), "zones.json")

def load_zones():
    if not os.path.exists(ZONES_FILE):
        return []
    with open(ZONES_FILE, "r", encoding="utf-8") as f:
        return json.load(f)

def save_zones(zones):
    with open(ZONES_FILE, "w", encoding="utf-8") as f:
        json.dump(zones, f, indent=2, ensure_ascii=False)

@app.post("/api/management_assistant/addZone/{device_id}")
async def add_zone(device_id: int, zone: ZoneRequest):
    if not zone.zoneName:
        raise HTTPException(status_code=400, detail="Zone name is required")
    if len(zone.vertices) < 3:
        raise HTTPException(status_code=400, detail="Zone must have at least 3 vertices")
    zones = load_zones()
    new_zone = Zone(
        zoneName=zone.zoneName,
        vertices=zone.vertices,
        createdAt=datetime.now().isoformat()
    )
    zones.append(new_zone.dict())
    save_zones(zones)
    print(f"Zone created: {zone.zoneName} with {len(zone.vertices)} vertices")
    return {"status": "success", "message": f"Zone {zone.zoneName} created"}

@app.get("/api/zones")
async def get_zones():
    return load_zones()

@app.delete("/api/zones/{zone_name}")
async def delete_zone(zone_name: str):
    zones = load_zones()
    zones = [z for z in zones if z["zoneName"] != zone_name]
    save_zones(zones)
    return {"status": "success", "message": f"Zone {zone_name} deleted"}

@app.get("/api/camera/stream")
async def camera_stream():
    return {
        "imageUrl": "https://picsum.photos/640/480",
        "timestamp": datetime.now().isoformat()
    }

@app.get("/")
async def root():
    return {"status": "ok", "message": "Smart Assistant Mock Server is running"}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8080)
