#!/bin/bash
set -e

SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/home/vertigo/android-sdk}}"
BUILD_TOOLS="$SDK_DIR/build-tools/34.0.0"
PLATFORM_JAR="$SDK_DIR/platforms/android-34/android.jar"
JAVA_BIN="${JAVA_HOME:-/home/vertigo/android-sdk/jdk-17}/bin"

export PATH="$JAVA_BIN:$BUILD_TOOLS:$PATH"

echo "=== Cleaning build directory ==="
rm -rf build compiled_res.zip unaligned.apk aligned.apk claude-amoled.apk
mkdir -p build/stubs_classes build/classes build/dex

echo "=== Compiling Xposed stubs ==="
javac -d build/stubs_classes $(find stubs -name "*.java")

echo "=== Compiling HookEntry ==="
javac -cp "$PLATFORM_JAR:build/stubs_classes" -d build/classes $(find src -name "*.java")

echo "=== Converting to DEX ==="
d8 --lib "$PLATFORM_JAR" --classpath build/stubs_classes --output build/dex $(find build/classes -name "*.class")

echo "=== Compiling Resources ==="
aapt2 compile --dir res -o compiled_res.zip
aapt2 link -I "$PLATFORM_JAR" --manifest AndroidManifest.xml -o unaligned.apk compiled_res.zip

echo "=== Packaging APK ==="
(cd build/dex && zip -u ../../unaligned.apk classes.dex)
zip -u -r unaligned.apk assets

echo "=== Aligning APK ==="
zipalign -f -p 4 unaligned.apk aligned.apk

if [ ! -f debug.keystore ]; then
    echo "=== Generating Debug Keystore ==="
    keytool -genkey -v -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
fi

echo "=== Signing APK ==="
apksigner sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android --out claude-amoled.apk aligned.apk

echo "=== Build Successful: claude-amoled.apk ==="
ls -lh claude-amoled.apk
