"""Prueba parametros de generateContent (aceptacion, latencia y lectura) sobre una foto real."""
import base64
import json
import sys
import time
import urllib.error
import urllib.request

ROOT = r"C:\Users\Ivan Sola\Desktop\MiNutricionAndroid"
KEY = [l.split("=", 1)[1].strip() for l in open(ROOT + r"\local.properties", encoding="utf-8")
       if l.startswith("gemini.api.key=")][0]
MODEL = "gemini-3.5-flash"
PROMPT = ("Lee esta foto de un producto y devuelve SOLO un JSON con: name, ean, "
          "kcal, protein, carbs, fat, y nutrients (objeto con vit_a, vit_c, vit_e, b3, b5 en "
          "sus unidades). Usa null en lo que no veas. No inventes nada.")

SCHEMA = {
    "type": "OBJECT",
    "properties": {
        "name": {"type": "STRING", "nullable": True},
        "ean": {"type": "STRING", "nullable": True},
        "kcal": {"type": "NUMBER", "nullable": True},
        "protein": {"type": "NUMBER", "nullable": True},
        "carbs": {"type": "NUMBER", "nullable": True},
        "fat": {"type": "NUMBER", "nullable": True},
        "nutrients": {"type": "OBJECT", "properties": {
            "vit_a": {"type": "NUMBER", "nullable": True},
            "vit_c": {"type": "NUMBER", "nullable": True},
            "vit_e": {"type": "NUMBER", "nullable": True},
            "b3": {"type": "NUMBER", "nullable": True},
            "b5": {"type": "NUMBER", "nullable": True},
        }},
    },
    "required": ["name", "ean"],
}

CONFIGS = {
    "base": {},
    "temp0": {"temperature": 0},
    "media_low": {"mediaResolution": "MEDIA_RESOLUTION_LOW"},
    "media_high": {"mediaResolution": "MEDIA_RESOLUTION_HIGH"},
    "schema": {"response_schema": SCHEMA},
    "temp0+schema+high": {"temperature": 0, "response_schema": SCHEMA,
                          "mediaResolution": "MEDIA_RESOLUTION_HIGH"},
}


def run(img_b64, name, extra):
    gen = {"response_mime_type": "application/json"}
    gen.update(extra)
    body = {"contents": [{"parts": [{"text": PROMPT},
                                    {"inline_data": {"mime_type": "image/jpeg", "data": img_b64}}]}],
            "generationConfig": gen}
    req = urllib.request.Request(
        "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s" % (MODEL, KEY),
        data=json.dumps(body).encode(), headers={"Content-Type": "application/json"})
    t = time.time()
    try:
        r = json.load(urllib.request.urlopen(req))
    except urllib.error.HTTPError as e:
        msg = json.loads(e.read().decode()).get("error", {}).get("message", "")
        print("%-18s RECHAZADO  %s" % (name, msg[:110]))
        return
    dt = time.time() - t
    txt = r["candidates"][0]["content"]["parts"][0].get("text", "")
    usage = r.get("usageMetadata", {})
    try:
        d = json.loads(txt)
        out = "ean=%s kcal=%s nutr=%s" % (d.get("ean"), d.get("kcal"),
                                          len([v for v in (d.get("nutrients") or {}).values() if v is not None]))
    except Exception:
        out = "JSON ilegible: " + txt[:60]
    print("%-18s %5.1fs  tok_in=%-6s tok_out=%-5s %s" % (
        name, dt, usage.get("promptTokenCount"), usage.get("candidatesTokenCount"), out))


if __name__ == "__main__":
    b = base64.b64encode(open(sys.argv[1], "rb").read()).decode()
    reps = int(sys.argv[2]) if len(sys.argv) > 2 else 1
    for _ in range(reps):
        for n, c in CONFIGS.items():
            run(b, n, c)
        print("-" * 70)
