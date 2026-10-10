# Blüte 🌸

Private Android-App für Bloom auf dem eigenen Server. Nur für den Eigenbedarf.

Die Daten liegen in Bloom auf dem Server (und sind dort mitgesichert). Die App liest und
schreibt sie über eine kleine Schnittstelle, die in Bloom eingebaut wird (`server/api.go`).
Bloom im Browser zeigt dieselben Daten. Die App merkt sich nur den zuletzt geladenen Stand,
damit Kalender und Vorhersagen auch ohne Heimnetz sichtbar bleiben.

- Kalender mit Periode, erwarteter Periode, fruchtbarem Fenster und Eisprung
- Eisprung selbst eintragen (Wärmegefühl, Ovulationstest, Mittelschmerz, Sonstiges)
- Zervixschleim und die Bloom-Symptome (auf Deutsch) mit Stärke 1–5, eigene Symptome
- Dashboard mit Phase, Vorhersagen, Hinweisen und Tipps
- Statistik: Zykluslängen, Verlauf, häufigste Symptome, Symptome nach Phase
- Hell/Dunkel in den Farben der pinken Bloom-Blume, optionale App-Sperre

## Vorhersage

Die Logik steht in `app/src/main/java/de/sephie/bluete/domain/CycleEngine.kt` und ist mit
Tests in `app/src/test/...` abgesichert. Bloom selbst rechnet immer „Zykluslänge − 14“.

- Zykluslänge: gewichteter Mittelwert der letzten 8 Zyklen, neuere zählen mehr, Ausreißer nur 20 %.
- Lutealphase (Eisprung bis Periode): aus deinen Eisprung-Einträgen gelernt, ohne Einträge 14 Tage.
- Eisprung im laufenden Zyklus eingetragen → nächste Periode = Eisprung + deine Lutealphase.
- Kein Eintrag, aber Schleim wässrig/spinnbar und danach wieder trockener → letzter fruchtbarer Tag gilt als Eisprung.
- Ein positiver Ovulationstest zählt als Eisprung am Folgetag.
- Fruchtbares Fenster: Eisprung −5 bis +1 Tag.

Vorhersagen sind Schätzungen und kein Verhütungsmittel.

## Server: Schnittstelle in Bloom einbauen

Siehe [`server/README.md`](server/README.md).

## App

Jeder Push auf `main` baut die APK und veröffentlicht sie unter **Releases**.
Auf dem Tablet herunterladen und antippen. Updates lassen sich drüberinstallieren, weil sie
mit demselben Schlüssel (`app/bluete.keystore`) signiert sind. Das Repository daher privat halten.
