#!/bin/bash

echo "Building APK..."
gradle assembleDebug

APK_PATH="./app/build/outputs/apk/debug/app-debug.apk"

if [ -f "$APK_PATH" ]; then
    echo "Installing APK on device..."
    adb install -r $APK_PATH
    echo "Launching app..."
    adb shell am start -n com.minimy.minimy/.MainActivity
else
    echo "APK not found. Build failed."
fi


#  ls app/src/main/java/com/example/myapp/*.java app/src/main/res/layout/*.xml | entr ./run.sh
