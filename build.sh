#!/bin/bash
# iappyxOS — Full build script
# Builds shell APK + container app, copies final APK to bin/
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BIN_DIR="$SCRIPT_DIR/bin"
SHELL_DIR="$SCRIPT_DIR/src/shell_apk"
CONTAINER_DIR="$SCRIPT_DIR/src/container_app"

echo "╔═════���════════════════════════╗"
echo "║   iappyxOS Build             ║"
echo "╚═════════��════════════════════╝"
echo ""

# Step 1: Build shell APK
echo "▶ Building shell APK..."
cd "$SHELL_DIR"
./gradlew assembleRelease -q
cp app/build/outputs/apk/release/app-release.apk "$CONTAINER_DIR/assets/shell_template.apk"
echo "✅ Shell APK built and copied"
echo ""

# Step 2: Build container app
echo "▶ Building container app..."
cd "$CONTAINER_DIR"
flutter build apk --release
echo ""

# Step 3: Copy to bin/
mkdir -p "$BIN_DIR"
cp build/app/outputs/flutter-apk/app-release.apk "$BIN_DIR/iappyxOS.apk"
echo "✅ Build complete: bin/iappyxOS.apk"
echo ""

# Step 4: Install if device connected
if adb devices 2>/dev/null | grep -q "device$"; then
    echo "▶ Installing on device..."
    adb install -r "$BIN_DIR/iappyxOS.apk"
    echo "✅ Installed"
else
    echo "ℹ No device connected. APK ready at bin/iappyxOS.apk"
fi
