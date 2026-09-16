"""Genera los iconos del pack food_line de la app a partir de iconos de trazo en caja de 24.

Fuentes (en tools/iconos_fuente/): Lucide (ISC), Tabler (MIT) y dibujos propios para lo que no
existe en ninguna librería (pavo, pasta, patata). Todas van por TRAZO, no por relleno — por eso no
se usa Phosphor, que dibuja con rellenos y no admite ni el grosor del pack ni el detalle dorado.

El pack usa una caja de 32x32, trazo #F2F0EC de 1.35 con puntas redondeadas y UN detalle en dorado
(#C9A227). Para cada icono:

  1. se escala para que su lado mayor mida TARGET (la mediana medida en el pack: ~19 de 32), a
     partir de su contorno REAL (getBBox medido en el navegador), no de la caja de 24, porque cada
     icono ocupa una parte distinta de su caja;
  2. se centra en (16, 16);
  3. se compensa el grosor dentro del grupo (1.35 / escala), para que tras escalar el trazo final
     sea exactamente 1.35 como el resto del pack;
  4. los elementos indicados se pintan en dorado.

Uso:  python lucide_to_pack.py <carpeta_salida>
      (para instalar: python lucide_to_pack.py ../app/src/main/assets/icons/food_line)
"""
import json
import os
import re
import sys

TARGET = 19.0
STROKE = 1.35
WHITE = "#F2F0EC"
GOLD = "#C9A227"
SOURCES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "iconos_fuente")

# Contornos reales de cada SVG fuente (x, y, ancho, alto), medidos con getBBox().
BBOX = {
    "lucide/bean": [1.995, 1.995, 20.005, 20.005], "lucide/beef": [2.002, 2.004, 18.995, 19.996],
    "lucide/croissant": [2.513, 2.513, 19.487, 19.487],
    "lucide/drumstick": [1.984, 1.998, 20.015, 19.975],
    "lucide/ham": [2.002, 1.737, 20.311, 20.261], "lucide/milk": [7, 2, 10, 20],
    "lucide/nut": [2, 2, 20, 20], "lucide/rabbit": [2, 2, 20, 19],
    "lucide/sandwich": [2, 4.001, 20, 15.999], "lucide/shrimp": [1.954, 2, 19.936, 20],
    "lucide/soup": [3, 3, 19, 18], "lucide/sprout": [4, 3, 16, 18], "lucide/vegan": [2, 2, 20, 20],
    "lucide/microwave": [2, 4, 20, 17], "lucide/broccoli": [2, 2, 20, 20],
    "lucide/can-soda": [5, 2, 14, 20],
    "tabler/cheese": [3, 4.008, 18, 16],
    "propios/pavo": [2.5, 2.5, 19, 19], "propios/pasta": [2, 3.895, 20, 16.605],
    "propios/patata": [5.9, 2.5, 12, 19],
    "propios/barrita": [3.086, 4.683, 17.827, 14.635], "propios/tortitas": [4, 5.2, 16, 12.8],
    "propios/proteina": [6, 3.5, 12, 17.5], "propios/infantil": [8, 4.001, 8, 16.999],
    # medidos con svgelements (contorno geométrico sin trazo, lo mismo que getBBox)
    "lucide/beer": [3, 2, 18, 20], "lucide/cookie": [2, 2, 20, 20], "lucide/pizza": [2, 2, 20.001, 20.001],
    "propios/zumo": [4.5, 3, 17.5, 19], "propios/te": [2, 2.5, 18, 19.5],
    "propios/aceitunas": [3, 2.4, 18.6, 18.6], "propios/embutido": [2.5, 4, 19, 18],
    "propios/postre": [3, 7, 18, 14],
}

# archivo del pack -> (fuente, índices de los elementos que van en dorado)
MAP = {
    "res":           ("lucide/beef",      [2]),             # el hueso redondo del filete
    "pollo":         ("lucide/drumstick", [1]),             # el hueso del muslo
    "cerdo":         ("lucide/ham",       [2]),             # el hueso del jamón
    "caza":          ("lucide/rabbit",    [3]),             # la oreja
    "mariscos":      ("lucide/shrimp",    [4]),             # las patas
    "panaderia":     ("lucide/croissant", [2, 3]),          # las puntas
    "frutos_secos":  ("lucide/nut",       [0]),             # el tallo
    "legumbres":     ("lucide/bean",      [1]),             # el hilio de la alubia
    "semillas":      ("lucide/sprout",    [1]),             # la hoja
    "carne_vegetal": ("lucide/vegan",     [0]),             # la hoja
    "fiambres":      ("lucide/sandwich",  [3]),             # el relleno
    "instantanea":   ("lucide/soup",      [3, 4, 5]),       # el vapor
    "leche":         ("lucide/milk",      [2]),             # el nivel de la leche
    # un táper se leía como caja de cartón; el microondas dice "plato preparado" sin dudas
    "preparados":    ("lucide/microwave", [1]),             # la ventana
    "verduras":      ("lucide/broccoli",  [0, 2]),          # detalles de los ramilletes
    "bebidas_lata":  ("lucide/can-soda",  [3, 4]),          # las bandas de la lata
    "quesos":        ("tabler/cheese",    [2, 3, 4]),       # los agujeros (Lucide no tiene queso)
    # sin equivalente en ninguna librería: dibujados a mano en el mismo estilo
    "pavo":          ("propios/pavo",     [0, 1]),          # la cola en abanico
    "pasta":         ("propios/pasta",    [6, 7]),          # la albahaca
    "tuberculos":    ("propios/patata",   [1, 2, 3, 4, 5]), # los ojos de la patata
    # añadidos tras auditar ~10.000 productos reales: no había icono para ellos
    "barrita":       ("propios/barrita",  [3, 4, 5, 6]),    # trocitos de fruto seco/cereal
    "tortitas":      ("propios/tortitas", [3, 4, 5, 6]),    # granos inflados
    "proteina":      ("propios/proteina", [2, 3]),          # la etiqueta del bote
    "infantil":      ("propios/infantil", [3, 4]),          # las marcas de medida del biberón
    # segunda auditoría: tipos frecuentes que compartían un icono genérico o equivocado
    "galletas":      ("lucide/cookie",    [1, 2, 3, 4, 5]), # las pepitas (antes: el caramelo)
    "cerveza":       ("lucide/beer",      [3]),             # la espuma (antes: la copa de vino)
    "pizza":         ("lucide/pizza",     [0, 1, 3]),       # los ingredientes (antes: el microondas)
    "zumo":          ("propios/zumo",     [3, 4]),          # la rodaja de naranja
    "te":            ("propios/te",       [3]),             # la etiqueta de la bolsita
    "aceitunas":     ("propios/aceitunas", [1]),            # el relleno de pimiento
    "embutidos":     ("propios/embutido", [3, 4, 5]),       # la grasa de la rodaja
    "postres":       ("propios/postre",   [1]),             # el caramelo del flan
}

ELEMENT = re.compile(r"<(path|circle|rect|line|ellipse|polyline|polygon)\b([^>]*?)/?>")


def build_svg(raw, bbox, gold):
    """SVG de 24 (Lucide o Tabler, ambos por trazo) -> SVG del pack. `bbox` = contorno real."""
    raw = re.sub(r"<!--.*?-->", "", raw, flags=re.S)   # Tabler lleva metadatos en un comentario
    body = re.sub(r"<svg[^>]*>", "", raw, flags=re.S).replace("</svg>", "")
    # Tabler incluye un rectángulo invisible de 24x24 (stroke="none") para alinear: fuera.
    elements = [e for e in ELEMENT.finditer(body) if 'stroke="none"' not in e.group(0)]
    x, y, w, h = bbox
    scale = TARGET / max(w, h)
    cx, cy = x + w / 2, y + h / 2
    out = []
    for i, m in enumerate(elements):
        tag, attrs = m.group(1), re.sub(r"\s+", " ", m.group(2)).strip()
        if i in gold:
            attrs += f' stroke="{GOLD}"'
        out.append(f"<{tag} {attrs}/>")
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32" width="32" height="32" '
        f'fill="none" stroke="{WHITE}" stroke-width="{STROKE}" stroke-linecap="round" '
        f'stroke-linejoin="round">'
        f'<g transform="translate(16 16) scale({scale:.4f}) translate({-cx:.3f} {-cy:.3f})" '
        f'stroke-width="{STROKE / scale:.4f}">'
        + "".join(out)
        + "</g></svg>\n"
    )


def convert(source, gold):
    """`source` = "biblioteca/nombre" dentro de iconos_fuente (p. ej. "lucide/beef")."""
    raw = open(os.path.join(SOURCES, source + ".svg"), encoding="utf-8").read()
    return build_svg(raw, BBOX[source], gold)


if __name__ == "__main__":
    dst = sys.argv[1]
    os.makedirs(dst, exist_ok=True)
    for pack_name, (source, gold) in MAP.items():
        open(os.path.join(dst, pack_name + ".svg"), "w", encoding="utf-8").write(convert(source, gold))
    print(f"{len(MAP)} iconos -> {dst}")
