"""Reproduce EXACTAMENTE la peticion de la app: prompt por foto + response_schema + temperature 0."""
import base64
import json
import re
import sys
import time
import urllib.error
import urllib.request

ROOT = r"C:\Users\Ivan Sola\Desktop\MiNutricionAndroid"
src = open(ROOT + r"\app\src\main\java\org\ivansola\minutricion\data\Gemini.kt", encoding="utf-8").read()
cat_src = open(ROOT + r"\app\src\main\java\org\ivansola\minutricion\data\FoodCategories.kt", encoding="utf-8").read()
props = open(ROOT + r"\local.properties", encoding="utf-8").read().splitlines()
KEY1 = [l.split("=", 1)[1].strip() for l in props if l.startswith("gemini.api.key=")][0]
KEY2 = [l.split("=", 1)[1].strip() for l in props if l.startswith("gemini.api.key.2=")]
KEY2 = KEY2[0] if KEY2 else KEY1

CATS = re.findall(r'FoodCat\("([^"]+)"', cat_src)
KEYS = re.findall(r'"(\w+)" to "(\w+)"', src.split("NUTRIENT_KEYS = listOf(", 1)[1].split("\n    )", 1)[0])

# --- prompt tal cual lo arma Gemini.kt
enum_block = src.split("enum class Shot", 1)[1].split("}", 1)[0]
HINTS = {}
for name in ("EAN", "NUTRITION", "FRONT"):
    frag = enum_block.split(name + "(", 1)[1].split("),", 1)[0]
    HINTS[name] = " ".join(re.findall(r'"([^"]*)"', frag))
body_tpl = src.split('return """', 1)[1].split('""".trimIndent()', 1)[0]
nutr = ", ".join('"%s": %s' % (k, u) for k, u in KEYS)

# --- esquema tal cual lo arma schema()
num = lambda: {"type": "NUMBER", "nullable": True}
st = lambda: {"type": "STRING", "nullable": True}
SCHEMA = {"type": "OBJECT", "required": ["found"], "properties": {
    "found": {"type": "BOOLEAN"}, "name": st(),
    "category": {"type": "STRING", "nullable": True, "enum": CATS}, "ean": st(),
    "basis": {"type": "STRING", "nullable": True, "enum": ["100g", "serving"]},
    "serving_g": num(), "serving_name": st(),
    "kcal": num(), "protein": num(), "carbs": num(), "fat": num(),
    "nutrients": {"type": "OBJECT", "properties": {k: num() for k, _ in KEYS}}}}


def run(img, shot, model, key, use_schema=False):
    p = body_tpl.replace("${shot.hint}", HINTS[shot]).replace("$cats", ", ".join(CATS)).replace("$nutr", nutr)
    p = "\n".join(l.strip() for l in p.strip().splitlines())
    gen = {"response_mime_type": "application/json", "temperature": 0}
    if use_schema:
        gen["response_schema"] = SCHEMA
    b = base64.b64encode(open(img, "rb").read()).decode()
    req = {"contents": [{"parts": [{"text": p},
                                   {"inline_data": {"mime_type": "image/jpeg", "data": b}}]}],
           "generationConfig": gen}
    r = urllib.request.Request(
        "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s" % (model, key),
        data=json.dumps(req).encode(), headers={"Content-Type": "application/json"})
    t = time.time()
    try:
        res = json.load(urllib.request.urlopen(r))
    except urllib.error.HTTPError as e:
        print("  %-9s %-18s RECHAZADO %s" % (shot, model, json.loads(e.read().decode())["error"]["message"][:80]))
        return
    cand = res["candidates"][0]
    txt = cand["content"]["parts"][0].get("text", "") if cand.get("content") else ""
    try:
        d = json.loads(txt)
    except Exception:
        print("  %-9s %-18s SIN JSON (finish=%s) %r" % (shot, model, cand.get("finishReason"), txt[:80]))
        return
    n = {k: v for k, v in (d.get("nutrients") or {}).items() if v is not None}
    print("  %-9s %-18s %4.1fs found=%s ean=%s name=%r nutr=%d kcal=%s" % (
        shot, model, time.time() - t, d.get("found"), d.get("ean"), (d.get("name") or "")[:28], len(n), d.get("kcal")))


if __name__ == "__main__":
    img = sys.argv[1]
    key = KEY2 if len(sys.argv) > 2 and sys.argv[2] == "2" else KEY1
    for model in ("gemini-3.6-flash", "gemini-3.5-flash"):
        for shot in ("EAN", "NUTRITION", "FRONT"):
            run(img, shot, model, key)
