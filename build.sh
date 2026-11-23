#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/.local/android-sdk}}"
TOOLS="$SDK/build-tools/35.0.0"
ANDROID_JAR="$SDK/platforms/android-35/android.jar"
if [[ -z "${JAVA_HOME:-}" ]] && [[ -x "$HOME/apps/jdk/temurin-25.0.5.1/bin/javac" ]]; then
  JAVA_HOME="$HOME/apps/jdk/temurin-25.0.5.1"
fi
if [[ -n "${JAVA_HOME:-}" ]]; then export JAVA_HOME; export PATH="$JAVA_HOME/bin:$PATH"; fi
for tool in "$TOOLS/aapt" "$TOOLS/d8" "$TOOLS/zipalign" "$TOOLS/apksigner"; do
  [[ -x "$tool" ]] || { echo "Missing Android build tool: $tool" >&2; exit 1; }
done
[[ -f "$ANDROID_JAR" ]] || { echo "Missing Android API 35 platform: $ANDROID_JAR" >&2; exit 1; }
command -v javac >/dev/null || { echo "A JDK is required (set JAVA_HOME)." >&2; exit 1; }
command -v keytool >/dev/null || { echo "JDK keytool is required." >&2; exit 1; }
command -v zip >/dev/null || { echo "The zip command is required." >&2; exit 1; }
mkdir -p build/classes build/dex
rm -rf build/classes/* build/dex/* build/resources.ap_ build/unsigned.apk build/aligned.apk build/BrainSinger-Open-debug.apk
"$TOOLS/aapt" package -f -M app/src/main/AndroidManifest.xml -S app/src/main/res -I "$ANDROID_JAR" -F build/resources.ap_
mapfile -t SOURCES < <(find app/src/main/java -name '*.java' -print | sort)
javac -encoding UTF-8 --release 8 -classpath "$ANDROID_JAR" -d build/classes "${SOURCES[@]}"
mapfile -t CLASSES < <(find build/classes -name '*.class' -print | sort)
"$TOOLS/d8" --lib "$ANDROID_JAR" --min-api 23 --output build/dex "${CLASSES[@]}"
cp build/resources.ap_ build/unsigned.apk
(cd build/dex && zip -q ../unsigned.apk classes.dex)
"$TOOLS/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk
if [[ ! -f build/debug.keystore ]]; then
  keytool -genkeypair -keystore build/debug.keystore -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
    -dname 'CN=Android Debug,O=Development,C=US' >/dev/null 2>&1
fi
"$TOOLS/apksigner" sign --ks build/debug.keystore --ks-key-alias androiddebugkey \
  --ks-pass pass:android --key-pass pass:android --out build/BrainSinger-Open-debug.apk build/aligned.apk
"$TOOLS/apksigner" verify --verbose build/BrainSinger-Open-debug.apk
echo "Built build/BrainSinger-Open-debug.apk"
