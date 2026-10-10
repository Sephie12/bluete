package main

// ─── Blüte-API ───────────────────────────────────────────────────────────────
//
// JSON-Schnittstelle für die Android-App „Blüte“. Sie arbeitet direkt auf der
// Bloom-Datenbank, damit Web-Oberfläche und App dieselben Daten sehen.
//
// Anmeldung: POST /api/v1/login mit E-Mail und Passwort des Bloom-Kontos.
// Danach jede Anfrage mit dem Header  Authorization: Bearer <token>.
//
// Zusätzlich zu den Bloom-Tabellen legt die API zwei eigene an:
//   bluete_ovulations      – selbst bemerkte Eisprünge mit Anzeichen
//   bluete_custom_symptoms – eigene Symptome, die in der App als Auswahl erscheinen

import (
	"database/sql"
	"encoding/json"
	"errors"
	"log"
	"net/http"
	"strconv"
	"strings"
	"time"
)

const apiVersion = 1

// Ein App-Login bleibt ein Jahr gültig.
const apiSessionDuration = 365 * 24 * time.Hour

var apiMucusTypes = map[string]bool{"": true, "dry": true, "sticky": true, "creamy": true, "watery": true, "egg-white": true}

var apiOvulationSigns = map[string]bool{"waerme": true, "lh_test": true, "mittelschmerz": true, "sonstiges": true}

var apiCategories = map[string]bool{"physical": true, "emotional": true, "flow": true, "other": true}

func registerAPI(mux *http.ServeMux) {
	if err := createAPISchema(); err != nil {
		log.Printf("🌸 Blüte-API: Tabellen konnten nicht angelegt werden: %v", err)
	}
	mux.HandleFunc("/api/v1/ping", apiPing)
	mux.HandleFunc("/api/v1/login", apiLogin)
	mux.HandleFunc("/api/v1/logout", apiAuth(apiLogout))
	mux.HandleFunc("/api/v1/data", apiAuth(apiData))
	mux.HandleFunc("/api/v1/periods", apiAuth(apiPeriods))
	mux.HandleFunc("/api/v1/periods/", apiAuth(apiPeriodByID))
	mux.HandleFunc("/api/v1/days/", apiAuth(apiDay))
	mux.HandleFunc("/api/v1/custom-symptoms", apiAuth(apiCustomSymptoms))
	log.Println("🌸 Blüte-API aktiv unter /api/v1")
}

func createAPISchema() error {
	_, err := db.Exec(`
	CREATE TABLE IF NOT EXISTS bluete_ovulations (
		user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
		date TEXT NOT NULL,
		signs TEXT NOT NULL DEFAULT '',
		created_at TEXT DEFAULT (datetime('now')),
		PRIMARY KEY (user_id, date)
	);
	CREATE TABLE IF NOT EXISTS bluete_custom_symptoms (
		user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
		name TEXT NOT NULL,
		category TEXT NOT NULL DEFAULT 'other',
		PRIMARY KEY (user_id, name)
	);`)
	return err
}

// ─── Hilfsfunktionen ─────────────────────────────────────────────────────────

func apiJSON(w http.ResponseWriter, status int, v interface{}) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.Header().Set("Cache-Control", "no-store")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(v)
}

func apiError(w http.ResponseWriter, status int, msg string) {
	apiJSON(w, status, map[string]string{"error": msg})
}

func apiDecode(w http.ResponseWriter, r *http.Request, v interface{}) bool {
	r.Body = http.MaxBytesReader(w, r.Body, 1<<20)
	if err := json.NewDecoder(r.Body).Decode(v); err != nil {
		apiError(w, http.StatusBadRequest, "Ungültige Anfrage")
		return false
	}
	return true
}

// apiDate prüft ein Datum im Format JJJJ-MM-TT und gibt es normalisiert zurück.
func apiDate(s string) (string, bool) {
	t, err := time.Parse("2006-01-02", strings.TrimSpace(s))
	if err != nil {
		return "", false
	}
	return t.Format("2006-01-02"), true
}

// apiDay10 schneidet gespeicherte Datumswerte auf JJJJ-MM-TT zu.
func apiDay10(s string) string {
	if len(s) >= 10 {
		return s[:10]
	}
	return s
}

func apiBearer(r *http.Request) string {
	h := r.Header.Get("Authorization")
	if strings.HasPrefix(h, "Bearer ") {
		return strings.TrimSpace(strings.TrimPrefix(h, "Bearer "))
	}
	return ""
}

type apiHandler func(w http.ResponseWriter, r *http.Request, user *User)

func apiAuth(next apiHandler) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		token := apiBearer(r)
		if token == "" {
			apiError(w, http.StatusUnauthorized, "Nicht angemeldet")
			return
		}
		userID, err := getSessionUserID(token)
		if err != nil {
			apiError(w, http.StatusUnauthorized, "Anmeldung abgelaufen")
			return
		}
		user, err := getUserByID(userID)
		if err != nil {
			apiError(w, http.StatusUnauthorized, "Konto nicht gefunden")
			return
		}
		if user.Role != "owner" {
			apiError(w, http.StatusForbidden, "Partner-Konten werden von der App nicht unterstützt")
			return
		}
		next(w, r, user)
	}
}

// ─── Anmeldung ───────────────────────────────────────────────────────────────

func apiPing(w http.ResponseWriter, r *http.Request) {
	apiJSON(w, http.StatusOK, map[string]interface{}{"app": "bloom", "api_version": apiVersion})
}

func apiLogin(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		apiError(w, http.StatusMethodNotAllowed, "Nur POST")
		return
	}
	ip := getClientIP(r)
	if !checkLoginRateLimit(ip) {
		apiError(w, http.StatusTooManyRequests, "Zu viele Anmeldeversuche. Bitte in 15 Minuten erneut versuchen.")
		return
	}
	var req struct {
		Email    string `json:"email"`
		Password string `json:"password"`
	}
	if !apiDecode(w, r, &req) {
		return
	}
	email := strings.TrimSpace(strings.ToLower(req.Email))
	user, err := getUserByEmail(email)
	if err != nil || req.Password == "" || !checkPassword(user.PasswordHash, req.Password) {
		recordFailedLogin(ip)
		apiError(w, http.StatusUnauthorized, "E-Mail oder Passwort falsch")
		return
	}
	clearFailedLogins(ip)
	if user.Role != "owner" {
		apiError(w, http.StatusForbidden, "Partner-Konten werden von der App nicht unterstützt")
		return
	}
	token := generateToken()
	if err := insertSession(token, user.ID, time.Now().Add(apiSessionDuration)); err != nil {
		apiError(w, http.StatusInternalServerError, "Anmeldung fehlgeschlagen")
		return
	}
	apiJSON(w, http.StatusOK, map[string]interface{}{"token": token, "name": user.Name})
}

func apiLogout(w http.ResponseWriter, r *http.Request, user *User) {
	deleteSession(apiBearer(r))
	apiJSON(w, http.StatusOK, map[string]bool{"ok": true})
}

// ─── Daten lesen ─────────────────────────────────────────────────────────────

type apiPeriod struct {
	ID    int64   `json:"id"`
	Start string  `json:"start"`
	End   *string `json:"end"`
}

type apiSymptom struct {
	ID       int64  `json:"id"`
	Date     string `json:"date"`
	Category string `json:"category"`
	Name     string `json:"name"`
	Severity int    `json:"severity"`
	Notes    string `json:"notes"`
}

type apiMucus struct {
	Date string `json:"date"`
	Type string `json:"type"`
}

type apiOvulation struct {
	Date  string   `json:"date"`
	Signs []string `json:"signs"`
}

type apiCustom struct {
	Name     string `json:"name"`
	Category string `json:"category"`
}

func apiData(w http.ResponseWriter, r *http.Request, user *User) {
	if r.Method != http.MethodGet {
		apiError(w, http.StatusMethodNotAllowed, "Nur GET")
		return
	}
	resp := struct {
		APIVersion int    `json:"api_version"`
		ServerTime string `json:"server_time"`
		User       struct {
			Name         string `json:"name"`
			CycleLength  int    `json:"cycle_length"`
			PeriodLength int    `json:"period_length"`
		} `json:"user"`
		Periods        []apiPeriod    `json:"periods"`
		Symptoms       []apiSymptom   `json:"symptoms"`
		Mucus          []apiMucus     `json:"mucus"`
		Ovulations     []apiOvulation `json:"ovulations"`
		CustomSymptoms []apiCustom    `json:"custom_symptoms"`
	}{
		APIVersion:     apiVersion,
		ServerTime:     time.Now().Format(time.RFC3339),
		Periods:        []apiPeriod{},
		Symptoms:       []apiSymptom{},
		Mucus:          []apiMucus{},
		Ovulations:     []apiOvulation{},
		CustomSymptoms: []apiCustom{},
	}
	resp.User.Name = user.Name
	resp.User.CycleLength = user.CycleLength
	resp.User.PeriodLength = user.PeriodLength

	fail := func(err error) {
		log.Printf("Blüte-API /data: %v", err)
		apiError(w, http.StatusInternalServerError, "Daten konnten nicht gelesen werden")
	}

	rows, err := db.Query("SELECT id, start_date, end_date FROM periods WHERE user_id = ? ORDER BY start_date", user.ID)
	if err != nil {
		fail(err)
		return
	}
	for rows.Next() {
		var p apiPeriod
		var start string
		var end sql.NullString
		if err := rows.Scan(&p.ID, &start, &end); err != nil {
			rows.Close()
			fail(err)
			return
		}
		p.Start = apiDay10(start)
		if end.Valid && end.String != "" {
			e := apiDay10(end.String)
			p.End = &e
		}
		resp.Periods = append(resp.Periods, p)
	}
	rows.Close()

	rows, err = db.Query("SELECT id, date, category, symptom, severity, COALESCE(notes, '') FROM symptoms WHERE user_id = ? ORDER BY date, id", user.ID)
	if err != nil {
		fail(err)
		return
	}
	for rows.Next() {
		var s apiSymptom
		if err := rows.Scan(&s.ID, &s.Date, &s.Category, &s.Name, &s.Severity, &s.Notes); err != nil {
			rows.Close()
			fail(err)
			return
		}
		s.Date = apiDay10(s.Date)
		resp.Symptoms = append(resp.Symptoms, s)
	}
	rows.Close()

	rows, err = db.Query("SELECT date, cervical_mucus FROM daily_readings WHERE user_id = ? AND COALESCE(cervical_mucus, '') != '' ORDER BY date", user.ID)
	if err != nil {
		fail(err)
		return
	}
	for rows.Next() {
		var m apiMucus
		if err := rows.Scan(&m.Date, &m.Type); err != nil {
			rows.Close()
			fail(err)
			return
		}
		m.Date = apiDay10(m.Date)
		resp.Mucus = append(resp.Mucus, m)
	}
	rows.Close()

	rows, err = db.Query("SELECT date, signs FROM bluete_ovulations WHERE user_id = ? ORDER BY date", user.ID)
	if err != nil {
		fail(err)
		return
	}
	for rows.Next() {
		var o apiOvulation
		var signs string
		if err := rows.Scan(&o.Date, &signs); err != nil {
			rows.Close()
			fail(err)
			return
		}
		o.Signs = []string{}
		for _, s := range strings.Split(signs, ",") {
			if s = strings.TrimSpace(s); s != "" {
				o.Signs = append(o.Signs, s)
			}
		}
		resp.Ovulations = append(resp.Ovulations, o)
	}
	rows.Close()

	rows, err = db.Query("SELECT name, category FROM bluete_custom_symptoms WHERE user_id = ? ORDER BY name", user.ID)
	if err != nil {
		fail(err)
		return
	}
	for rows.Next() {
		var c apiCustom
		if err := rows.Scan(&c.Name, &c.Category); err != nil {
			rows.Close()
			fail(err)
			return
		}
		resp.CustomSymptoms = append(resp.CustomSymptoms, c)
	}
	rows.Close()

	apiJSON(w, http.StatusOK, resp)
}

// ─── Perioden ────────────────────────────────────────────────────────────────

type apiPeriodReq struct {
	Start string  `json:"start"`
	End   *string `json:"end"`
}

func (req apiPeriodReq) validate() (string, *string, error) {
	start, ok := apiDate(req.Start)
	if !ok {
		return "", nil, errors.New("Ungültiger Beginn")
	}
	if req.End == nil || strings.TrimSpace(*req.End) == "" {
		return start, nil, nil
	}
	end, ok := apiDate(*req.End)
	if !ok {
		return "", nil, errors.New("Ungültiges Ende")
	}
	if end < start {
		return "", nil, errors.New("Das Ende liegt vor dem Beginn")
	}
	return start, &end, nil
}

// POST /api/v1/periods – neue Periode
func apiPeriods(w http.ResponseWriter, r *http.Request, user *User) {
	if r.Method != http.MethodPost {
		apiError(w, http.StatusMethodNotAllowed, "Nur POST")
		return
	}
	var req apiPeriodReq
	if !apiDecode(w, r, &req) {
		return
	}
	start, end, err := req.validate()
	if err != nil {
		apiError(w, http.StatusBadRequest, err.Error())
		return
	}
	var exists int
	db.QueryRow("SELECT COUNT(*) FROM periods WHERE user_id = ? AND substr(start_date, 1, 10) = ?", user.ID, start).Scan(&exists)
	if exists > 0 {
		apiError(w, http.StatusConflict, "An diesem Tag beginnt bereits eine Periode")
		return
	}
	res, err := db.Exec("INSERT INTO periods (user_id, start_date, end_date) VALUES (?, ?, ?)", user.ID, start, end)
	if err != nil {
		apiError(w, http.StatusInternalServerError, "Periode konnte nicht gespeichert werden")
		return
	}
	id, _ := res.LastInsertId()
	apiJSON(w, http.StatusOK, map[string]int64{"id": id})
}

// PUT oder DELETE /api/v1/periods/{id}
func apiPeriodByID(w http.ResponseWriter, r *http.Request, user *User) {
	id, err := strconv.ParseInt(strings.TrimPrefix(r.URL.Path, "/api/v1/periods/"), 10, 64)
	if err != nil {
		apiError(w, http.StatusNotFound, "Periode nicht gefunden")
		return
	}
	if _, err := getPeriodByID(id, user.ID); err != nil {
		apiError(w, http.StatusNotFound, "Periode nicht gefunden")
		return
	}
	switch r.Method {
	case http.MethodPut:
		var req apiPeriodReq
		if !apiDecode(w, r, &req) {
			return
		}
		start, end, err := req.validate()
		if err != nil {
			apiError(w, http.StatusBadRequest, err.Error())
			return
		}
		if _, err := db.Exec("UPDATE periods SET start_date = ?, end_date = ? WHERE id = ? AND user_id = ?", start, end, id, user.ID); err != nil {
			apiError(w, http.StatusInternalServerError, "Periode konnte nicht geändert werden")
			return
		}
	case http.MethodDelete:
		if err := deletePeriod(id, user.ID); err != nil {
			apiError(w, http.StatusInternalServerError, "Periode konnte nicht gelöscht werden")
			return
		}
	default:
		apiError(w, http.StatusMethodNotAllowed, "Nur PUT oder DELETE")
		return
	}
	apiJSON(w, http.StatusOK, map[string]bool{"ok": true})
}

// ─── Tageseintrag ────────────────────────────────────────────────────────────

type apiDayReq struct {
	Symptoms []struct {
		Category string `json:"category"`
		Name     string `json:"name"`
		Severity int    `json:"severity"`
	} `json:"symptoms"`
	Mucus          string   `json:"mucus"`
	OvulationSigns []string `json:"ovulation_signs"`
}

// PUT /api/v1/days/{JJJJ-MM-TT} – ersetzt Symptome, Zervixschleim und Eisprung dieses Tages
func apiDay(w http.ResponseWriter, r *http.Request, user *User) {
	if r.Method != http.MethodPut {
		apiError(w, http.StatusMethodNotAllowed, "Nur PUT")
		return
	}
	date, ok := apiDate(strings.TrimPrefix(r.URL.Path, "/api/v1/days/"))
	if !ok {
		apiError(w, http.StatusBadRequest, "Ungültiges Datum")
		return
	}
	var req apiDayReq
	if !apiDecode(w, r, &req) {
		return
	}
	if !apiMucusTypes[req.Mucus] {
		apiError(w, http.StatusBadRequest, "Unbekannte Schleim-Art")
		return
	}
	var signs []string
	for _, s := range req.OvulationSigns {
		if !apiOvulationSigns[s] {
			apiError(w, http.StatusBadRequest, "Unbekanntes Eisprung-Anzeichen")
			return
		}
		signs = append(signs, s)
	}
	seen := map[string]bool{}
	for i := range req.Symptoms {
		s := &req.Symptoms[i]
		s.Name = strings.TrimSpace(s.Name)
		if s.Name == "" || len(s.Name) > 100 || seen[s.Name] {
			apiError(w, http.StatusBadRequest, "Ungültiges oder doppeltes Symptom")
			return
		}
		seen[s.Name] = true
		if !apiCategories[s.Category] {
			s.Category = "other"
		}
		if s.Severity < 1 || s.Severity > 5 {
			s.Severity = 3
		}
	}

	tx, err := db.Begin()
	if err != nil {
		apiError(w, http.StatusInternalServerError, "Speichern fehlgeschlagen")
		return
	}
	defer tx.Rollback()

	// Symptome ersetzen, Notizen aus Bloom bleiben erhalten
	notes := map[string]string{}
	rows, err := tx.Query("SELECT symptom, COALESCE(notes, '') FROM symptoms WHERE user_id = ? AND substr(date, 1, 10) = ?", user.ID, date)
	if err == nil {
		for rows.Next() {
			var n, t string
			if rows.Scan(&n, &t) == nil && t != "" {
				notes[n] = t
			}
		}
		rows.Close()
	}
	if _, err := tx.Exec("DELETE FROM symptoms WHERE user_id = ? AND substr(date, 1, 10) = ?", user.ID, date); err != nil {
		apiError(w, http.StatusInternalServerError, "Speichern fehlgeschlagen")
		return
	}
	for _, s := range req.Symptoms {
		if _, err := tx.Exec(
			"INSERT INTO symptoms (user_id, date, category, symptom, severity, notes) VALUES (?, ?, ?, ?, ?, ?)",
			user.ID, date, s.Category, s.Name, s.Severity, notes[s.Name],
		); err != nil {
			apiError(w, http.StatusInternalServerError, "Speichern fehlgeschlagen")
			return
		}
	}

	// Zervixschleim: nur dieses Feld ändern, andere Bloom-Werte (Temperatur, Notizen …) bleiben
	var readingID int64
	err = tx.QueryRow("SELECT id FROM daily_readings WHERE user_id = ? AND date = ?", user.ID, date).Scan(&readingID)
	switch {
	case err == nil:
		if _, err := tx.Exec("UPDATE daily_readings SET cervical_mucus = ? WHERE id = ?", req.Mucus, readingID); err != nil {
			apiError(w, http.StatusInternalServerError, "Speichern fehlgeschlagen")
			return
		}
		if req.Mucus == "" {
			// Leere Einträge entfernen
			tx.Exec(`DELETE FROM daily_readings WHERE id = ?
				AND COALESCE(basal_temp, 0) = 0 AND COALESCE(notes, '') = ''
				AND COALESCE(sleep_quality, 0) = 0 AND COALESCE(stress_level, 0) = 0 AND COALESCE(energy_level, 0) = 0`, readingID)
		}
	case errors.Is(err, sql.ErrNoRows):
		if req.Mucus != "" {
			if _, err := tx.Exec("INSERT INTO daily_readings (user_id, date, cervical_mucus) VALUES (?, ?, ?)", user.ID, date, req.Mucus); err != nil {
				apiError(w, http.StatusInternalServerError, "Speichern fehlgeschlagen")
				return
			}
		}
	default:
		apiError(w, http.StatusInternalServerError, "Speichern fehlgeschlagen")
		return
	}

	// Eisprung
	if _, err := tx.Exec("DELETE FROM bluete_ovulations WHERE user_id = ? AND date = ?", user.ID, date); err != nil {
		apiError(w, http.StatusInternalServerError, "Speichern fehlgeschlagen")
		return
	}
	if len(signs) > 0 {
		if _, err := tx.Exec("INSERT INTO bluete_ovulations (user_id, date, signs) VALUES (?, ?, ?)", user.ID, date, strings.Join(signs, ",")); err != nil {
			apiError(w, http.StatusInternalServerError, "Speichern fehlgeschlagen")
			return
		}
	}

	if err := tx.Commit(); err != nil {
		apiError(w, http.StatusInternalServerError, "Speichern fehlgeschlagen")
		return
	}
	apiJSON(w, http.StatusOK, map[string]bool{"ok": true})
}

// ─── Eigene Symptome ─────────────────────────────────────────────────────────

// POST {name, category} legt an, DELETE ?name=… entfernt
func apiCustomSymptoms(w http.ResponseWriter, r *http.Request, user *User) {
	switch r.Method {
	case http.MethodPost:
		var req apiCustom
		if !apiDecode(w, r, &req) {
			return
		}
		name := strings.TrimSpace(req.Name)
		if name == "" || len(name) > 100 {
			apiError(w, http.StatusBadRequest, "Ungültiger Name")
			return
		}
		if !apiCategories[req.Category] {
			req.Category = "other"
		}
		if _, err := db.Exec(
			"INSERT INTO bluete_custom_symptoms (user_id, name, category) VALUES (?, ?, ?) ON CONFLICT(user_id, name) DO UPDATE SET category = excluded.category",
			user.ID, name, req.Category,
		); err != nil {
			apiError(w, http.StatusInternalServerError, "Speichern fehlgeschlagen")
			return
		}
	case http.MethodDelete:
		name := strings.TrimSpace(r.URL.Query().Get("name"))
		if _, err := db.Exec("DELETE FROM bluete_custom_symptoms WHERE user_id = ? AND name = ?", user.ID, name); err != nil {
			apiError(w, http.StatusInternalServerError, "Löschen fehlgeschlagen")
			return
		}
	default:
		apiError(w, http.StatusMethodNotAllowed, "Nur POST oder DELETE")
		return
	}
	apiJSON(w, http.StatusOK, map[string]bool{"ok": true})
}
