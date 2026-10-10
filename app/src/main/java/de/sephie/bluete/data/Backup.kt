package de.sephie.bluete.data

import de.sephie.bluete.domain.Mucus
import de.sephie.bluete.domain.SymptomCatalog
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class ImportResult(
    val source: String,
    val periods: Int,
    val symptoms: Int,
    val ovulations: Int,
    val mucus: Int,
    val bloomCycleLength: Int?,
    val bloomPeriodLength: Int?
)

/** Export und Import als JSON. Liest auch den JSON-Export von Bloom. */
class Backup(private val dao: CycleDao) {

    suspend fun exportJson(): String {
        val root = JSONObject()
        root.put("app", "bluete")
        root.put("version", 1)
        root.put("exportiert", LocalDate.now().toString())
        root.put("periods", JSONArray().apply {
            dao.periodsOnce().forEach { p ->
                put(JSONObject().put("start", p.start).apply { if (p.ende != null) put("end", p.ende) })
            }
        })
        root.put("symptoms", JSONArray().apply {
            dao.symptomsOnce().forEach { s ->
                put(JSONObject().put("date", s.datum).put("category", s.kategorie).put("name", s.name).put("severity", s.staerke))
            }
        })
        root.put("ovulations", JSONArray().apply {
            dao.ovulationsOnce().forEach { o ->
                put(JSONObject().put("date", o.datum).put("signs", JSONArray(o.anzeichen.split(',').filter { it.isNotBlank() })))
            }
        })
        root.put("mucus", JSONArray().apply {
            dao.mucusOnce().forEach { m -> put(JSONObject().put("date", m.datum).put("type", m.art)) }
        })
        root.put("customSymptoms", JSONArray().apply {
            dao.customSymptomsOnce().forEach { c -> put(JSONObject().put("name", c.name).put("category", c.kategorie)) }
        })
        return root.toString(2)
    }

    private fun validDate(s: String?): String? =
        s?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it.take(10)).toString() }.getOrNull() }

    /** Fügt die Daten zu den vorhandenen hinzu. Bereits vorhandene Perioden werden übersprungen. */
    suspend fun importJson(text: String): ImportResult {
        val root = JSONObject(text)
        val isBloom = root.has("user") || root.has("daily_readings") ||
            (root.optJSONArray("periods")?.optJSONObject(0)?.has("start_date") == true)

        val existingStarts = dao.periodsOnce().map { it.start }.toMutableSet()
        var periods = 0
        root.optJSONArray("periods")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val start = validDate(o.optString(if (isBloom) "start_date" else "start")) ?: continue
                val end = validDate(o.optString(if (isBloom) "end_date" else "end", ""))
                if (start in existingStarts) continue
                dao.insertPeriod(PeriodEntity(start = start, ende = end))
                existingStarts += start
                periods++
            }
        }

        val known = SymptomCatalog.builtIn.map { it.name }.toSet()
        val symptoms = mutableListOf<SymptomEntity>()
        root.optJSONArray("symptoms")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val date = validDate(o.optString("date")) ?: continue
                val rawName = o.optString(if (isBloom) "symptom" else "name").trim()
                if (rawName.isEmpty()) continue
                val name = if (isBloom) SymptomCatalog.fromBloom(rawName) else rawName
                val category = o.optString("category", "other").ifBlank { "other" }
                val sev = o.optInt("severity", 3).let { if (it in 1..5) it else 3 }
                symptoms += SymptomEntity(datum = date, kategorie = category, name = name, staerke = sev)
                if (name !in known) dao.upsertCustomSymptom(CustomSymptomEntity(name, category))
            }
        }
        if (symptoms.isNotEmpty()) dao.insertSymptoms(symptoms)

        var ovulations = 0
        root.optJSONArray("ovulations")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val date = validDate(o.optString("date")) ?: continue
                val signs = o.optJSONArray("signs")?.let { s -> (0 until s.length()).map { s.getString(it) } } ?: emptyList()
                dao.upsertOvulation(OvulationEntity(date, signs.joinToString(",")))
                ovulations++
            }
        }

        var mucus = 0
        val mucusArr = if (isBloom) root.optJSONArray("daily_readings") else root.optJSONArray("mucus")
        mucusArr?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val date = validDate(o.optString("date")) ?: continue
                val key = o.optString(if (isBloom) "cervical_mucus" else "type", "")
                val type = Mucus.fromKey(key) ?: continue
                dao.upsertMucus(MucusEntity(date, type.key))
                mucus++
            }
        }

        root.optJSONArray("customSymptoms")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val name = o.optString("name").trim()
                if (name.isNotEmpty()) dao.upsertCustomSymptom(CustomSymptomEntity(name, o.optString("category", "other")))
            }
        }

        val user = root.optJSONObject("user")
        return ImportResult(
            source = if (isBloom) "Bloom" else "Blüte",
            periods = periods,
            symptoms = symptoms.size,
            ovulations = ovulations,
            mucus = mucus,
            bloomCycleLength = user?.optInt("cycle_length", 0)?.takeIf { it in 15..60 },
            bloomPeriodLength = user?.optInt("period_length", 0)?.takeIf { it in 1..14 }
        )
    }

    suspend fun deleteAll() {
        dao.clearPeriods()
        dao.clearSymptoms()
        dao.clearOvulations()
        dao.clearMucus()
        dao.clearCustomSymptoms()
    }
}
