package de.sephie.bluete.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.sephie.bluete.BlueteApp
import de.sephie.bluete.data.AppSettings
import de.sephie.bluete.data.Backup
import de.sephie.bluete.data.CustomSymptomEntity
import de.sephie.bluete.data.MucusEntity
import de.sephie.bluete.data.OvulationEntity
import de.sephie.bluete.data.PeriodEntity
import de.sephie.bluete.data.SymptomEntity
import de.sephie.bluete.domain.CurrentCycle
import de.sephie.bluete.domain.CycleEngine
import de.sephie.bluete.domain.CycleStats
import de.sephie.bluete.domain.Mucus
import de.sephie.bluete.domain.MucusObservation
import de.sephie.bluete.domain.OvulationMark
import de.sephie.bluete.domain.OvulationSign
import de.sephie.bluete.domain.PeriodRange
import de.sephie.bluete.domain.PredictedCycle
import de.sephie.bluete.domain.SmartAlert
import de.sephie.bluete.domain.SymptomCategory
import de.sephie.bluete.domain.SymptomLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class CustomSymptom(val name: String, val category: SymptomCategory)

data class UiState(
    val loaded: Boolean = false,
    val today: LocalDate = LocalDate.now(),
    val settings: AppSettings = AppSettings(),
    val periods: List<PeriodRange> = emptyList(),
    val marks: List<OvulationMark> = emptyList(),
    val mucus: List<MucusObservation> = emptyList(),
    val symptoms: List<SymptomLog> = emptyList(),
    val customSymptoms: List<CustomSymptom> = emptyList(),
    val engine: CycleEngine = CycleEngine(),
    val stats: CycleStats? = null,
    val current: CurrentCycle? = null,
    val upcoming: List<PredictedCycle> = emptyList(),
    val alerts: List<SmartAlert> = emptyList()
)

/** Eine mögliche Aktion zur Periode an einem bestimmten Tag. */
data class PeriodAction(val label: String, val run: () -> Unit)

private data class RawData(
    val periods: List<PeriodEntity>,
    val symptoms: List<SymptomEntity>,
    val ovulations: List<OvulationEntity>,
    val mucus: List<MucusEntity>,
    val custom: List<CustomSymptomEntity>
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val blueteApp = app as BlueteApp
    private val dao = blueteApp.database.dao()
    private val settingsStore = blueteApp.settings
    private val backup = Backup(dao)
    private val today = MutableStateFlow(LocalDate.now())

    val settings: StateFlow<AppSettings> = settingsStore.state

    private val raw = combine(dao.periods(), dao.symptoms(), dao.ovulations(), dao.mucus(), dao.customSymptoms()) { p, s, o, m, c ->
        RawData(p, s, o, m, c)
    }

    val state: StateFlow<UiState> = combine(raw, settingsStore.state, today) { r, settings, day ->
        withContext(Dispatchers.Default) { buildState(r, settings, day) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    private fun buildState(r: RawData, settings: AppSettings, day: LocalDate): UiState {
        val engine = CycleEngine(settings.defaultCycleLength, settings.defaultPeriodLength)
        val periods = r.periods.mapNotNull { p ->
            val start = runCatching { LocalDate.parse(p.start) }.getOrNull() ?: return@mapNotNull null
            PeriodRange(p.id, start, p.ende?.let { runCatching { LocalDate.parse(it) }.getOrNull() })
        }
        val marks = r.ovulations.mapNotNull { o ->
            val date = runCatching { LocalDate.parse(o.datum) }.getOrNull() ?: return@mapNotNull null
            OvulationMark(date, o.anzeichen.split(',').mapNotNull { OvulationSign.fromKey(it) }.toSet())
        }
        val mucus = r.mucus.mapNotNull { m ->
            val date = runCatching { LocalDate.parse(m.datum) }.getOrNull() ?: return@mapNotNull null
            Mucus.fromKey(m.art)?.let { MucusObservation(date, it) }
        }
        val symptoms = r.symptoms.mapNotNull { s ->
            val date = runCatching { LocalDate.parse(s.datum) }.getOrNull() ?: return@mapNotNull null
            SymptomLog(date, s.kategorie, s.name, s.staerke)
        }
        val stats = engine.stats(periods, marks, mucus, day)
        val current = engine.currentCycle(periods, marks, mucus, stats, day)
        return UiState(
            loaded = true,
            today = day,
            settings = settings,
            periods = periods,
            marks = marks,
            mucus = mucus,
            symptoms = symptoms,
            customSymptoms = r.custom.map { CustomSymptom(it.name, SymptomCategory.fromKey(it.kategorie)) },
            engine = engine,
            stats = stats,
            current = current,
            upcoming = current?.let { engine.upcoming(it, stats, day) } ?: emptyList(),
            alerts = engine.alerts(stats, current, symptoms)
        )
    }

    fun refreshToday() {
        today.value = LocalDate.now()
    }

    fun updateSettings(change: (AppSettings) -> AppSettings) = settingsStore.update(change)

    // ─── Perioden ──────────────────────────────────────────────────────────

    private fun launchIo(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) { block() }
    }

    fun startPeriod(date: LocalDate) = launchIo {
        dao.insertPeriod(PeriodEntity(start = date.toString(), ende = null))
    }

    fun setPeriod(p: PeriodRange, start: LocalDate, end: LocalDate?) = launchIo {
        dao.upsertPeriod(PeriodEntity(id = p.id, start = start.toString(), ende = end?.toString()))
    }

    fun deletePeriod(p: PeriodRange) = launchIo { dao.deletePeriod(p.id) }

    /** Welche Perioden-Aktionen passen zu diesem Tag? */
    fun periodActions(date: LocalDate, s: UiState): List<PeriodAction> {
        val engine = s.engine
        val periodLen = s.stats?.periodLength ?: s.settings.defaultPeriodLength
        val actions = mutableListOf<PeriodAction>()
        val containing = s.periods.firstOrNull { p ->
            !date.isBefore(p.start) && !date.isAfter(engine.effectiveEnd(p, s.today, periodLen))
        }
        if (containing != null) {
            if (containing.end == null) {
                actions += PeriodAction("Periode endete an diesem Tag") { setPeriod(containing, containing.start, date) }
            } else if (date != containing.end) {
                actions += PeriodAction("Ende auf diesen Tag legen") { setPeriod(containing, containing.start, date) }
            }
            if (date != containing.start) {
                actions += PeriodAction("Beginn auf diesen Tag legen") { setPeriod(containing, date, containing.end) }
            }
            actions += PeriodAction("Diese Periode löschen") { deletePeriod(containing) }
            return actions
        }
        val before = s.periods.filter { it.start.isBefore(date) }.maxByOrNull { it.start }
        if (before != null) {
            val end = engine.effectiveEnd(before, s.today, periodLen)
            if (CycleEngine.days(end, date) in 1..3) {
                actions += PeriodAction("Periode bis zu diesem Tag verlängern") { setPeriod(before, before.start, date) }
            }
        }
        val after = s.periods.filter { it.start.isAfter(date) }.minByOrNull { it.start }
        if (after != null && CycleEngine.days(date, after.start) in 1..3) {
            actions += PeriodAction("Beginn auf diesen Tag vorziehen") { setPeriod(after, date, after.end) }
        }
        if (!date.isAfter(s.today)) {
            actions += PeriodAction("Periode begann an diesem Tag") {
                // Läuft noch eine offene Periode, wird sie vorher sinnvoll beendet.
                val open = s.periods.firstOrNull { it.end == null && it.start.isBefore(date) }
                if (open != null) {
                    val normalEnd = open.start.plusDays(periodLen - 1L)
                    val end = if (normalEnd.isBefore(date)) normalEnd else date.minusDays(1)
                    setPeriod(open, open.start, end)
                }
                startPeriod(date)
            }
        }
        return actions
    }

    // ─── Tageseintrag ──────────────────────────────────────────────────────

    fun saveDay(
        date: LocalDate,
        ovulationSigns: Set<OvulationSign>,
        mucus: Mucus?,
        symptoms: Map<String, Pair<SymptomCategory, Int>>
    ) = launchIo {
        val key = date.toString()
        if (ovulationSigns.isEmpty()) dao.deleteOvulation(key)
        else dao.upsertOvulation(OvulationEntity(key, ovulationSigns.joinToString(",") { it.key }))
        if (mucus == null) dao.deleteMucus(key) else dao.upsertMucus(MucusEntity(key, mucus.key))
        dao.replaceSymptomsOn(key, symptoms.map { (name, v) ->
            SymptomEntity(datum = key, kategorie = v.first.key, name = name, staerke = v.second)
        })
    }

    fun addCustomSymptom(name: String, category: SymptomCategory) = launchIo {
        val n = name.trim()
        if (n.isNotEmpty()) dao.upsertCustomSymptom(CustomSymptomEntity(n, category.key))
    }

    fun deleteCustomSymptom(name: String) = launchIo { dao.deleteCustomSymptom(name) }

    // ─── Sicherung ─────────────────────────────────────────────────────────

    fun export(uri: Uri, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val msg = withContext(Dispatchers.IO) {
                runCatching {
                    val json = backup.exportJson()
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use {
                        it.write(json.toByteArray(Charsets.UTF_8))
                    }
                    "Sicherung gespeichert."
                }.getOrElse { "Sicherung fehlgeschlagen: ${it.message}" }
            }
            onDone(msg)
        }
    }

    fun import(uri: Uri, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val msg = withContext(Dispatchers.IO) {
                runCatching {
                    val text = getApplication<Application>().contentResolver.openInputStream(uri)!!
                        .use { it.readBytes().toString(Charsets.UTF_8) }
                    val r = backup.importJson(text)
                    if (r.bloomCycleLength != null || r.bloomPeriodLength != null) {
                        withContext(Dispatchers.Main) {
                            settingsStore.update {
                                it.copy(
                                    defaultCycleLength = r.bloomCycleLength ?: it.defaultCycleLength,
                                    defaultPeriodLength = r.bloomPeriodLength ?: it.defaultPeriodLength
                                )
                            }
                        }
                    }
                    "Import aus ${r.source}: ${r.periods} Perioden, ${r.symptoms} Symptome, " +
                        "${r.ovulations} Eisprünge, ${r.mucus} Schleim-Einträge."
                }.getOrElse { "Import fehlgeschlagen: ${it.message}" }
            }
            onDone(msg)
        }
    }

    fun deleteAll(onDone: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { backup.deleteAll() }
            onDone()
        }
    }
}
