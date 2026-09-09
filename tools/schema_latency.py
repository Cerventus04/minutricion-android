"""Mide si response_schema hace mas lenta la lectura: pares con/sin esquema, alternados."""
import base64
import json
import re
import statistics
import sys
import time
import urllib.error
import urllib.request

ROOT = r"C:\Users\Ivan Sola\Desktop\MiNutricionAndroid"
src = open(ROOT + r"\app\src\main\java\org\ivansola\minutricion\data\Gemini.kt", encoding="utf-8").read()
cat_src = open(ROOT + r"\app\src\main\java\org\ivansola\minutricion\data\FoodCategories.kt", encoding="utf-8").read()
KEY = [l.split("=", 1)[1].strip() for l in open(ROOT + r"\local.properties", encoding="utf-8")
       if l.startswith("gemini.api.key=")][0]
MODEL = sys.argv[3] if len(sys.argv) > 3 else "gemini-3.5-flash"
CATS = re.findall(r'FoodCat\("([^"]+)"', cat_src)
KEYS = [k for k, _ in re.findall(r'"(\w+)" to "(\w+)"',
        src.split("NUTRIENT_KEYS = listOf(", 1)[1].split("\n    )", 1)[0])]
num = lambda: {"type": "NUMBER", "nullable": True}
st = lambda: {"type": "STRING", "nullable": True}
SCHEMA = {"type": "OBJECT", "required": ["found"], "properties": {
    "found": {"type": "BOOLEAN"}, "name": st(),
    "category": {"type": "STRING", "nullable": True, "enum": CATS}, "ean": st(),
    "basis": {"type": "STRING", "nullable": True, "enum": ["100g", "serving"]},
    "serving_g": num(), "serving_name": st(),
    "kcal": num(), "protein": num(), "carbs": num(), "fat": num(),
    "nutrients": {"type": "OBJECT", "properties": {k: num() for k in KEYS}}}}
PROMPT = ("Lee esta foto de un producto alimenticio o complemento y devuelve un JSON con found, "
          "name, category, ean, basis, serving_g, serving_name, kcal, protein, carbs, fat y "
          "nutrients. Rellena solo lo que veas; null en lo demas. No inventes valores.")


def call(b, use_schema):
    gen = {"response_mime_type": "application/json", "temperature": 0}
    if use_schema:
        gen["response_schema"] = SCHEMA
    body = {"contents": [{"parts": [{"text": PROMPT},
                                    {"inline_data": {"mime_type": "image/jpeg", "data": b}}]}],
            "generationConfig": gen}
    req = urllib.request.Request(
        "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s" % (MODEL, KEY),
        data=json.dumps(body).encode(), headers={"Content-Type": "application/json"})
    t = time.time()
    try:
        r = json.load(urllib.request.urlopen(req))
    except urllib.error.HTTPError as e:
        print("  fallo:", json.loads(e.read().decode())["error"]["message"][:90])
        return None, None
    dt = time.time() - t
    return dt, r.get("usageMetadata", {}).get("candidatesTokenCount")


b = base64.b64encode(open(sys.argv[1], "rb").read()).decode()
reps = int(sys.argv[2]) if len(sys.argv) > 2 else 4
res = {True: [], False: []}
tok = {True: [], False: []}
for i in range(reps):
    for use in (i % 2 == 0, i % 2 != 0):        # alterna el orden dentro de cada par
        dt, tk = call(b, use)
        if dt:
            res[use].append(dt); tok[use].append(tk)
            print("  %-9s %5.1fs  tok_out=%s" % ("schema" if use else "sin", dt, tk))
for use in (False, True):
    v = res[use]
    if v:
        print("%-7s n=%d  mediana=%.1fs  min=%.1f  max=%.1f  tok_out medio=%.0f" % (
            "schema" if use else "sin", len(v), statistics.median(v), min(v), max(v),
            statistics.mean(tok[use])))
