import paho.mqtt.client as mqtt
import json
import time
import random
import math
import sys

BROKER = "broker.hivemq.com"
PORT = 1883
TOPIC = "assistants/1/ld2450"

targets = [
    {"id": 1, "x": 0, "y": 1000, "speed": 0, "inZone": False},
    {"id": 2, "x": -500, "y": 500, "speed": 0, "inZone": False},
    {"id": 3, "x": 500, "y": -500, "speed": 0, "inZone": False},
]

angle = [0, 120, 240]
radius = [1000, 800, 600]
speed_factor = [0.02, 0.03, 0.015]

def generate_radar_data():
    global angle, targets
    for i in range(3):
        angle[i] += speed_factor[i]
        x = int(radius[i] * math.cos(angle[i]))
        y = int(radius[i] * math.sin(angle[i]))
        x += random.randint(-50, 50)
        y += random.randint(-50, 50)
        speed = int(abs(speed_factor[i]) * radius[i] * 10)
        targets[i]["x"] = x
        targets[i]["y"] = y
        targets[i]["speed"] = speed
        targets[i]["inZone"] = random.random() < 0.3
    data = {
        "targets": targets,
        "zones": [
            {
                "name": "WorkZone",
                "vertices": [
                    {"x": -1000, "y": 500},
                    {"x": 1000, "y": 500},
                    {"x": 1000, "y": 1500},
                    {"x": -1000, "y": 1500}
                ],
                "pointCount": 4
            }
        ],
        "personsInZones": []
    }
    for target in targets:
        if target["inZone"]:
            data["personsInZones"].append({
                "targetId": target["id"],
                "zones": "WorkZone",
                "x": target["x"],
                "y": target["y"],
                "speed": target["speed"]
            })
    return data

def on_connect(client, userdata, flags, rc):
    if rc == 0:
        print(f"Connected to MQTT broker ({BROKER}:{PORT})")
        print(f"Publishing to topic: {TOPIC}")
    else:
        print(f"Failed to connect, return code: {rc}")

def on_disconnect(client, userdata, rc):
    print(f"Disconnected from MQTT broker (rc={rc})")

def main():
    global BROKER, PORT

    if len(sys.argv) > 1 and sys.argv[1] == "--local":
        BROKER = "localhost"
        print("Mode: LOCAL broker (localhost:1883)")
    else:
        print("Mode: PUBLIC broker (broker.hivemq.com:1883)")
        print("  If connection fails, port 1883 may be blocked.")
        print("  Run with --local for local Mosquitto.\n")

    client = mqtt.Client()
    client.on_connect = on_connect
    client.on_disconnect = on_disconnect

    try:
        print(f"Connecting to {BROKER}:{PORT}...")
        client.connect(BROKER, PORT, 60)
        client.loop_start()
        print("Radar Simulator started")
        print(f"Topic: {TOPIC}")
        print("Press Ctrl+C to stop\n")

        while True:
            data = generate_radar_data()
            payload = json.dumps(data)
            result = client.publish(TOPIC, payload)
            if result.rc != mqtt.MQTT_ERR_SUCCESS:
                print(f"Publish error: {result.rc}")
            time.sleep(0.5)
    except KeyboardInterrupt:
        print("\nStopping simulator...")
        client.loop_stop()
        client.disconnect()
    except Exception as e:
        print(f"FATAL: {e}")

if __name__ == "__main__":
    main()
