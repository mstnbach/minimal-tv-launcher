#!/bin/sh
set -eu
if [ -z "${JAVA_HOME:-}" ] && [ -d /usr/lib/jvm/java-17-openjdk-amd64 ]; then
    JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
    export JAVA_HOME
    PATH="$JAVA_HOME/bin:$PATH"
    export PATH
fi
MODE=${1:-debug}
case "$MODE" in
    debug) [ "$#" -le 1 ] || { echo "Usage: $0 [debug | release KEYSTORE ALIAS]" >&2; exit 2; } ;;
    release)
        [ "$#" -eq 3 ] || { echo "Usage: $0 release KEYSTORE ALIAS" >&2; exit 2; }
        [ -f "$2" ] || { echo "Keystore not found: $2" >&2; exit 2; }
        RELEASE_KEYSTORE=$(cd "$(dirname "$2")" && pwd)/$(basename "$2")
        RELEASE_ALIAS=$3
        case "$RELEASE_KEYSTORE" in "$(pwd)/build/"*) echo "Keep the keystore outside build/; it is cleared during builds." >&2; exit 2 ;; esac
        ;;
    *) echo "Usage: $0 [debug | release KEYSTORE ALIAS]" >&2; exit 2 ;;
esac
SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/android-sdk}}
TOOLS="$SDK/build-tools/34.0.0"
ANDROID="$SDK/platforms/android-35/android.jar"
OUT=build
rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/generated"
"$TOOLS/aapt" package -f -m -M app/src/main/AndroidManifest.xml -S app/src/main/res -J "$OUT/generated" -I "$ANDROID" -F "$OUT/resources.ap_"
find app/src/main/java "$OUT/generated" -name '*.java' > "$OUT/sources.txt"
javac -source 8 -target 8 -classpath "$ANDROID" -d "$OUT/classes" @"$OUT/sources.txt"
"$TOOLS/d8" --lib "$ANDROID" --min-api 23 --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
cp "$OUT/resources.ap_" "$OUT/unsigned.apk"
(cd "$OUT/dex" && "$TOOLS/aapt" add ../unsigned.apk classes.dex >/dev/null)
"$TOOLS/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
if [ "$MODE" = release ]; then
    "$TOOLS/apksigner" sign --ks "$RELEASE_KEYSTORE" --ks-key-alias "$RELEASE_ALIAS" --ks-pass stdin --out "$OUT/launcher-release.apk" "$OUT/aligned.apk"
    "$TOOLS/apksigner" verify "$OUT/launcher-release.apk"
    echo "APK: $OUT/launcher-release.apk"
else
    if [ ! -f debug.keystore ]; then
        keytool -genkeypair -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -dname 'CN=Android Debug,O=Android,C=US' -keyalg RSA -keysize 2048 -validity 10000 >/dev/null 2>&1
    fi
    "$TOOLS/apksigner" sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android --out "$OUT/launcher.apk" "$OUT/aligned.apk"
    echo "APK: $OUT/launcher.apk"
fi
