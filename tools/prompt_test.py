"""Prueba los prompts POR FOTO de Gemini.kt contra la API real, con el texto exacto del fuente."""
import base64
import json
import re
import sys
import time
import urllib.request

ROOT = r"C:\Users\Ivan Sola\Desktop\MiNutricionAndroid"
GEM = ROOT + r"\app\src\main\java\org\ivansola\minutricion\data\Gemini.kt"
src = open(GEM, encoding="utf-8").read()

# --- hints del enum Shot (cadenas Kotlin concatenadas con " + " y multilinea)
enum_block = src.split("enum class Shot", 1)[1].split("}", 1)[0]
hints = {}
for name in ("EAN", "NUTRITION", "FRONT"):
    frag = enum_block.split(name + "(", 1)[1].split("),", 1)[0]
    hints[name] = " ".join(re.findall(r'"([^"]*)"', frag)).replace("  ", " ")

# --- plantilla del prompt
body = src.split('return """', 1)[1].split('""".trimIndent()', 1)[0]
keys = re.findall(r'"(\w+)" to "(\w+)"', src.split("NUTRIENT_KEYS = listOf(", 1)[1].split("\n    )", 1)[0])
nutr = ", ".join('"%s": %s' % (k, u) for k, u in keys)

cat_src = open(ROOT + r"\app\src\main\java\org\ivansola\minutricion\data\FoodCategories.kt", encoding="utf-8").read()
cats = ", ".join(re.findall(r'FoodCategory\("([^"]+)"', cat_src)) or "Suplementos, Lacteos, Bebidas"

key = [l.split("=", 1)[1].strip() for l in open(ROOT + r"\local.properties", encoding="utf-8")
       if l.startswith("gemini.api.key=")][0]

img, shot = sys.argv[1], sys.argv[2]
prompt = (body.replace("${shot.hint}", hints[shot])
              .replace("$cats", cats).replace("$nutr", nutr))
prompt = "\n".join(l.strip() for l in prompt.strip().splitlines())

data = base64.b64encode(open(img, "rb").read()).decode()
req = {"contents": [{"parts": [{"text": prompt},
                               {"inline_data": {"mime_type": "image/jpeg", "data": data}}]}],
       "generationConfig": {"response_mime_type": "application/json"}}
t = time.time()
r = json.load(urllib.request.urlopen(urllib.request.Request(
    "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent?key=" + key,
    data=json.dumps(req).encode(), headers={"Content-Type": "application/json"})))
print("== %s  %.1fs   hint=%s" % (shot, time.time() - t, hints[shot][:60]))
print(r["candidates"][0]["content"]["parts"][0]["text"][:500])
