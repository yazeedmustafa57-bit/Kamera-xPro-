#!/bin/bash
# SmartCam Pro - APK Build Script
# Usage: ./build-apk.sh [debug|release]

set -e

BUILD_TYPE=${1:-release}
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ANDROID_DIR="$SCRIPT_DIR/android-app"

echo "╔══════════════════════════════════════╗"
echo "║   SmartCam Pro APK Builder v1.1.0   ║"
echo "╚══════════════════════════════════════╝"
echo ""

# Check Java
if ! command -v java &> /dev/null; then
    echo "❌ Java not found. Install JDK 17+"
    exit 1
fi
echo "✅ Java: $(java -version 2>&1 | head -1)"

# Check Android SDK
if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
    # Try common locations
    for dir in ~/Android/Sdk /opt/android-sdk /usr/local/android-sdk; do
        if [ -d "$dir" ]; then
            export ANDROID_HOME="$dir"
            break
        fi
    done
fi

if [ -z "$ANDROID_HOME" ]; then
    echo "❌ ANDROID_HOME not set"
    echo "   Set: export ANDROID_HOME=/path/to/android-sdk"
    exit 1
fi
echo "✅ Android SDK: $ANDROID_HOME"

# Navigate to android directory
cd "$ANDROID_DIR"

# Make gradlew executable
chmod +x gradlew

# Build
echo ""
echo "Building $BUILD_TYPE APK..."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━"

if [ "$BUILD_TYPE" = "debug" ]; then
    ./gradlew assembleDebug --no-daemon 2>&1 | tail -20
    APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
else
    ./gradlew assembleRelease --no-daemon 2>&1 | tail -20
    APK_PATH="app/build/outputs/apk/release/app-release.apk"
fi

# Check result
if [ -f "$APK_PATH" ]; then
    APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
    echo ""
    echo "╔══════════════════════════════════════╗"
    echo "║         ✅ BUILD SUCCESSFUL          ║"
    echo "╠══════════════════════════════════════╣"
    echo "║  APK: $APK_PATH"
    echo "║  Size: $APK_SIZE"
    echo "║  Type: $BUILD_TYPE"
    echo "╚══════════════════════════════════════╝"
    echo ""
    echo "Install on device:"
    echo "  adb install $APK_PATH"
else
    echo ""
    echo "❌ Build failed. Check errors above."
    exit 1
fi
