#!/usr/bin/env bash
# Tests JVM rápidos (sin capturas): uso  run_tests.sh 'org.ivansola.minutricion.TextMatchTest'
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
ARGS=""
for t in "$@"; do ARGS="$ARGS --tests $t"; done
./gradlew :app:testDebugUnitTest $ARGS --console=plain -q 2>&1 | grep -vE "^w: |warning:" || true
R="$BUILD/app/build/test-results/testDebugUnitTest"
python3 - "$R" <<'PY'
import sys, glob, xml.etree.ElementTree as ET
tot=fail=0
for f in glob.glob(sys.argv[1]+"/*.xml"):
    r=ET.parse(f).getroot(); tot+=int(r.get("tests")); fail+=int(r.get("failures"))+int(r.get("errors"))
    for tc in r.iter("testcase"):
        for fl in list(tc.findall("failure"))+list(tc.findall("error")):
            print("FALLO", tc.get("classname").split('.')[-1], tc.get("name"), "->", (fl.get("message") or "")[:200])
print(f"tests: {tot}  fallos: {fail}")
PY
