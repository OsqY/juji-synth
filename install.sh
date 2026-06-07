#!/bin/bash
# install.sh — Build and install Juji Synth on a connected Android device
#
# Usage:
#   ./install.sh              Build debug APK and install
#   ./install.sh release      Build release APK and install
#   ./install.sh --reinstall  Force reinstall (clear data first)
#
# Requirements: Android device connected via USB with USB debugging enabled.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

APK_DIR="app/build/outputs/apk"
BUILD_TYPE="${1:-debug}"

# Validate build type
if [ "$BUILD_TYPE" != "debug" ] && [ "$BUILD_TYPE" != "release" ]; then
    echo "Usage: $0 [debug|release] [--reinstall]"
    exit 1
fi

REINSTALL=false
if [ "${2:-}" = "--reinstall" ] || [ "${1:-}" = "--reinstall" ]; then
    REINSTALL=true
    BUILD_TYPE="debug"
fi

echo "🔨 Building $BUILD_TYPE APK..."
./gradlew "assemble${BUILD_TYPE^}"

APK_PATH="$APK_DIR/$BUILD_TYPE/app-${BUILD_TYPE}.apk"

if [ ! -f "$APK_PATH" ]; then
    echo "❌ APK not found at $APK_PATH"
    exit 1
fi

echo "📱 Checking for connected device..."
DEVICES=$(adb devices | grep -v "List" | grep "device$" | wc -l)

if [ "$DEVICES" -eq 0 ]; then
    echo "❌ No Android device detected. Connect your device via USB and enable USB debugging."
    echo ""
    echo "   Quick steps:"
    echo "   1. Enable Developer Options: Settings → About → Tap 'Build Number' 7×"
    echo "   2. Enable USB Debugging: Settings → Developer Options → USB Debugging"
    echo "   3. Connect via USB and accept the debugging prompt on your device"
    exit 1
fi

echo "📱 Device found! $DEVICES device(s) connected."

if [ "$REINSTALL" = true ]; then
    echo "🗑️  Uninstalling previous version..."
    adb uninstall com.jujisynth.app 2>/dev/null || true
fi

echo "📲 Installing Juji Synth..."
adb install -r "$APK_PATH"

echo ""
echo "✅ Juji Synth installed successfully!"
echo ""
echo "   Launch the app from your app drawer (icon: 'Juji Synth')"
echo "   or run: adb shell monkey -p com.jujisynth.app 1"
echo ""
echo "📋 To view logcat: adb logcat -s JujiSynth"
