#!/usr/bin/env bash  
set -e  

KEYSTORE_FILE="roboqwen-release.keystore"  
ALIAS_NAME="roboqwen_alias"  
UNSIGNED_APK="app/build/outputs/apk/release/app-release-unsigned.apk"  
FINAL_APK="roboqwen-final.apk"  

# Set SDK location fallback
if [ -z "$ANDROID_HOME" ]; then
    export ANDROID_HOME="$HOME/Android/Sdk"
fi

# Locate latest build-tools version
BUILD_TOOLS_DIR=$(ls -d $ANDROID_HOME/build-tools/*/ 2>/dev/null | sort -V | tail -n1)  

if [ -z "$BUILD_TOOLS_DIR" ]; then  
    echo "❌ CRITICAL ERROR: Unable to locate build-tools inside $ANDROID_HOME/build-tools/"  
    exit 1  
fi  

echo "🚀 STAGE 1: Assembling minified Release APK..."  
./gradlew assembleRelease --no-daemon

if [ ! -f "$KEYSTORE_FILE" ]; then  
    echo "🔑 Keystore profile missing. Generating signing certificate..."  
    keytool -genkey -v -keystore "$KEYSTORE_FILE" -alias "$ALIAS_NAME" \  
            -keyalg RSA -keysize 2048 -validity 10000 \  
            -storepass "roboqwen123" -keypass "roboqwen123" \  
            -dname "CN=RoboQwen, O=LocalDev, C=US"  
fi  

echo "🖋️ STAGE 2: Applying cryptographic signatures to output package..."  
"${BUILD_TOOLS_DIR}apksigner" sign --keystore "$KEYSTORE_FILE" \  
    --storepass "roboqwen123" \  
    --out "$FINAL_APK" "$UNSIGNED_APK"  

echo "✅ STAGE 3: Running signature alignment verification..."  
"${BUILD_TOOLS_DIR}apksigner" verify "$FINAL_APK"  

if ! adb devices | grep -q -E "[0-9a-zA-Z]+\s+device"; then  
    echo "⚠️ SYSTEM WARNING: Physical Android hardware device or emulator not detected over ADB."  
    exit 1  
fi  

echo "📲 STAGE 4: Sideloading final binary application archive..."  
adb install -r "$FINAL_APK"  

echo "🎉 Build sequence execution completed successfully."  
echo "📟 STAGE 5: Launching Asynchronous Cognitive Trace Monitor Stream..."  

adb logcat -c  
adb logcat -s ROBO_AGENT_THOUGHT:D System.out:I *:S
