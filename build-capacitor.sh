#!/bin/bash
# Build script for Capacitor Android APK
# This script temporarily modifies the project for static export, builds, syncs, and restores

set -e
echo "🔧 NEXA AI — Capacitor Android Build Script"
echo "============================================="

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_DIR"

# Step 1: Save original files
echo "📦 Step 1: Backing up API routes and preview..."
[ -d src/app/api ] && mv src/app/api src/api-backup
[ -d src/app/preview ] && mv src/app/preview src/preview-backup
mkdir -p src/app/api

# Step 2: Clean previous builds
echo "🧹 Step 2: Cleaning previous builds..."
rm -rf .next out

# Step 3: Build Next.js static export
echo "⚡ Step 3: Building Next.js static export..."
npm run build

# Step 4: Verify index.html exists
if [ ! -f out/index.html ]; then
    echo "❌ ERROR: index.html not found in out/ directory!"
    echo "   The static export may have failed."
    exit 1
fi
echo "✅ index.html generated successfully"

# Step 5: Sync with Capacitor
echo "📱 Step 5: Syncing with Capacitor..."
npx cap sync android

# Step 6: Restore original files
echo "♻️ Step 6: Restoring API routes and preview..."
rm -rf src/app/api
[ -d src/api-backup ] && mv src/api-backup src/app/api
[ -d src/preview-backup ] && mv src/preview-backup src/app/preview

# Step 7: Build Android APK
echo "🏗️ Step 7: Building Android APK..."
cd android
chmod +x gradlew
./gradlew assembleDebug
cd ..

APK_PATH="android/app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK_PATH" ]; then
    echo ""
    echo "🎉 BUILD SUCCESSFUL!"
    echo "📱 APK location: $APK_PATH"
else
    echo ""
    echo "⚠️ APK not found at expected location. Check android/app/build/outputs/"
fi
