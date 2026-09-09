#!/usr/bin/env bash
# Renderiza las pantallas a PNG en la JVM (sin emulador) -> app/src/test/snapshots/images/
set -e
SDK="$HOME/.buildozer/android/platform/android-sdk"
SRC="/mnt/c/Users/Ivan Sola/Desktop/MiNutricionAndroid"
BUILD="$HOME/minutricion_kt"
export ANDROID_HOME="$SDK"
export PATH="$HOME/gradle-8.7/bin:$PATH"
rsync -a --delete --exclude=.gradle --exclude=build --exclude="app/build" \
  --exclude=local.properties --exclude=.idea "$SRC/" "$BUILD/"
echo "sdk.dir=$SDK" > "$BUILD/local.properties"
grep '^gemini\.api\.key' "$SRC/local.properties" >> "$BUILD/local.properties" || true
cd "$BUILD"
./gradlew :app:recordPaparazziDebug --console=plain
mkdir -p "$SRC/bin/snapshots"
cp "$BUILD"/app/src/test/snapshots/images/*.png "$SRC/bin/snapshots/" 2>/dev/null || true
echo "PNG -> $SRC/bin/snapshots/"
