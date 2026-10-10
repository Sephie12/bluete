package de.sephie.bluete.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Fehler, die der Server meldet (z. B. 401 = Anmeldung abgelaufen). */
class ApiException(val code: Int, message: String) : Exception(message)

/** Server nicht erreichbar (kein Heimnetz, Server aus …). */
class OfflineException(cause: Throwable) : IOException("Server nicht erreichbar", cause)

/**
 * Spricht mit der Blüte-Schnittstelle in Bloom (/api/v1).
 * Alle Aufrufe blockieren und gehören auf Dispatchers.IO.
 */
class BloomApi(baseUrl: String, private val token: String) {
    private val base = baseUrl.trim().trimEnd('/')

    private fun request(method: String, path: String, body: JSONObject? = null, auth: Boolean = true): JSONObject {
        val conn = try {
            (URL(base + path).openConnection() as HttpURLConnection)
        } catch (e: Exception) {
            throw ApiException(0, "Ungültige Server-Adresse")
        }
        try {
            conn.requestMethod = method
            conn.connectTimeout = 5000
            conn.readTimeout = 20000
            conn.useCaches = false
            conn.setRequestProperty("Accept", "application/json")
            if (auth) conn.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(text).optString("error") }.getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: when (code) {
                        404 -> "Bloom kennt die Blüte-Schnittstelle noch nicht (Server aktualisieren)"
                        else -> "Serverfehler ($code)"
                    }
                throw ApiException(code, msg)
            }
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        } catch (e: ApiException) {
            throw e
        } catch (e: IOException) {
            throw OfflineException(e)
        } catch (e: org.json.JSONException) {
            throw ApiException(0, "Unerwartete Antwort – ist das wirklich dein Bloom-Server?")
        } finally {
            conn.disconnect()
        }
    }

    /** Prüft, ob unter der Adresse ein Bloom mit Blüte-Schnittstelle läuft. */
    fun ping() {
        val r = request("GET", "/api/v1/ping", auth = false)
        if (r.optInt("api_version", 0) < 1) throw ApiException(0, "Unerwartete Antwort vom Server")
    }

    /** Liefert Token und Namen. */
    fun login(email: String, password: String): Pair<String, String> {
        val r = request("POST", "/api/v1/login", JSONObject().put("email", email).put("password", password), auth = false)
        return r.getString("token") to r.optString("name")
    }

    fun logout() {
        request("POST", "/api/v1/logout")
    }

    fun fetchData(): JSONObject = request("GET", "/api/v1/data")

    fun createPeriod(start: String, end: String?) {
        request("POST", "/api/v1/periods", periodBody(start, end))
    }

    fun updatePeriod(id: Long, start: String, end: String?) {
        request("PUT", "/api/v1/periods/$id", periodBody(start, end))
    }

    fun deletePeriod(id: Long) {
        request("DELETE", "/api/v1/periods/$id")
    }

    fun saveDay(date: String, symptoms: List<Triple<String, String, Int>>, mucus: String, ovulationSigns: List<String>) {
        val body = JSONObject()
            .put("symptoms", JSONArray().apply {
                symptoms.forEach { (category, name, severity) ->
                    put(JSONObject().put("category", category).put("name", name).put("severity", severity))
                }
            })
            .put("mucus", mucus)
            .put("ovulation_signs", JSONArray(ovulationSigns))
        request("PUT", "/api/v1/days/$date", body)
    }

    fun addCustomSymptom(name: String, category: String) {
        request("POST", "/api/v1/custom-symptoms", JSONObject().put("name", name).put("category", category))
    }

    fun deleteCustomSymptom(name: String) {
        request("DELETE", "/api/v1/custom-symptoms?name=" + URLEncoder.encode(name, "UTF-8").replace("+", "%20"))
    }

    private fun periodBody(start: String, end: String?) =
        JSONObject().put("start", start).put("end", end ?: JSONObject.NULL)
}
