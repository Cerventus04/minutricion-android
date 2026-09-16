#!/usr/bin/env bash
# Ejecuta solo la auditoría de iconos (no los tests de captura). Informe en tools/auditoria_iconos/.
set -e
SDK="$HOME/.buildozer/android/platform/android-sdk"
SRC="/mnt/c/Users/Ivan Sola/Desktop/MiNutricionAndroid"
BUILD="$HOME/minutricion_kt"
export ANDROID_HOME="$SDK"
export PATH="$HOME/gradle-8.7/bin:$PATH"
rsync -a --delete --exclude=.gradle --exclude=build --exclude="app/build" \
  --exclude=local.properties --exclude=.idea --exclude=tools/auditoria_iconos "$SRC/" "$BUILD/"
echo "sdk.dir=$SDK" > "$BUILD/local.properties"
grep '^gemini\.api\.key' "$SRC/local.properties" >> "$BUILD/local.properties" || true
cd "$BUILD"
./gradlew :app:testDebugUnitTest --tests 'org.ivansola.minutricion.IconAuditTest' --console=plain -q
