#!/usr/bin/env bash
set -e

echo "🧹 STAGE 0: Sanitizing source files..."
find app/src/main/java app/src/main/res -type f \( -name "*.kt" -o -name "*.xml" \) -exec sed -i 's/\xA0/ /g' {} +

echo "🚀 STAGE 1: Assembling Debug APK..."
./gradlew assembleDebug --no-daemon

echo "📲 STAGE 2: Installing APK to target device..."
ADB_DEVICE=$(adb devices | grep -v "List" | grep "device" | awk '{print $1}' | head -n 1)

if [ -z "$ADB_DEVICE" ]; then
    echo "❌ ERROR: No active ADB device connected."
    exit 1
fi

adb -s "$ADB_DEVICE" install -r app/build/outputs/apk/debug/app-debug.apk

echo "🎉 STAGE 3: Launching Client..."
adb -s "$ADB_DEVICE" shell am start -n com.example.roboqwen/.MainActivity

echo "✅ Deployment completed successfully!"
