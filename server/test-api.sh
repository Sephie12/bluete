#!/bin/bash
# Prüft die Blüte-API gegen einen laufenden Bloom-Server.
# Aufruf: bash test-api.sh http://localhost:8099
set -euo pipefail
B="${1:-http://localhost:8099}"
FEHLER=0
ok()   { echo "OK    $1"; }
fail() { echo "FEHLER $1"; echo "::error::$1"; FEHLER=$((FEHLER+1)); }
check() { if [ "$2" = "$3" ]; then ok "$1"; else fail "$1: erwartet '$3', erhalten '$2'"; fi; }

JAR=$(mktemp)
# Konto über das normale Bloom-Formular anlegen
curl -s -o /dev/null -c "$JAR" -X POST "$B/register" \
  --data-urlencode "storage_mode=cloud" --data-urlencode "name=Test" --data-urlencode "email=test@example.com" \
  --data-urlencode "password=geheim1234" --data-urlencode "confirm=geheim1234"

check "ping" "$(curl -s "$B/api/v1/ping" | jq -r .api_version)" "1"
check "ohne Token 401" "$(curl -s -o /dev/null -w '%{http_code}' "$B/api/v1/data")" "401"
check "falsches Token 401" "$(curl -s -o /dev/null -w '%{http_code}' -H 'Authorization: Bearer xyz' "$B/api/v1/data")" "401"
check "falsches Passwort 401" "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$B/api/v1/login" -d '{"email":"test@example.com","password":"falsch"}')" "401"

LOGIN=$(curl -s -X POST "$B/api/v1/login" -d '{"email":"Test@Example.com","password":"geheim1234"}')
T=$(echo "$LOGIN" | jq -r .token)
check "login name" "$(echo "$LOGIN" | jq -r .name)" "Test"
H="Authorization: Bearer $T"

D=$(curl -s -H "$H" "$B/api/v1/data")
check "leere Daten" "$(echo "$D" | jq '.periods|length')" "0"
check "Zykluslänge aus Bloom" "$(echo "$D" | jq '.user.cycle_length')" "28"

P1=$(curl -s -H "$H" -X POST "$B/api/v1/periods" -d '{"start":"2026-09-01","end":"2026-09-05"}' | jq .id)
P2=$(curl -s -H "$H" -X POST "$B/api/v1/periods" -d '{"start":"2026-09-29"}' | jq .id)
check "doppelte Periode 409" "$(curl -s -o /dev/null -w '%{http_code}' -H "$H" -X POST "$B/api/v1/periods" -d '{"start":"2026-09-29"}')" "409"
check "Ende vor Beginn 400" "$(curl -s -o /dev/null -w '%{http_code}' -H "$H" -X POST "$B/api/v1/periods" -d '{"start":"2026-10-05","end":"2026-10-01"}')" "400"
check "Periode beenden" "$(curl -s -H "$H" -X PUT "$B/api/v1/periods/$P2" -d '{"start":"2026-09-29","end":"2026-10-03"}' | jq .ok)" "true"

curl -s -H "$H" -X PUT "$B/api/v1/days/2026-09-15" -d '{
  "symptoms":[{"category":"physical","name":"Krämpfe","severity":4},{"category":"emotional","name":"Reizbarkeit","severity":2}],
  "mucus":"egg-white","ovulation_signs":["waerme","mittelschmerz"]}' > /dev/null
curl -s -H "$H" -X POST "$B/api/v1/custom-symptoms" -d '{"name":"Heißes Gesicht","category":"other"}' > /dev/null

D=$(curl -s -H "$H" "$B/api/v1/data")
check "zwei Perioden" "$(echo "$D" | jq '.periods|length')" "2"
check "Ende gespeichert" "$(echo "$D" | jq -r ".periods[] | select(.id==$P2) | .end")" "2026-10-03"
check "zwei Symptome" "$(echo "$D" | jq '[.symptoms[]|select(.date=="2026-09-15")]|length')" "2"
check "Umlaut erhalten" "$(echo "$D" | jq -r '.symptoms[0].name')" "Krämpfe"
check "Schleim" "$(echo "$D" | jq -r '.mucus[0].type')" "egg-white"
check "Eisprung" "$(echo "$D" | jq -r '.ovulations[0].signs|join(",")')" "waerme,mittelschmerz"
check "eigenes Symptom" "$(echo "$D" | jq -r '.custom_symptoms[0].name')" "Heißes Gesicht"

# Bloom-Weboberfläche sieht dieselben Daten
curl -s -o /dev/null -c "$JAR" -b "$JAR" -X POST "$B/login" \
  --data-urlencode "email=test@example.com" --data-urlencode "password=geheim1234"
EXP=$(curl -s -b "$JAR" "$B/export?format=json")
check "Bloom-Export Perioden" "$(echo "$EXP" | jq '.periods|length')" "2"
check "Bloom-Export Symptom" "$(echo "$EXP" | jq -r '[.symptoms[].symptom]|sort|join(",")')" "Krämpfe,Reizbarkeit"
check "Bloom-Export Schleim" "$(echo "$EXP" | jq -r '.daily_readings[0].cervical_mucus')" "egg-white"
check "Bloom-Dashboard lädt" "$(curl -s -o /dev/null -w '%{http_code}' -b "$JAR" "$B/dashboard")" "200"

# Tag ändern: ein Symptom weg, Schleim weg, Eisprung weg
curl -s -H "$H" -X PUT "$B/api/v1/days/2026-09-15" -d '{"symptoms":[{"category":"physical","name":"Krämpfe","severity":2}],"mucus":"","ovulation_signs":[]}' > /dev/null
D=$(curl -s -H "$H" "$B/api/v1/data")
check "Symptom ersetzt" "$(echo "$D" | jq -c '[.symptoms[]|select(.date=="2026-09-15")|[.name,.severity]]')" '[["Krämpfe",2]]'
check "Schleim entfernt" "$(echo "$D" | jq '.mucus|length')" "0"
check "Eisprung entfernt" "$(echo "$D" | jq '.ovulations|length')" "0"
check "leerer Tageswert gelöscht" "$(curl -s -b "$JAR" "$B/export?format=json" | jq '.daily_readings|length')" "0"

check "ungültiger Schleim 400" "$(curl -s -o /dev/null -w '%{http_code}' -H "$H" -X PUT "$B/api/v1/days/2026-09-16" -d '{"symptoms":[],"mucus":"quatsch","ovulation_signs":[]}')" "400"
check "ungültiges Datum 400" "$(curl -s -o /dev/null -w '%{http_code}' -H "$H" -X PUT "$B/api/v1/days/2026-13-01" -d '{"symptoms":[],"mucus":"","ovulation_signs":[]}')" "400"

curl -s -H "$H" -X DELETE "$B/api/v1/custom-symptoms?name=Hei%C3%9Fes%20Gesicht" > /dev/null
check "eigenes Symptom gelöscht" "$(curl -s -H "$H" "$B/api/v1/data" | jq '.custom_symptoms|length')" "0"
check "Periode löschen" "$(curl -s -H "$H" -X DELETE "$B/api/v1/periods/$P1" | jq .ok)" "true"
check "fremde ID 404" "$(curl -s -o /dev/null -w '%{http_code}' -H "$H" -X DELETE "$B/api/v1/periods/99999")" "404"

check "abmelden" "$(curl -s -H "$H" -X POST "$B/api/v1/logout" | jq .ok)" "true"
check "Token danach ungültig" "$(curl -s -o /dev/null -w '%{http_code}' -H "$H" "$B/api/v1/data")" "401"

echo "----"
if [ "$FEHLER" -gt 0 ]; then echo "$FEHLER Fehler"; exit 1; fi
echo "Alle Prüfungen bestanden"
