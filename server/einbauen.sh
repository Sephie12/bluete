#!/bin/sh
# Baut die Blüte-API in einen Bloom-Quellordner ein.
# Aufruf: sh einbauen.sh /pfad/zu/bloom-tracker
set -e
ZIEL="${1:-.}"
HIER="$(cd "$(dirname "$0")" && pwd)"
[ -f "$ZIEL/main.go" ] || { echo "Kein Bloom-Ordner: $ZIEL"; exit 1; }
cp "$HIER/api.go" "$ZIEL/api.go"
if ! grep -q "registerAPI(mux)" "$ZIEL/main.go"; then
  sed -i 's/^\(\s*\)mux := http.NewServeMux()$/&\n\1registerAPI(mux)/' "$ZIEL/main.go"
fi
grep -q "registerAPI(mux)" "$ZIEL/main.go" || { echo "main.go konnte nicht angepasst werden"; exit 1; }
echo "Blüte-API eingebaut in $ZIEL"
