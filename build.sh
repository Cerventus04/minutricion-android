#!/usr/bin/env bash
# Compila el APK de depuración de Mi Nutrición (Kotlin + Compose) desde WSL.
# Uso (desde WSL):  bash /mnt/c/Users/Ivan\ Sola/Desktop/MiNutricionAndroid/build.sh
set -e

SDK="$HOME/.buildozer/android/platform/android-sdk"   # SDK reutilizado de buildozer (+ platform-34)
SRC="/mnt/c/Users/Ivan Sola/Desktop/MiNutricionAndroid"
BUILD="$HOME/minutricion_kt"                            # dir persistente (cache de Gradle)
export ANDROID_HOME="$SDK"
export PATH="$HOME/gradle-8.7/bin:$PATH"

# Copia el fuente (sin artefactos) al dir de build persistente.
rsync -a --delete \
  --exclude=.gradle --exclude=build --exclude="app/build" \
  --exclude=local.properties --exclude=.idea \
  "$SRC/" "$BUILD/"
echo "sdk.dir=$SDK" > "$BUILD/local.properties"
# la clave de Gemini vive en el local.properties de origen (excluido del rsync a propósito, para
# no arrastrar la ruta del SDK de Windows); se copia aparte para que "Rellenar con IA" funcione.
grep '^gemini\.api\.key' "$SRC/local.properties" 2>/dev/null >> "$BUILD/local.properties" || true

cd "$BUILD"
# RELEASE (R8/minify, no debuggable) → scroll mucho más fluido que debug, como Fitia.
./gradlew :app:assembleRelease --console=plain

mkdir -p "$SRC/bin"
# el nombre sale del versionName real (antes iba fijo y se quedaba desfasado al subir de versión)
VER=$(sed -n 's/.*versionName *= *"\([^"]*\)".*/\1/p' "$SRC/app/build.gradle.kts" | head -1)
cp "$BUILD/app/build/outputs/apk/release/app-release.apk" "$SRC/bin/MiNutricion-$VER.apk"
echo "APK -> $SRC/bin/MiNutricion-$VER.apk"
