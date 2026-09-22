#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
SDK_PATH="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
BUILD_TOOLS="$SDK_PATH/build-tools/${ANDROID_BUILD_TOOLS_VERSION:-35.0.0}"
ANDROID_JAR="$SDK_PATH/platforms/android-36/android.jar"
if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/libexec/java_home ]; then
    JAVA_HOME="$(/usr/libexec/java_home)"
    export JAVA_HOME
fi
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
# Keep this ignored keystore to sign future updates with the same identity.
KEYSTORE="${POKEWILDS_KEYSTORE:-$PWD/launcher-signing.p12}"
export POKEWILDS_STORE_PASSWORD="${POKEWILDS_STORE_PASSWORD:-android}"
mkdir -p classes dex ../bin
"$BUILD_TOOLS/aapt2" compile --dir res -o resources.zip
"$BUILD_TOOLS/aapt2" link -o unsigned.apk -I "$ANDROID_JAR" --manifest AndroidManifest.xml resources.zip
javac --release 8 -classpath "$ANDROID_JAR" -d classes src/local/pokewilds/launcher/MainActivity.java
"$BUILD_TOOLS/d8" --lib "$ANDROID_JAR" --min-api 26 --output dex classes/local/pokewilds/launcher/MainActivity.class
(cd dex && zip -q ../unsigned.apk classes.dex)
"$BUILD_TOOLS/zipalign" -f 4 unsigned.apk aligned.apk
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair -keystore "$KEYSTORE" -storepass:env POKEWILDS_STORE_PASSWORD -keypass:env POKEWILDS_STORE_PASSWORD -alias pokewilds -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=PokeWilds Local Launcher' -storetype PKCS12
fi
"$BUILD_TOOLS/apksigner" sign --ks "$KEYSTORE" --ks-pass env:POKEWILDS_STORE_PASSWORD --out ../bin/PokeWilds-Launcher.apk aligned.apk
"$BUILD_TOOLS/apksigner" verify --verbose ../bin/PokeWilds-Launcher.apk
