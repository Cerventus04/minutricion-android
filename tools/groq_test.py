"""Prueba un modelo de Groq leyendo una etiqueta: EAN, categoria y UNIDADES de micronutrientes."""
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
CATS = re.findall(r'FoodCat\("([^"]+)"', cat_src)
KEYS = re.findall(r'"(\w+)" to "(\w+)"',
                  src.split("NUTRIENT_KEYS = listOf(", 1)[1].split("\n    )", 1)[0])
UNITS = ", ".join('"%s" en %s' % (k, u) for k, u in KEYS)

KEY = sys.argv[3]
MODEL = sys.argv[2]
PROMPT = ("Lee esta foto de un producto alimenticio o complemento y devuelve SOLO un JSON con: "
          "found (bool), name, category (una de: %s), ean, basis ('100g' o 'serving'), "
          "serving_g, serving_name, kcal, protein, carbs, fat y nutrients. "
          "En nutrients usa EXACTAMENTE estas unidades, convirtiendo si la etiqueta usa otra: %s. "
          "Incluye solo lo que veas; null en lo demas. No inventes valores."
          % (", ".join(CATS), UNITS))

b = base64.b64encode(open(sys.argv[1], "rb").read()).decode()
body = {"model": MODEL, "temperature": 0,
        "response_format": {"type": "json_object"},
        "messages": [{"role": "user", "content": [
            {"type": "text", "text": PROMPT},
            {"type": "image_url", "image_url": {"url": "data:image/jpeg;base64," + b}}]}]}
req = urllib.request.Request("https://api.groq.com/openai/v1/chat/completions",
                             data=json.dumps(body).encode(),
                             headers={"Content-Type": "application/json",
                                      "Authorization": "Bearer " + KEY})
t = time.time()
try:
    r = json.load(urllib.request.urlopen(req))
except urllib.error.HTTPError as e:
    print("RECHAZADO %s: %s" % (MODEL, e.read().decode()[:300]))
    raise SystemExit(1)
dt = time.time() - t
txt = r["choices"][0]["message"]["content"]
print("%s  %.1fs" % (MODEL, dt))
try:
    d = json.loads(txt)
except Exception:
    print("JSON ilegible:", txt[:300]); raise SystemExit(1)
n = d.get("nutrients") or {}
print("  ean=%s  categoria=%s (valida=%s)" % (d.get("ean"), d.get("category"), d.get("category") in CATS))
print("  UNIDADES  b12=%s (real 3 mcg)  biotin=%s (real 150 mcg)  iron=%s (real 14.5 mg)"
      % (n.get("b12"), n.get("biotin"), n.get("iron")))
print("  " + json.dumps(d, ensure_ascii=False)[:300])
