from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List, Optional
import json
import os
import uuid
from datetime import datetime

app = FastAPI(title="Smart Assistant Mock Server")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

DATA_DIR = os.path.dirname(os.path.abspath(__file__))

# ─── Auth ────────────────────────────────────────────────────────────────────

class LoginRequest(BaseModel):
    email: str
    password: str

class RegisterRequest(BaseModel):
    name: str
    email: str
    password: str

class AuthResponse(BaseModel):
    token: str
    userId: str
    name: str
    email: str

USERS_FILE = os.path.join(DATA_DIR, "users.json")

def load_users():
    if not os.path.exists(USERS_FILE):
        return []
    with open(USERS_FILE, "r", encoding="utf-8") as f:
        return json.load(f)

def save_users(users):
    with open(USERS_FILE, "w", encoding="utf-8") as f:
        json.dump(users, f, indent=2, ensure_ascii=False)

@app.post("/api/v1/auth/login")
async def login(req: LoginRequest):
    users = load_users()
    for u in users:
        if u["email"] == req.email and u["password"] == req.password:
            return AuthResponse(
                token=f"mock_token_{u['userId']}",
                userId=u["userId"],
                name=u["name"],
                email=u["email"]
            )
    raise HTTPException(status_code=401, detail="Invalid credentials")

@app.post("/api/v1/auth/register")
async def register(req: RegisterRequest):
    users = load_users()
    for u in users:
        if u["email"] == req.email:
            raise HTTPException(status_code=409, detail="Email already registered")
    user_id = str(uuid.uuid4())
    new_user = {
        "userId": user_id,
        "name": req.name,
        "email": req.email,
        "password": req.password
    }
    users.append(new_user)
    save_users(users)
    return AuthResponse(
        token=f"mock_token_{user_id}",
        userId=user_id,
        name=req.name,
        email=req.email
    )

# ─── Devices ─────────────────────────────────────────────────────────────────

class WifiConfigRequest(BaseModel):
    ssid: str
    password: str

class DeviceStatusDto(BaseModel):
    deviceId: str
    status: str
    ip: str = ""

@app.get("/api/v1/devices/{device_id}/status")
async def get_device_status(device_id: str):
    return DeviceStatusDto(
        deviceId=device_id,
        status="online",
        ip="192.168.1.100"
    )

@app.post("/api/v1/devices/{device_id}/wifi")
async def configure_wifi(device_id: str, config: WifiConfigRequest):
    return {"status": "success", "message": f"WiFi configured: {config.ssid}"}

# ─── Zones (via DeviceApi DTOs) ──────────────────────────────────────────────

ZONES_FILE = os.path.join(DATA_DIR, "zones.json")

class Vertex(BaseModel):
    x: int
    y: int

class ZoneDto(BaseModel):
    id: str
    name: str
    x1: float
    y1: float
    x2: float
    y2: float
    trigger: str = "ENTER"
    dwellTimeMinutes: int = 0
    smartThingsDeviceId: Optional[str] = None
    smartThingsAction: Optional[str] = None

class ZoneRequest(BaseModel):
    zoneName: str
    vertices: List[Vertex]

class AddZoneRequest(BaseModel):
    zoneName: str
    vertices: List[Vertex]

def load_zones():
    if not os.path.exists(ZONES_FILE):
        return []
    with open(ZONES_FILE, "r", encoding="utf-8") as f:
        return json.load(f)

def save_zones(zones):
    with open(ZONES_FILE, "w", encoding="utf-8") as f:
        json.dump(zones, f, indent=2, ensure_ascii=False)

@app.post("/api/v1/devices/{device_id}/zones")
async def save_zone_dto(device_id: str, zone: ZoneDto):
    zones = load_zones()
    existing = [z for z in zones if z.get("id") == zone.id]
    entry = zone.dict()
    entry["deviceId"] = device_id
    entry["createdAt"] = datetime.now().isoformat()
    if existing:
        for i, z in enumerate(zones):
            if z.get("id") == zone.id:
                zones[i] = entry
                break
    else:
        zones.append(entry)
    save_zones(zones)
    return {"status": "success", "id": zone.id}

@app.get("/api/v1/devices/{device_id}/zones")
async def get_zones_dto(device_id: str):
    zones = load_zones()
    return [z for z in zones if z.get("deviceId") == device_id]

# ─── Management Assistant ────────────────────────────────────────────────────

@app.post("/api/management_assistant/addZone/{device_id}")
async def add_zone_ma(device_id: int, zone: ZoneRequest):
    if not zone.zoneName:
        raise HTTPException(status_code=400, detail="Zone name is required")
    if len(zone.vertices) < 3:
        raise HTTPException(status_code=400, detail="Zone must have at least 3 vertices")
    zones = load_zones()
    new_zone = {
        "zoneName": zone.zoneName,
        "vertices": [v.dict() for v in zone.vertices],
        "createdAt": datetime.now().isoformat()
    }
    zones.append(new_zone)
    save_zones(zones)
    return {"status": "success", "message": f"Zone {zone.zoneName} created"}

@app.get("/api/zones")
async def get_zones():
    return load_zones()

@app.delete("/api/zones/{zone_name}")
async def delete_zone(zone_name: str):
    zones = load_zones()
    zones = [z for z in zones if z.get("zoneName") != zone_name]
    save_zones(zones)
    return {"status": "success", "message": f"Zone {zone_name} deleted"}

@app.get("/api/management_assistant/takePhoto/{device_id}")
async def take_photo(device_id: str):
    return {
        "url": f"https://picsum.photos/seed/{device_id}_{datetime.now().timestamp()}/640/480",
        "status": "success"
    }

@app.get("/api/camera/stream")
async def camera_stream():
    return {
        "imageUrl": "https://picsum.photos/640/480",
        "timestamp": datetime.now().isoformat()
    }

# ─── Rooms ───────────────────────────────────────────────────────────────────

class EnabledRequest(BaseModel):
    enabled: bool

# Stores enabled state for zones not yet persisted in zones.json
zone_enabled_overrides: dict = {}

@app.post("/api/v1/zones/{zone_id}/enabled")
async def toggle_zone_enabled(zone_id: str, req: EnabledRequest):
    zones = load_zones()
    for z in zones:
        if z.get("id") == zone_id or z.get("zoneName") == zone_id:
            z["enabled"] = req.enabled
            save_zones(zones)
            zone_enabled_overrides.pop(zone_id, None)
            return {"status": "success", "zoneId": zone_id, "enabled": req.enabled}
    zone_enabled_overrides[zone_id] = req.enabled
    return {"status": "success", "zoneId": zone_id, "enabled": req.enabled, "note": "override"}

@app.post("/api/v1/devices/{device_id}/zones/enabled")
async def toggle_all_zones_enabled(device_id: str, req: EnabledRequest):
    zones = load_zones()
    for z in zones:
        if z.get("deviceId") == device_id or True:
            z["enabled"] = req.enabled
    save_zones(zones)
    return {"status": "success", "enabled": req.enabled, "count": len(zones)}

class RoomCreate(BaseModel):
    name: str

class RoomUpdate(BaseModel):
    name: str

ROOMS_FILE = os.path.join(DATA_DIR, "rooms.json")

def load_rooms():
    if not os.path.exists(ROOMS_FILE):
        return []
    with open(ROOMS_FILE, "r", encoding="utf-8") as f:
        return json.load(f)

def save_rooms(rooms):
    with open(ROOMS_FILE, "w", encoding="utf-8") as f:
        json.dump(rooms, f, indent=2, ensure_ascii=False)

@app.post("/api/rooms")
async def create_room(room: RoomCreate):
    rooms = load_rooms()
    new_room = {
        "id": str(uuid.uuid4()),
        "name": room.name,
        "createdAt": datetime.now().isoformat()
    }
    rooms.append(new_room)
    save_rooms(rooms)
    return new_room

@app.get("/api/rooms")
async def get_rooms():
    return load_rooms()

@app.get("/api/rooms/{room_id}")
async def get_room(room_id: str):
    rooms = load_rooms()
    for r in rooms:
        if r["id"] == room_id:
            return r
    raise HTTPException(status_code=404, detail="Room not found")

@app.put("/api/rooms/{room_id}")
async def update_room(room_id: str, room: RoomUpdate):
    rooms = load_rooms()
    for r in rooms:
        if r["id"] == room_id:
            r["name"] = room.name
            save_rooms(rooms)
            return r
    raise HTTPException(status_code=404, detail="Room not found")

@app.delete("/api/rooms/{room_id}")
async def delete_room(room_id: str):
    rooms = load_rooms()
    rooms = [r for r in rooms if r["id"] != room_id]
    save_rooms(rooms)
    return {"status": "success"}

# ─── Zone Bindings ────────────────────────────────────────────────────────────

class BindingDto(BaseModel):
    deviceId: str
    action: str
    extraParams: dict = {}

ZONE_BINDINGS_FILE = os.path.join(DATA_DIR, "zone_bindings.json")

def load_zone_bindings():
    if not os.path.exists(ZONE_BINDINGS_FILE):
        return {}
    with open(ZONE_BINDINGS_FILE, "r", encoding="utf-8") as f:
        return json.load(f)

def save_zone_bindings(data: dict):
    with open(ZONE_BINDINGS_FILE, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)

@app.post("/api/v1/zones/{zone_id}/bindings")
async def save_zone_bindings_endpoint(zone_id: str, bindings: List[BindingDto]):
    data = load_zone_bindings()
    data[zone_id] = [
        {"deviceId": b.deviceId, "action": b.action, "extraParams": b.extraParams}
        for b in bindings
    ]
    save_zone_bindings(data)
    return {"status": "success", "zoneId": zone_id, "bindingsCount": len(bindings)}

# ─── SmartThings ─────────────────────────────────────────────────────────────

class SmartThingsDeviceDto(BaseModel):
    deviceId: str
    name: str
    capabilities: List[str] = []

MOCK_SMARTTHINGS_DEVICES = [
    SmartThingsDeviceDto(deviceId="st_001", name="Lamp Living Room", capabilities=["switch"]),
    SmartThingsDeviceDto(deviceId="st_002", name="AC Unit", capabilities=["switch", "thermostat"]),
    SmartThingsDeviceDto(deviceId="st_003", name="TV", capabilities=["switch"]),
]

@app.get("/api/v1/smartthings/devices")
async def get_smartthings_devices():
    return MOCK_SMARTTHINGS_DEVICES

@app.post("/api/v1/smartthings/devices/{device_id}/{action}")
async def smartthings_command(device_id: str, action: str):
    return {"status": "success", "message": f"Command {action} sent to {device_id}"}

# ─── Social Touch ────────────────────────────────────────────────────────────

class SocialTouchRequest(BaseModel):
    senderName: str
    senderDeviceId: str

social_inbox = {}           # target_device_id -> list of TouchEvent
social_enabled: dict = {}   # device_id -> bool

SOCIAL_ENABLED_FILE = os.path.join(DATA_DIR, "social_enabled.json")

def load_social_enabled():
    global social_enabled
    if os.path.exists(SOCIAL_ENABLED_FILE):
        with open(SOCIAL_ENABLED_FILE, "r") as f:
            social_enabled = json.load(f)
    social_enabled.setdefault("mock_friend_1", True)

def save_social_enabled():
    with open(SOCIAL_ENABLED_FILE, "w") as f:
        json.dump(social_enabled, f, indent=2)

load_social_enabled()

MOCK_FRIEND = {"deviceId": "mock_friend_1", "name": "Тестовый друг", "email": "friend@mock.ru"}
MOCK_FRIENDS_LIST = [MOCK_FRIEND]

@app.get("/api/v1/social/user")
async def find_social_user(deviceId: str = "", name: str = ""):
    if deviceId:
        for u in load_users():
            if u.get("userId") == deviceId:
                return {"deviceId": u["userId"], "name": u["name"]}
        for f in MOCK_FRIENDS_LIST:
            if f["deviceId"] == deviceId:
                return f
    if name:
        for u in load_users():
            if name.lower() in u.get("name", "").lower():
                return {"deviceId": u["userId"], "name": u["name"]}
        for f in MOCK_FRIENDS_LIST:
            if name.lower() in f["name"].lower():
                return f
    raise HTTPException(status_code=404, detail="User not found")

@app.post("/api/v1/social/touch/{target_device_id}")
async def send_social_touch(target_device_id: str, req: Optional[SocialTouchRequest] = None):
    enabled = social_enabled.get(target_device_id, True)
    if not enabled:
        return {"status": "ignored", "message": f"{target_device_id} has social touch disabled"}
    event = {
        "senderName": req.senderName if req else "Simulator",
        "senderDeviceId": req.senderDeviceId if req else "simulator",
        "timestamp": datetime.now().isoformat()
    }
    if target_device_id not in social_inbox:
        social_inbox[target_device_id] = []
    social_inbox[target_device_id].append(event)
    print(f"[Social Touch] {event['senderName']} -> {target_device_id}", flush=True)
    return {"status": "success", "message": f"Touch from {event['senderName']} sent to {target_device_id}"}

@app.get("/api/v1/social/touch/inbox/{my_device_id}")
async def get_social_inbox(my_device_id: str):
    events = social_inbox.pop(my_device_id, [])
    return {"events": events, "count": len(events)}

@app.post("/api/v1/social/enabled")
async def set_social_enabled(data: dict):
    device_id = data.get("deviceId", "default")
    enabled = data.get("enabled", True)
    social_enabled[device_id] = enabled
    save_social_enabled()
    return {"status": "success", "deviceId": device_id, "enabled": enabled}

@app.get("/api/v1/social/enabled/{device_id}")
async def get_social_enabled(device_id: str):
    return {"deviceId": device_id, "enabled": social_enabled.get(device_id, True)}

# ─── Radar Simulator ─────────────────────────────────────────────────────────

SIM_FILE = os.path.join(DATA_DIR, "sim_settings.json")
sim_running = False
sim_thread = None

class SimSettings(BaseModel):
    numTargets: int = 5
    pattern: str = "random"  # "circle", "random", "broken_line"
    targets: List[dict] = []

def load_sim_settings() -> dict:
    if not os.path.exists(SIM_FILE):
        return {"numTargets": 5, "pattern": "random", "targets": []}
    with open(SIM_FILE, "r") as f:
        return json.load(f)

def save_sim_settings(data: dict):
    with open(SIM_FILE, "w") as f:
        json.dump(data, f, indent=2)

@app.get("/api/sim/settings")
async def get_sim_settings():
    return load_sim_settings()

@app.post("/api/sim/settings")
async def update_sim_settings(settings: SimSettings):
    data = settings.dict()
    save_sim_settings(data)
    return {"status": "success"}

@app.post("/api/sim/start")
async def start_sim():
    global sim_running
    sim_running = True
    return {"status": "running"}

@app.post("/api/sim/stop")
async def stop_sim():
    global sim_running
    sim_running = False
    return {"status": "stopped"}

# ─── Web UI ──────────────────────────────────────────────────────────────────

from fastapi.responses import HTMLResponse
from fastapi.staticfiles import StaticFiles

HTML_UI = """<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<title>Radar Simulator</title>
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body { font-family: sans-serif; background: #1a1a2e; color: #fff; padding: 20px; }
  h1 { margin-bottom: 16px; }
  .row { display: flex; gap: 16px; align-items: center; margin-bottom: 12px; flex-wrap: wrap; }
  label { font-size: 14px; color: #aaa; }
  canvas { background: #16213e; border: 1px solid #333; border-radius: 8px; cursor: crosshair; }
  select, input, button {
    background: #0f3460; color: #fff; border: 1px solid #533483;
    padding: 8px 16px; border-radius: 6px; font-size: 14px;
  }
  button:hover { background: #533483; cursor: pointer; }
  button.danger { background: #a32020; border-color: #e94560; }
  button.success { background: #1a6b3c; border-color: #4ecca3; }
  #log { margin-top: 12px; padding: 8px; background: #0f3460; border-radius: 6px; max-height: 120px; overflow-y: auto; font-size: 12px; color: #4ecca3; }
  .status { display: inline-block; width: 10px; height: 10px; border-radius: 50%; margin-right: 6px; }
  .on { background: #4ecca3; } .off { background: #e94560; }
</style>
</head>
<body>
<h1>🎯 Radar Simulator</h1>
<div class="row">
  <label>Количество точек: <input id="numTargets" type="range" min="0" max="20" value="5"></label>
  <span id="numLabel">5</span>
</div>
<div class="row">
  <label>Паттерн:
    <select id="pattern">
      <option value="circle">Окружность</option>
      <option value="random" selected>Случайно</option>
      <option value="broken_line">Случайная ломаная</option>
      <option value="manual">Ручное управление</option>
    </select>
  </label>
  <button id="startBtn" class="success">▶ Запустить</button>
  <button id="stopBtn" class="danger">■ Остановить</button>
  <button id="saveBtn">💾 Сохранить</button>
</div>
<div class="row">
  <button id="socialTouchBtn">📱 Отправить Social Touch</button>
</div>
<canvas id="canvas" width="800" height="600"></canvas>
<div id="log"></div>
<script>
const canvas = document.getElementById("canvas");
const ctx = canvas.getContext("2d");
const log = document.getElementById("log");
let targets = [];
let draggingId = -1;
let isRunning = false;

function rand(min, max) { return Math.random() * (max - min) + min; }

function generateTargets(count) {
  targets = [];
  for (let i = 0; i < count; i++) {
    targets.push({
      id: i,
      x: rand(-3000, 3000),
      y: rand(-3000, 3000),
      speed: rand(50, 500),
      vx: rand(-200, 200),
      vy: rand(-200, 200),
      angle: rand(0, Math.PI * 2),
      originX: 0, originY: 0
    });
  }
}

function worldToScreen(wx, wy) {
  const cx = canvas.width / 2, cy = canvas.height / 2, scale = 0.1;
  return { x: cx + wx * scale, y: cy - wy * scale };
}

function screenToWorld(sx, sy) {
  const cx = canvas.width / 2, cy = canvas.height / 2, scale = 0.1;
  return { x: (sx - cx) / scale, y: -(sy - cy) / scale };
}

function draw() {
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  const cx = canvas.width / 2, cy = canvas.height / 2;

  ctx.strokeStyle = "#333"; ctx.lineWidth = 1;
  for (let i = -3000; i <= 3000; i += 500) {
    const p = worldToScreen(i, 0);
    ctx.beginPath(); ctx.moveTo(p.x, 0); ctx.lineTo(p.x, canvas.height); ctx.stroke();
    const p2 = worldToScreen(0, i);
    ctx.beginPath(); ctx.moveTo(0, p2.y); ctx.lineTo(canvas.width, p2.y); ctx.stroke();
  }

  ctx.fillStyle = "#533483"; ctx.beginPath();
  ctx.arc(cx, cy, 12, 0, Math.PI * 2); ctx.fill();
  ctx.strokeStyle = "#7b68ee"; ctx.lineWidth = 2;
  ctx.stroke();
  ctx.fillStyle = "#fff"; ctx.font = "12px sans-serif";
  ctx.fillText("Радар", cx + 16, cy + 6);

  for (const t of targets) {
    const p = worldToScreen(t.x, t.y);
    const hue = (t.id * 40) % 360;
    ctx.shadowColor = "hsl(" + hue + ", 100%, 50%)";
    ctx.shadowBlur = 15;
    ctx.fillStyle = "hsl(" + hue + ", 100%, 60%)";
    ctx.beginPath(); ctx.arc(p.x, p.y, 10, 0, Math.PI * 2); ctx.fill();
    ctx.shadowBlur = 0;
    ctx.strokeStyle = "#fff"; ctx.lineWidth = 2;
    ctx.stroke();
    ctx.fillStyle = "#fff";
    ctx.fillText("T" + t.id + " (" + Math.round(t.x) + ", " + Math.round(t.y) + ")", p.x + 16, p.y - 6);
  }
}

function updateSim() {
  if (!isRunning) return;
  const pattern = document.getElementById("pattern").value;
  for (const t of targets) {
    if (pattern === "circle") {
      t.angle += 0.02;
      t.x = Math.cos(t.angle) * 2000;
      t.y = Math.sin(t.angle) * 2000;
    } else if (pattern === "random") {
      t.x += t.vx * 0.05;
      t.y += t.vy * 0.05;
      if (Math.abs(t.x) > 2800 || Math.abs(t.y) > 2800) {
        t.vx = rand(-200, 200);
        t.vy = rand(-200, 200);
        t.x = Math.max(-2800, Math.min(2800, t.x));
        t.y = Math.max(-2800, Math.min(2800, t.y));
      }
    } else if (pattern === "broken_line") {
      t.x += t.vx * 0.03;
      t.y += t.vy * 0.03;
      if (Math.random() < 0.01 || Math.abs(t.x) > 2500 || Math.abs(t.y) > 2500) {
        t.vx = rand(-300, 300);
        t.vy = rand(-300, 300);
      }
    }
  }
  draw();
  sendTargets();
  requestAnimationFrame(updateSim);
}

function sendTargets() {
  if (!isRunning) return;
  const data = targets.map(t => ({ id: t.id, x: Math.round(t.x), y: Math.round(t.y), speed: Math.round(t.speed), inZone: false }));
  fetch("/api/sim/targets", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(data) })
    .catch(() => {});
}

canvas.addEventListener("mousedown", (e) => {
  const rect = canvas.getBoundingClientRect();
  const sx = e.clientX - rect.left, sy = e.clientY - rect.top;
  for (let i = targets.length - 1; i >= 0; i--) {
    const p = worldToScreen(targets[i].x, targets[i].y);
    const dx = sx - p.x, dy = sy - p.y;
    if (dx * dx + dy * dy < 400) { draggingId = i; break; }
  }
});

canvas.addEventListener("mousemove", (e) => {
  if (draggingId < 0) return;
  const rect = canvas.getBoundingClientRect();
  const sx = e.clientX - rect.left, sy = e.clientY - rect.top;
  const w = screenToWorld(sx, sy);
  targets[draggingId].x = Math.round(w.x);
  targets[draggingId].y = Math.round(w.y);
  draw();
  sendTargets();
});

canvas.addEventListener("mouseup", () => { draggingId = -1; });
canvas.addEventListener("mouseleave", () => { draggingId = -1; });

document.getElementById("numTargets").addEventListener("input", (e) => {
  const count = parseInt(e.target.value);
  document.getElementById("numLabel").textContent = count;
  generateTargets(count);
  draw();
  sendTargets();
});

document.getElementById("startBtn").addEventListener("click", () => {
  isRunning = true;
  fetch("/api/sim/start", { method: "POST" });
  updateSim();
});

document.getElementById("stopBtn").addEventListener("click", () => {
  isRunning = false;
  fetch("/api/sim/stop", { method: "POST" });
  fetch("/api/sim/targets", { method: "POST", headers: { "Content-Type": "application/json" }, body: "[]" });
});

document.getElementById("saveBtn").addEventListener("click", () => {
  const data = { numTargets: targets.length, pattern: document.getElementById("pattern").value, targets: targets };
  fetch("/api/sim/settings", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(data) });
  log.innerHTML = "<div>✓ Настройки сохранены</div>" + log.innerHTML;
});

document.getElementById("socialTouchBtn").addEventListener("click", () => {
  fetch("/api/v1/social/touch/test_device", { method: "POST" });
  log.innerHTML = "<div>📱 Social Touch отправлен</div>" + log.innerHTML;
});

fetch("/api/sim/settings").then(r => r.json()).then(data => {
  if (data.targets && data.targets.length > 0) {
    targets = data.targets.map(t => ({ ...t, vx: 0, vy: 0, angle: 0 }));
    document.getElementById("numTargets").value = targets.length;
    document.getElementById("numLabel").textContent = targets.length;
  } else {
    generateTargets(parseInt(document.getElementById("numTargets").value));
  }
  if (data.pattern) document.getElementById("pattern").value = data.pattern;
  draw();
  sendTargets();
});
</script>
</body>
</html>
"""

@app.get("/simulator", response_class=HTMLResponse)
async def simulator_ui():
    return HTML_UI

# ─── Simulator target endpoint (receives from web UI, publishes via MQTT) ────

import threading
import time
import math
import random
import sys
import paho.mqtt.client as mqtt
import json as json_lib

sim_manual_targets = []       # данные из Web UI
sim_last_updated = 0.0        # timestamp последнего обновления от Web UI
sim_prev_data_hash = 0        # хеш предыдущих данных — чтобы игнорировать повторения

@app.post("/api/sim/targets")
async def update_sim_targets(data: List[dict]):
    global sim_manual_targets, sim_last_updated, sim_prev_data_hash
    if data:
        h = hash(str(data))
        if h == sim_prev_data_hash:
            return {"ok": True, "ignored": True}   # те же данные — игнорируем
        sim_prev_data_hash = h
        sim_manual_targets = data
        sim_last_updated = time.time()
    else:
        sim_manual_targets = []
        sim_last_updated = 0.0
    return {"ok": True}

mqtt_connected = False

def on_mqtt_connect(client, userdata, flags, rc):
    global mqtt_connected
    if rc == 0:
        mqtt_connected = True
        print(f"[MQTT] Connected to broker.hivemq.com:1883", flush=True)
    else:
        print(f"[MQTT] Connection failed, rc={rc}", flush=True)

def on_mqtt_disconnect(client, userdata, rc):
    global mqtt_connected
    mqtt_connected = False
    print(f"[MQTT] Disconnected, rc={rc}", flush=True)

def mqtt_publish_loop():
    client = mqtt.Client(client_id="mock_radar_main", protocol=mqtt.MQTTv311)
    client.on_connect = on_mqtt_connect
    client.on_disconnect = on_mqtt_disconnect
    client.reconnect_delay_set(min_delay=1, max_delay=120)

    try:
        print("[MQTT] Connecting to broker.hivemq.com:1883 ...", flush=True)
        client.connect("broker.hivemq.com", 1883, 60)
        client.loop_start()
    except Exception as e:
        print(f"[MQTT] Connection error: {e}", flush=True)
        return

    # auto targets state (like old simulator.py)
    auto_angle = [0, 120, 240]
    auto_radius = [1000, 800, 600]
    auto_speed = [0.02, 0.03, 0.015]

    while True:
        try:
            if not mqtt_connected:
                time.sleep(0.5)
                continue

            # Если от Web UI были данные меньше 2 секунд назад — используем их
            use_manual = bool(sim_manual_targets) and (time.time() - sim_last_updated) < 2.0
            time.sleep(0.1 if use_manual else 0.5)

            if use_manual:
                manual_targets = [
                    {"id": t.get("id", i), "x": t.get("x", 0), "y": t.get("y", 0),
                     "speed": t.get("speed", 0), "inZone": t.get("inZone", False)}
                    for i, t in enumerate(sim_manual_targets)
                ]
                persons = [
                    {"targetId": t["id"], "zones": "WorkZone",
                     "x": t["x"], "y": t["y"], "speed": t["speed"]}
                    for t in manual_targets if t["inZone"]
                ]
                ld2450 = {
                    "targets": manual_targets,
                    "zones": [{
                        "name": "WorkZone",
                        "vertices": [{"x": -1000, "y": 500}, {"x": 1000, "y": 500},
                                     {"x": 1000, "y": 1500}, {"x": -1000, "y": 1500}],
                        "pointCount": 4
                    }],
                    "personsInZones": persons
                }
                payload = json_lib.dumps(ld2450)
                client.publish("assistants/1/ld2450", payload)
                continue

            # auto-generate targets (old simulator style)
            targets = []
            for i in range(3):
                auto_angle[i] += auto_speed[i]
                x = int(auto_radius[i] * math.cos(auto_angle[i])) + random.randint(-50, 50)
                y = int(auto_radius[i] * math.sin(auto_angle[i])) + random.randint(-50, 50)
                speed = int(abs(auto_speed[i]) * auto_radius[i] * 10)
                in_zone = random.random() < 0.3
                targets.append({
                    "id": i + 1, "x": x, "y": y, "speed": speed, "inZone": in_zone
                })

            persons = []
            for t in targets:
                if t["inZone"]:
                    persons.append({
                        "targetId": t["id"], "zones": "WorkZone",
                        "x": t["x"], "y": t["y"], "speed": t["speed"]
                    })

            ld2450 = {
                "targets": targets,
                "zones": [{
                    "name": "WorkZone",
                    "vertices": [{"x": -1000, "y": 500}, {"x": 1000, "y": 500},
                                 {"x": 1000, "y": 1500}, {"x": -1000, "y": 1500}],
                    "pointCount": 4
                }],
                "personsInZones": persons
            }
            payload = json_lib.dumps(ld2450)
            client.publish("assistants/1/ld2450", payload)
        except Exception as e:
            print(f"[MQTT] Error in publish loop: {e}", flush=True)
            time.sleep(1)

threading.Thread(target=mqtt_publish_loop, daemon=True).start()

# ─── Health ──────────────────────────────────────────────────────────────────

@app.get("/")
async def root():
    return {"status": "ok", "message": "Smart Assistant Mock Server is running"}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8080)
