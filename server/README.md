# Blüte-Schnittstelle für Bloom

`api.go` ergänzt Bloom um eine JSON-Schnittstelle unter `/api/v1`, über die die App liest und
schreibt. Bloom selbst funktioniert unverändert weiter. Zusätzlich legt die Schnittstelle zwei
Tabellen in der Bloom-Datenbank an: `bluete_ovulations` (deine Eisprung-Einträge) und
`bluete_custom_symptoms` (eigene Symptome).

| Methode | Pfad | Zweck |
|---|---|---|
| GET | `/api/v1/ping` | Prüfen, ob die Schnittstelle da ist |
| POST | `/api/v1/login` | Anmelden mit Bloom-E-Mail und Passwort, liefert Token (1 Jahr gültig) |
| POST | `/api/v1/logout` | Token ungültig machen |
| GET | `/api/v1/data` | Alle Daten: Perioden, Symptome, Schleim, Eisprünge, eigene Symptome |
| POST | `/api/v1/periods` | Periode anlegen |
| PUT/DELETE | `/api/v1/periods/{id}` | Periode ändern oder löschen |
| PUT | `/api/v1/days/{JJJJ-MM-TT}` | Symptome, Schleim und Eisprung eines Tages setzen |
| POST/DELETE | `/api/v1/custom-symptoms` | Eigenes Symptom anlegen oder entfernen |

Alle Anfragen außer ping und login brauchen den Header `Authorization: Bearer <token>`.
`test-api.sh` prüft alles automatisch (läuft bei jeder Änderung auf GitHub).

## Einbauen

Der Fork `Sephie12/bloom-tracker` enthält Bloom mit eingebauter Schnittstelle.
Im Bloom-Ordner auf dem Server (LXC-Container):

```
git remote set-url origin https://github.com/Sephie12/bloom-tracker.git
git pull
docker compose up -d --build
```

Die Daten liegen im Docker-Volume und bleiben beim Neubauen erhalten.
Danach sollte `http://192.168.2.150:8080/api/v1/ping` im Browser `{"api_version":1,"app":"bloom"}` zeigen.

Alternativ ohne Fork: `sh einbauen.sh /pfad/zu/bloom-tracker` kopiert `api.go` hinein und
ergänzt in `main.go` die Zeile `registerAPI(mux)`.
