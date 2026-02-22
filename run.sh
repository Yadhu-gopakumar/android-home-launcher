#!/bin/bash
echo "🚀 Building APK..."
./gradlew clean assembleDebug

# Check if the build was actually successful
if [ $? -eq 0 ]; then
    echo "✅ Build Successful!"
    
    # Use 'find' to grab the first APK it sees in the debug folder
    APK_PATH=$(find app/build/outputs/apk/debug -name "*.apk" | head -n 1)
    
    if [ -n "$APK_PATH" ]; then
        echo "📲 Installing $APK_PATH on device..."
        adb install -r "$APK_PATH"
        
        echo "🚀 Launching Minimy..."
        adb shell am start -n com.minimy.minimy/.MainActivity
    else
        echo "❌ APK not found. The folder might be empty."
    fi
else
    echo "❌ Build failed. Check the Gradle errors above."
fi