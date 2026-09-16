#!/bin/bash
# Compara dos rondas de la auditoría: qué nombres cambian de icono (muestra aleatoria).
# Uso: bash casos.sh antes.tsv despues.tsv [cuántos]
cd "$(dirname "$0")"
python3 - "$1" "$2" "${3:-60}" <<'PY'
import sys, random
def load(p):
    d = {}
    for line in open(p, encoding="utf-8"):
        parts = line.rstrip("\n").split("\t")
        d[parts[-1]] = parts[0]
    return d
a, b, k = load(sys.argv[1]), load(sys.argv[2]), int(sys.argv[3])
diff = sorted(n for n in b if n in a and a[n] != b[n])
print(f"cambian {len(diff)} de {len(b)}")
for n in random.sample(diff, min(k, len(diff))):
    print(f"{a[n]:>14} -> {b[n]:<14} {n}")
PY
