package de.sephie.bluete.data

import android.content.Context
import de.sephie.bluete.domain.Mucus
import de.sephie.bluete.domain.MucusObservation
import de.sephie.bluete.domain.OvulationMark
import de.sephie.bluete.domain.OvulationSign
import de.sephie.bluete.domain.PeriodRange
import de.sephie.bluete.domain.SymptomCategory
import de.sephie.bluete.domain.SymptomLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

data class CustomSymptom(val name: String, val category: SymptomCategory)

/** Alles, was die App vom Server bekommt. */
data class ServerData(
    val userName: String,
    val cycleLength: Int,
    val periodLength: Int,
    val periods: List<PeriodRange>,
    val symptoms: List<SymptomLog>,
    val mucus: List<MucusObservation>,
    val ovulations: List<OvulationMark>,
    val customSymptoms: List<CustomSymptom>
) {
    companion object {
        private fun date(s: String?): LocalDate? = s?.takeIf { it.length >= 10 }?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

        fun parse(o: JSONObject): ServerData {
            val user = o.optJSONObject("user") ?: JSONObject()
            fun arr(name: String) = o.optJSONArray(name) ?: org.json.JSONArray()

            val periods = arr("periods").let { a ->
                (0 until a.length()).mapNotNull { i ->
                    val p = a.getJSONObject(i)
                    val start = date(p.optString("start")) ?: return@mapNotNull null
                    val end = if (p.isNull("end")) null else date(p.optString("end"))
                    PeriodRange(p.getLong("id"), start, end)
                }
            }
            val symptoms = arr("symptoms").let { a ->
                (0 until a.length()).mapNotNull { i ->
                    val s = a.getJSONObject(i)
                    val d = date(s.optString("date")) ?: return@mapNotNull null
                    SymptomLog(d, s.optString("category", "other"), s.optString("name"), s.optInt("severity", 3).coerceIn(1, 5))
                }
            }
            val mucus = arr("mucus").let { a ->
                (0 until a.length()).mapNotNull { i ->
                    val m = a.getJSONObject(i)
                    val d = date(m.optString("date")) ?: return@mapNotNull null
                    Mucus.fromKey(m.optString("type"))?.let { MucusObservation(d, it) }
                }
            }
            val ovulations = arr("ovulations").let { a ->
                (0 until a.length()).mapNotNull { i ->
                    val ov = a.getJSONObject(i)
                    val d = date(ov.optString("date")) ?: return@mapNotNull null
                    val signs = ov.optJSONArray("signs")
                    val set = if (signs == null) emptySet() else
                        (0 until signs.length()).mapNotNull { OvulationSign.fromKey(signs.getString(it)) }.toSet()
                    OvulationMark(d, set.ifEmpty { setOf(OvulationSign.SONSTIGES) })
                }
            }
            val custom = arr("custom_symptoms").let { a ->
                (0 until a.length()).map { i ->
                    val c = a.getJSONObject(i)
                    CustomSymptom(c.optString("name"), SymptomCategory.fromKey(c.optString("category")))
                }
            }
            return ServerData(
                userName = user.optString("name"),
                cycleLength = user.optInt("cycle_length", 28).takeIf { it in 15..60 } ?: 28,
                periodLength = user.optInt("period_length", 5).takeIf { it in 1..14 } ?: 5,
                periods = periods,
                symptoms = symptoms,
                mucus = mucus,
                ovulations = ovulations,
                customSymptoms = custom
            )
        }
    }
}

data class SyncState(
    /** Zeitpunkt des letzten erfolgreichen Abgleichs (ms), null = noch nie. */
    val lastSync: Long? = null,
    val online: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null
)

/**
 * Datenquelle ist der Bloom-Server. Der zuletzt geladene Stand wird als
 * Zwischenspeicher abgelegt, damit Kalender und Vorhersagen auch ohne
 * Heimnetz sichtbar bleiben. Geschrieben wird nur direkt auf den Server.
 */
class Repository(context: Context, private val settings: SettingsStore) {
    private val cacheFile = File(context.noBackupFilesDir, "bloom-stand.json")
    private val mutex = Mutex()

    private val _data = MutableStateFlow<ServerData?>(null)
    val data: StateFlow<ServerData?> = _data

    private val _sync = MutableStateFlow(SyncState())
    val sync: StateFlow<SyncState> = _sync

    private fun api() = settings.state.value.let { BloomApi(it.serverUrl, it.token) }

    suspend fun loadCache() {
        withContext(Dispatchers.IO) {
            if (_data.value == null && cacheFile.exists()) {
                runCatching {
                    val o = JSONObject(cacheFile.readText(Charsets.UTF_8))
                    _data.value = ServerData.parse(o.getJSONObject("data"))
                    _sync.value = _sync.value.copy(lastSync = o.optLong("zeit").takeIf { it > 0 })
                }
            }
        }
    }

    /** Lädt alles neu vom Server. Gibt eine Fehlermeldung zurück oder null. */
    suspend fun refresh(): String? = withContext(Dispatchers.IO) {
        if (!settings.state.value.loggedIn) return@withContext null
        mutex.withLock {
            _sync.value = _sync.value.copy(refreshing = true)
            try {
                val json = api().fetchData()
                val parsed = ServerData.parse(json)
                val now = System.currentTimeMillis()
                runCatching {
                    cacheFile.writeText(JSONObject().put("zeit", now).put("data", json).toString(), Charsets.UTF_8)
                }
                _data.value = parsed
                _sync.value = SyncState(lastSync = now, online = true)
                null
            } catch (e: Exception) {
                val msg = handleError(e)
                _sync.value = _sync.value.copy(refreshing = false, online = false, error = msg)
                msg
            }
        }
    }

    /** Führt eine Änderung auf dem Server aus und lädt danach neu. */
    suspend fun write(action: (BloomApi) -> Unit): String? = withContext(Dispatchers.IO) {
        try {
            action(api())
        } catch (e: Exception) {
            val msg = handleError(e)
            if (e is OfflineException) _sync.value = _sync.value.copy(online = false, error = msg)
            return@withContext "Nicht gespeichert: $msg"
        }
        refresh()
    }

    private fun handleError(e: Exception): String = when (e) {
        is OfflineException -> "Server nicht erreichbar. Bist du im Heimnetz?"
        is ApiException -> {
            if (e.code == 401) {
                settings.update { it.copy(token = "") }
                "Anmeldung abgelaufen. Bitte neu anmelden."
            } else e.message ?: "Unbekannter Fehler"
        }
        else -> e.message ?: "Unbekannter Fehler"
    }

    /** Meldet sich an. Gibt eine Fehlermeldung zurück oder null. */
    suspend fun login(serverUrl: String, email: String, password: String): String? = withContext(Dispatchers.IO) {
        val url = normalizeUrl(serverUrl)
        try {
            val api = BloomApi(url, "")
            api.ping()
            val (token, _) = api.login(email.trim(), password)
            settings.update { it.copy(serverUrl = url, token = token, email = email.trim()) }
        } catch (e: OfflineException) {
            return@withContext "Server nicht erreichbar. Stimmt die Adresse und bist du im Heimnetz?"
        } catch (e: ApiException) {
            return@withContext e.message ?: "Anmeldung fehlgeschlagen"
        }
        // Zwischenspeicher eines anderen Kontos verwerfen
        cacheFile.delete()
        _data.value = null
        refresh()
    }

    suspend fun logout(): Unit = withContext(Dispatchers.IO) {
        runCatching { api().logout() }
        settings.update { it.copy(token = "") }
        cacheFile.delete()
        _data.value = null
        _sync.value = SyncState()
    }

    companion object {
        fun normalizeUrl(input: String): String {
            var s = input.trim().trimEnd('/')
            if (s.endsWith("/dashboard")) s = s.removeSuffix("/dashboard")
            if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
            return s
        }
    }
}
