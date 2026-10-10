package de.sephie.bluete.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.sephie.bluete.BlueteApp
import de.sephie.bluete.data.AppSettings
import de.sephie.bluete.data.CustomSymptom
import de.sephie.bluete.data.ServerData
import de.sephie.bluete.data.SyncState
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class UiState(
    /** Es liegen Daten vor (vom Server oder aus dem Zwischenspeicher). */
    val hasData: Boolean = false,
    val today: LocalDate = LocalDate.now(),
    val settings: AppSettings = AppSettings(),
    val sync: SyncState = SyncState(),
    val userName: String = "",
    val defaultCycleLength: Int = 28,
    val defaultPeriodLength: Int = 5,
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

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val blueteApp = app as BlueteApp
    private val repo = blueteApp.repository
    private val settingsStore = blueteApp.settings
    private val today = MutableStateFlow(LocalDate.now())

    val settings: StateFlow<AppSettings> = settingsStore.state

    /** Meldungen für die Snackbar. */
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    val state: StateFlow<UiState> = combine(repo.data, repo.sync, settingsStore.state, today) { data, sync, settings, day ->
        withContext(Dispatchers.Default) { buildState(data, sync, settings, day) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    init {
        viewModelScope.launch {
            repo.loadCache()
            repo.refresh()
        }
    }

    private fun buildState(data: ServerData?, sync: SyncState, settings: AppSettings, day: LocalDate): UiState {
        if (data == null) return UiState(today = day, settings = settings, sync = sync)
        val engine = CycleEngine(data.cycleLength, data.periodLength)
        val stats = engine.stats(data.periods, data.ovulations, data.mucus, day)
        val current = engine.currentCycle(data.periods, data.ovulations, data.mucus, stats, day)
        return UiState(
            hasData = true,
            today = day,
            settings = settings,
            sync = sync,
            userName = data.userName,
            defaultCycleLength = data.cycleLength,
            defaultPeriodLength = data.periodLength,
            periods = data.periods,
            marks = data.ovulations,
            mucus = data.mucus,
            symptoms = data.symptoms,
            customSymptoms = data.customSymptoms,
            engine = engine,
            stats = stats,
            current = current,
            upcoming = current?.let { engine.upcoming(it, stats, day) } ?: emptyList(),
            alerts = engine.alerts(stats, current, data.symptoms)
        )
    }

    fun updateSettings(change: (AppSettings) -> AppSettings) = settingsStore.update(change)

    /** Datum aktualisieren und neu vom Server laden (z. B. beim Öffnen der App). */
    fun refresh(showResult: Boolean = false) {
        today.value = LocalDate.now()
        viewModelScope.launch {
            val err = repo.refresh()
            if (showResult) _messages.send(err ?: "Aktualisiert.")
        }
    }

    // ─── Anmeldung ─────────────────────────────────────────────────────────

    fun login(server: String, email: String, password: String, onError: (String) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            val err = repo.login(server, email, password)
            _busy.value = false
            if (err != null) onError(err)
        }
    }

    fun logout() {
        viewModelScope.launch { repo.logout() }
    }

    // ─── Schreiben ─────────────────────────────────────────────────────────

    private fun write(success: String? = null, onSuccess: () -> Unit = {}, action: (de.sephie.bluete.data.BloomApi) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            val err = repo.write(action)
            _busy.value = false
            if (err == null) {
                success?.let { _messages.send(it) }
                onSuccess()
            } else {
                _messages.send(err)
            }
        }
    }

    fun startPeriod(date: LocalDate) =
        write("Periode eingetragen.") { it.createPeriod(date.toString(), null) }

    fun setPeriod(p: PeriodRange, start: LocalDate, end: LocalDate?) =
        write("Periode gespeichert.") { it.updatePeriod(p.id, start.toString(), end?.toString()) }

    fun deletePeriod(p: PeriodRange) =
        write("Periode gelöscht.") { it.deletePeriod(p.id) }

    /** Welche Perioden-Aktionen passen zu diesem Tag? */
    fun periodActions(date: LocalDate, s: UiState): List<PeriodAction> {
        val engine = s.engine
        val periodLen = s.stats?.periodLength ?: s.defaultPeriodLength
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
                write("Periode eingetragen.") { api ->
                    if (open != null) {
                        val normalEnd = open.start.plusDays(periodLen - 1L)
                        val end = if (normalEnd.isBefore(date)) normalEnd else date.minusDays(1)
                        api.updatePeriod(open.id, open.start.toString(), end.toString())
                    }
                    api.createPeriod(date.toString(), null)
                }
            }
        }
        return actions
    }

    fun saveDay(
        date: LocalDate,
        ovulationSigns: Set<OvulationSign>,
        mucus: Mucus?,
        symptoms: Map<String, Pair<SymptomCategory, Int>>,
        onSuccess: () -> Unit
    ) = write("Gespeichert.", onSuccess) { api ->
        api.saveDay(
            date.toString(),
            symptoms.map { (name, v) -> Triple(v.first.key, name, v.second) },
            mucus?.key ?: "",
            ovulationSigns.map { it.key }
        )
    }

    fun addCustomSymptom(name: String, category: SymptomCategory) {
        val n = name.trim()
        if (n.isNotEmpty()) write { it.addCustomSymptom(n, category.key) }
    }

    fun deleteCustomSymptom(name: String) = write { it.deleteCustomSymptom(name) }
}
