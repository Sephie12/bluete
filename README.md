# Blüte 🌸

Private Zyklus-App für Android, nach dem Vorbild von Bloom. Nur für den Eigenbedarf.

- Kalender mit Periode, erwarteter Periode, fruchtbarem Fenster und Eisprung
- Eisprung selbst eintragen (Wärmegefühl, Ovulationstest, Mittelschmerz, Sonstiges)
- Zervixschleim und die Bloom-Symptome (auf Deutsch) mit Stärke 1–5, eigene Symptome
- Dashboard mit Phase, Vorhersagen, Hinweisen und Tipps
- Statistik: Zykluslängen, Verlauf, häufigste Symptome, Symptome nach Phase
- Import des Bloom-JSON-Exports, eigene Sicherung als JSON
- Hell/Dunkel in den Farben der pinken Bloom-Blume
- Keine Internet-Berechtigung, keine Google-Backups, optionale App-Sperre

## Vorhersage

Die Logik steht in `app/src/main/java/de/sephie/bluete/domain/CycleEngine.kt` und ist mit
Tests in `app/src/test/...` abgesichert.

- Zykluslänge: gewichteter Mittelwert der letzten 8 Zyklen, neuere zählen mehr, Ausreißer nur 20 %.
- Lutealphase (Eisprung bis Periode): aus deinen Eisprung-Einträgen gelernt, ohne Einträge 14 Tage.
- Eisprung im laufenden Zyklus eingetragen → nächste Periode = Eisprung + deine Lutealphase.
- Kein Eintrag, aber Schleim wässrig/spinnbar und danach wieder trockener → letzter fruchtbarer Tag gilt als Eisprung.
- Ein positiver Ovulationstest zählt als Eisprung am Folgetag.
- Fruchtbares Fenster: Eisprung −5 bis +1 Tag.

Vorhersagen sind Schätzungen und kein Verhütungsmittel.

## APK bauen

### Weg A: GitHub Actions (kein Programm nötig)
1. Privates Repository auf GitHub anlegen und diesen Ordner hochladen.
2. Unter **Actions** läuft „APK bauen“ automatisch (oder per „Run workflow“).
3. Nach ca. 5 Minuten im Lauf unter **Artifacts** `bluete-apk` herunterladen und entpacken.

### Weg B: Docker auf dem Heimserver
Im Projektordner:

```
docker run --rm -v "$PWD":/project -w /project mingc/android-build-box \
  bash -c "chmod +x gradlew && ./gradlew assembleRelease"
```

Die APK liegt danach unter `app/build/outputs/apk/release/app-release.apk`.

### Installieren
APK aufs Tablet kopieren, antippen und „Aus dieser Quelle installieren“ erlauben.

Updates werden mit demselben Schlüssel (`app/bluete.keystore`) signiert und lassen sich
deshalb einfach drüberinstallieren, ohne dass Daten verloren gehen. Das Repository sollte
daher privat bleiben.
