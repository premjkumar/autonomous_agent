#!/usr/bin/env bash
set -e

PORT=8000

echo "=== 1. Checking Active Device Connectivity ==="
ADB_DEVICE=$(adb devices | grep -v "List" | grep "device" | awk '{print $1}' | head -n 1)

if [ -z "$ADB_DEVICE" ]; then
    echo "❌ ERROR: No active ADB device detected over USB."
    echo "Ensure USB debugging is enabled and reconnect the cable."
    exit 1
fi
echo "📱 Device Detected: $ADB_DEVICE"

echo "=== 2. Probing Host Middleware Server ==="
if ! pgrep -f "uvicorn" > /dev/null; then
    echo "⚠️ WARNING: Uvicorn server process not running on host!"
    echo "Please launch ./start_server.sh in another terminal."
else
    echo "✅ Host Uvicorn server process is active."
fi

echo "=== 3. Resetting ADB Reverse Transport Tunnel ==="
adb -s "$ADB_DEVICE" reverse --remove-all || true
adb -s "$ADB_DEVICE" reverse tcp:${PORT} tcp:${PORT}
echo "🔗 Tunnel Established: Android 127.0.0.1:${PORT} -> Fedora Host:${PORT}"

echo "=== 4. Probing Android Loopback Link ==="
PROBE_RESULT=$(adb -s "$ADB_DEVICE" shell "curl -s -w '%{http_code}' -X POST http://127.0.0.1:${PORT}/chat -H 'Content-Type: application/json' -d '{\"prompt\": \"time\"}'" 2>/dev/null || echo "FAILED")

if [[ "$PROBE_RESULT" == *"200"* ]]; then
    echo "✅ Transport Link Alive! Endpoint responded with 200 OK."
else
    echo "❌ ERROR: Loopback probe failed (Response: $PROBE_RESULT)."
    echo "Check firewall rules or verify start_server.sh is listening on 0.0.0.0."
    exit 1
fi

echo "--------------------------------------------------------"
echo "🤖 Neural Link Transport Fully Restored!"
echo "Streaming live speech recognizer & transport logs..."
echo "Press CTRL+C to stop log stream."
echo "--------------------------------------------------------"

adb -s "$ADB_DEVICE" logcat -v time SpeechRecognizer:V RoboQwen:D *:S
