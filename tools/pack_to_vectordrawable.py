"""Convierte el pack de iconos SVG (assets/icons/food_line) en VectorDrawables de Android.

Por qué VectorDrawable y no cargar los SVG en tiempo de ejecución: es el formato vectorial nativo,
se pinta nítido a cualquier tamaño, lo cachea el sistema y no hace falta ninguna librería extra.

Qué hace con cada SVG:
  - aplana las transformaciones (<g transform=...>) sobre las coordenadas, así el XML final no
    necesita grupos;
  - convierte <circle>, <rect> y <ellipse> en trazados (VectorDrawable solo entiende <path>);
  - reescribe cada trazado con comandos absolutos y separadores explícitos. Los SVG de Lucide usan
    la sintaxis compacta de arcos ("a1 1 0 003.2 1"), que el parser de Android no siempre lee bien;
    al normalizarla se quita ese riesgo;
  - conserva el color de cada trazo (blanco o dorado) y fija el grosor en 1.35, que es el que tiene
    todo el pack una vez aplicada la escala.

Uso:  python pack_to_vectordrawable.py   (lee el pack y escribe en app/src/main/res/drawable)
Requiere: pip install svgelements
"""
import os
import re

from svgelements import SVG, Path, Shape

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app", "src", "main", "assets", "icons", "food_line")
DST = os.path.join(ROOT, "app", "src", "main", "res", "drawable")
PREFIX = "food_"
STROKE = 1.35


def fmt(v):
    """Número compacto pero con separación explícita (sin '0.' ni '-' pegados al anterior)."""
    s = f"{v:.3f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def normalized_d(path):
    """Trazado con comandos absolutos M/L/C/Q/A/Z y todos los números separados por espacios."""
    out = []
    for seg in path:
        name = type(seg).__name__
        if name == "Move":
            out.append(f"M {fmt(seg.end.x)} {fmt(seg.end.y)}")
        elif name == "Line":
            out.append(f"L {fmt(seg.end.x)} {fmt(seg.end.y)}")
        elif name == "Close":
            out.append("Z")
        elif name == "CubicBezier":
            out.append(f"C {fmt(seg.control1.x)} {fmt(seg.control1.y)} "
                       f"{fmt(seg.control2.x)} {fmt(seg.control2.y)} {fmt(seg.end.x)} {fmt(seg.end.y)}")
        elif name == "QuadraticBezier":
            out.append(f"Q {fmt(seg.control.x)} {fmt(seg.control.y)} {fmt(seg.end.x)} {fmt(seg.end.y)}")
        elif name == "Arc":
            # los arcos se pasan a cúbicas: el resultado es exacto a efectos visuales y así no
            # dependemos de cómo interprete Android los indicadores de arco grande/sentido
            for c in seg.as_cubic_curves():
                out.append(f"C {fmt(c.control1.x)} {fmt(c.control1.y)} "
                           f"{fmt(c.control2.x)} {fmt(c.control2.y)} {fmt(c.end.x)} {fmt(c.end.y)}")
        else:
            raise ValueError(f"segmento no soportado: {name}")
    return " ".join(out)


def hex_color(color):
    return "#%02X%02X%02X" % (color.red, color.green, color.blue)


def convert(svg_path):
    svg = SVG.parse(svg_path, reify=True)
    paths = []
    for el in svg.elements():
        if not isinstance(el, Shape) or el.stroke is None or el.stroke.value is None:
            continue
        p = Path(el)
        p.reify()
        d = normalized_d(p)
        if d:
            paths.append((d, hex_color(el.stroke)))
    body = "\n".join(
        f'    <path android:pathData="{d}"\n'
        f'        android:strokeColor="{c}" android:strokeWidth="{STROKE}"\n'
        f'        android:strokeLineCap="round" android:strokeLineJoin="round"\n'
        f'        android:fillColor="#00000000" />'
        for d, c in paths
    )
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        f"<!-- Generado por tools/pack_to_vectordrawable.py a partir de {os.path.basename(svg_path)}."
        " No editar a mano. -->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="32dp" android:height="32dp"\n'
        '    android:viewportWidth="32" android:viewportHeight="32">\n'
        f"{body}\n</vector>\n"
    ), len(paths)


if __name__ == "__main__":
    os.makedirs(DST, exist_ok=True)
    # se borran los generados de una pasada anterior, por si se quitó algún icono del pack
    for f in os.listdir(DST):
        if f.startswith(PREFIX) and f.endswith(".xml"):
            os.remove(os.path.join(DST, f))
    total = 0
    for f in sorted(os.listdir(SRC)):
        if not f.endswith(".svg"):
            continue
        name = PREFIX + re.sub(r"[^a-z0-9_]", "_", f[:-4].lower())
        xml, n = convert(os.path.join(SRC, f))
        if n == 0:
            raise SystemExit(f"{f}: no se ha extraído ningún trazo")
        open(os.path.join(DST, name + ".xml"), "w", encoding="utf-8").write(xml)
        total += 1
    print(f"{total} VectorDrawables -> {DST}")
