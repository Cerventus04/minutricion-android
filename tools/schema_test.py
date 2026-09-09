"""Valida contra la API el esquema y la temperatura que usa Gemini.kt (mismos datos del fuente)."""
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
KEY = [l.split("=", 1)[1].strip() for l in open(ROOT + r"\local.properties", encoding="utf-8")
       if l.startswith("gemini.api.key=")][0]
MODEL = sys.argv[2] if len(sys.argv) > 2 else "gemini-3.5-flash"

CATS = re.findall(r'FoodCat\("([^"]+)"', cat_src)
KEYS = [k for k, _ in re.findall(r'"(\w+)" to "(\w+)"',
        src.split("NUTRIENT_KEYS = listOf(", 1)[1].split("\n    )", 1)[0])]
print("categorias=%d  nutrientes=%d" % (len(CATS), len(KEYS)))

num = lambda: {"type": "NUMBER", "nullable": True}
st = lambda: {"type": "STRING", "nullable": True}
SCHEMA = {"type": "OBJECT", "required": ["found"], "properties": {
    "found": {"type": "BOOLEAN"},
    "name": st(),
    "category": {"type": "STRING", "nullable": True, "enum": CATS},
    "ean": st(),
    "basis": {"type": "STRING", "nullable": True, "enum": ["100g", "serving"]},
    "serving_g": num(), "serving_name": st(),
    "kcal": num(), "protein": num(), "carbs": num(), "fat": num(),
    "nutrients": {"type": "OBJECT", "properties": {k: num() for k in KEYS}},
}}

PROMPT = ("Lee esta foto de un producto alimenticio o complemento y devuelve el JSON pedido. "
          "Rellena solo lo que veas; null en lo demas. No inventes valores.")

b = base64.b64encode(open(sys.argv[1], "rb").read()).decode()
body = {"contents": [{"parts": [{"text": PROMPT},
                                {"inline_data": {"mime_type": "image/jpeg", "data": b}}]}],
        "generationConfig": {"response_mime_type": "application/json",
                             "response_schema": SCHEMA, "temperature": 0}}
req = urllib.request.Request(
    "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s" % (MODEL, KEY),
    data=json.dumps(body).encode(), headers={"Content-Type": "application/json"})
t = time.time()
try:
    r = json.load(urllib.request.urlopen(req))
except urllib.error.HTTPError as e:
    print("RECHAZADO:", json.loads(e.read().decode())["error"]["message"][:300])
    raise SystemExit(1)
txt = r["candidates"][0]["content"]["parts"][0]["text"]
print("OK %.1fs  tok_out=%s" % (time.time() - t, r.get("usageMetadata", {}).get("candidatesTokenCount")))
d = json.loads(txt)
print("tipos:", {k: type(v).__name__ for k, v in d.items()})
print("categoria valida:", d.get("category") in CATS or d.get("category") is None, "->", d.get("category"))
print(json.dumps({k: v for k, v in d.items() if v not in (None, {})}, ensure_ascii=False)[:400])
