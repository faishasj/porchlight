#!/bin/sh
# Refreshes the bundled Material Design Icons font and name list from Pictogrammers' repository.
# Needs curl and python3. Run from the project root.
set -eu

BASE=https://raw.githubusercontent.com/Templarian/MaterialDesign-Webfont/master
OUT=app/src/main/assets/mdi
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

curl -fsSL -o "$OUT/materialdesignicons.ttf" "$BASE/fonts/materialdesignicons-webfont.ttf"
curl -fsSL -o "$TMP/variables.scss" "$BASE/scss/_variables.scss"
curl -fsSL -o "$TMP/LICENSE" "$BASE/LICENSE"
curl -fsSL -o "$TMP/apache" https://www.apache.org/licenses/LICENSE-2.0.txt
cat "$TMP/LICENSE" "$TMP/apache" > "$OUT/LICENSE"

# The SCSS map lists every icon as `"name": F0335,`. Write it as `name f0335` lines.
python3 - "$TMP/variables.scss" "$OUT/codepoints.txt" <<'PY'
import re, sys
pairs = re.findall(r'"([a-z0-9-]+)": ([0-9A-F]+)', open(sys.argv[1]).read())
open(sys.argv[2], "w").write("".join(f"{n} {c.lower()}\n" for n, c in sorted(pairs)))
PY
echo "Updated $OUT"
