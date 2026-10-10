package de.sephie.bluete.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Die komplette Zyklus- und Vorhersagelogik. Reines Kotlin ohne Android,
 * damit sie sich mit Unit-Tests prüfen lässt.
 *
 * Unterschiede zu Bloom (das immer Zykluslänge − 14 rechnet):
 *  - neuere Zyklen zählen stärker, Ausreißer werden abgeschwächt
 *  - die Lutealphase (Eisprung bis Periode) wird aus deinen Eisprung-Einträgen gelernt
 *  - ist im laufenden Zyklus ein Eisprung eingetragen, wird die nächste Periode
 *    ab dem Eisprung berechnet statt über die Zykluslänge
 *  - Zervixschleim kann den Eisprung bestätigen, wenn kein Eintrag vorliegt
 */
class CycleEngine(
    private val defaultCycleLength: Int = 28,
    private val defaultPeriodLength: Int = 5
) {
    companion object {
        const val DEFAULT_LUTEAL = 14.0
        const val MIN_CYCLE = 15
        const val MAX_CYCLE = 60
        const val MIN_LUTEAL = 7
        const val MAX_LUTEAL = 19
        const val RECENT_CYCLES = 8
        const val DECAY = 0.8
        /** Eine offene Periode, die länger als das zurückliegt, gilt als vergessen zu beenden. */
        const val OPEN_PERIOD_MAX_DAYS = 10

        fun days(a: LocalDate, b: LocalDate): Int = ChronoUnit.DAYS.between(a, b).toInt()
    }

    // ─── Hilfsfunktionen ────────────────────────────────────────────────────

    /**
     * Gewichteter Mittelwert, neueste Werte zuerst übergeben.
     * Werte, die stark vom Median abweichen, zählen nur 20 %.
     * Liefert Mittelwert und gewichtete Standardabweichung.
     */
    internal fun robustWeightedMean(newestFirst: List<Double>): Pair<Double, Double> {
        if (newestFirst.isEmpty()) return 0.0 to 0.0
        val sorted = newestFirst.sorted()
        val median = median(sorted)
        val mad = median(newestFirst.map { abs(it - median) }.sorted())
        val threshold = max(4.0, 3.0 * 1.4826 * mad)
        var sumW = 0.0
        var sumWV = 0.0
        val weights = newestFirst.mapIndexed { i, v ->
            val w = DECAY.pow(i) * (if (abs(v - median) > threshold) 0.2 else 1.0)
            sumW += w
            sumWV += w * v
            w
        }
        val mean = sumWV / sumW
        var sumWD = 0.0
        newestFirst.forEachIndexed { i, v -> sumWD += weights[i] * (v - mean) * (v - mean) }
        return mean to sqrt(sumWD / sumW)
    }

    private fun median(sorted: List<Double>): Double {
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
    }

    /** Effektives Ende einer Periode (bei offener Periode bis heute, höchstens begrenzt). */
    fun effectiveEnd(p: PeriodRange, today: LocalDate, periodLength: Int): LocalDate {
        p.end?.let { return it }
        val sinceStart = days(p.start, today)
        return if (sinceStart in 0 until OPEN_PERIOD_MAX_DAYS) today
        else p.start.plusDays((periodLength - 1).toLong())
    }

    /**
     * Ermittelt den beobachteten Eisprung im Zeitraum [from, untilExclusive).
     * Reihenfolge: eigener Eintrag vor Zervixschleim.
     */
    internal fun observedOvulation(
        from: LocalDate,
        untilExclusive: LocalDate,
        marks: List<OvulationMark>,
        mucus: List<MucusObservation>,
        today: LocalDate,
        requireConfirmedMucus: Boolean
    ): Pair<LocalDate, OvulationSource>? {
        val inWindow = marks.filter { !it.date.isBefore(from) && it.date.isBefore(untilExclusive) }
        if (inWindow.isNotEmpty()) {
            // Ein positiver LH-Test kündigt den Eisprung meist ca. einen Tag vorher an.
            val candidates = inWindow.map { m ->
                if (m.signs == setOf(OvulationSign.LH_TEST)) m.date.plusDays(1) else m.date
            }.sorted()
            return candidates[(candidates.size - 1) / 2] to OvulationSource.EINTRAG
        }

        val obs = mucus.filter { !it.date.isBefore(from) && it.date.isBefore(untilExclusive) }
            .sortedBy { it.date }
        val best = obs.maxOfOrNull { it.type.fertility } ?: return null
        if (best < 3) return null
        // Höhepunkt = letzter Tag mit dem fruchtbarsten Schleim
        val peak = obs.last { it.type.fertility == best }.date
        if (requireConfirmedMucus) {
            val dropAfter = obs.any { it.date.isAfter(peak) && it.type.fertility < best }
            val longEnoughAgo = days(peak, today) >= 3
            if (!dropAfter && !longEnoughAgo) return null
        }
        return peak to OvulationSource.SCHLEIM
    }

    // ─── Statistik über abgeschlossene Zyklen ──────────────────────────────

    fun stats(
        periods: List<PeriodRange>,
        marks: List<OvulationMark>,
        mucus: List<MucusObservation>,
        today: LocalDate
    ): CycleStats {
        val sorted = periods.sortedBy { it.start }

        // Periodenlänge aus abgeschlossenen Perioden
        val periodLens = sorted.filter { it.end != null }
            .map { days(it.start, it.end!!) + 1 }
            .filter { it in 1..14 }
            .reversed()
            .map { it.toDouble() }
        val periodLength = if (periodLens.isEmpty()) defaultPeriodLength
        else robustWeightedMean(periodLens.take(RECENT_CYCLES)).first.roundToInt().coerceIn(1, 14)

        // Rohe Zyklen und beobachtete Lutealphasen
        data class Raw(val start: LocalDate, val next: LocalDate, val len: Int, val pLen: Int?, val obs: Pair<LocalDate, OvulationSource>?)
        val raw = sorted.zipWithNext().map { (a, b) ->
            val len = days(a.start, b.start)
            val pLen = a.end?.let { days(a.start, it) + 1 }
            val obs = observedOvulation(a.start, b.start, marks, mucus, today, requireConfirmedMucus = false)
                ?.takeIf { (o, _) ->
                    val lut = days(o, b.start)
                    days(a.start, o) >= 5 && lut in MIN_LUTEAL..MAX_LUTEAL
                }
            Raw(a.start, b.start, len, pLen, obs)
        }

        val usable = raw.filter { it.len in MIN_CYCLE..MAX_CYCLE }
        val lengthsNewestFirst = usable.reversed().take(RECENT_CYCLES).map { it.len.toDouble() }
        val (cycleMean, cycleSd) = if (lengthsNewestFirst.isEmpty()) defaultCycleLength.toDouble() to 0.0
        else robustWeightedMean(lengthsNewestFirst)

        val lutealNewestFirst = raw.reversed().mapNotNull { r -> r.obs?.let { days(it.first, r.next).toDouble() } }
            .take(RECENT_CYCLES)
        val (lutMean, lutSd) = if (lutealNewestFirst.isEmpty()) DEFAULT_LUTEAL to 0.0
        else {
            val (m, sd) = robustWeightedMean(lutealNewestFirst)
            // Bei wenigen Werten etwas Richtung 14 ziehen, damit ein einzelner Ausreißer nicht alles bestimmt.
            val n = lutealNewestFirst.size.toDouble()
            ((n * m + DEFAULT_LUTEAL) / (n + 1)) to sd
        }

        val cycles = raw.map { r ->
            val obs = r.obs
            val ovu = obs?.first ?: r.next.minusDays(lutMean.roundToInt().toLong())
            CompletedCycle(
                start = r.start,
                nextStart = r.next,
                length = r.len,
                periodLength = r.pLen,
                ovulation = ovu,
                ovulationSource = obs?.second ?: OvulationSource.BERECHNET,
                lutealLength = obs?.let { days(it.first, r.next) }
            )
        }

        return CycleStats(
            cycles = cycles,
            usedCycleCount = usable.size,
            cycleLength = cycleMean,
            cycleSd = cycleSd,
            minCycle = usable.minOfOrNull { it.len },
            maxCycle = usable.maxOfOrNull { it.len },
            periodLength = periodLength,
            lutealLength = lutMean,
            lutealSd = lutSd,
            lutealCount = lutealNewestFirst.size
        )
    }

    // ─── Laufender Zyklus ──────────────────────────────────────────────────

    fun currentCycle(
        periods: List<PeriodRange>,
        marks: List<OvulationMark>,
        mucus: List<MucusObservation>,
        stats: CycleStats,
        today: LocalDate
    ): CurrentCycle? {
        val last = periods.filter { !it.start.isAfter(today) }.maxByOrNull { it.start } ?: return null
        val s = last.start
        val cycleLen = stats.cycleLength.roundToInt()
        val luteal = stats.lutealLength.roundToInt()
        val periodEnd = effectiveEnd(last, today, stats.periodLength)
        val periodRunning = last.end == null && days(s, today) < OPEN_PERIOD_MAX_DAYS ||
            (last.end != null && !today.isAfter(last.end))

        val observed = observedOvulation(
            from = s.plusDays(1), untilExclusive = today.plusDays(1),
            marks = marks, mucus = mucus, today = today, requireConfirmedMucus = true
        )?.takeIf { (o, _) -> days(s, o) >= 5 }

        val nextPeriod: LocalDate
        val ovulation: LocalDate
        val source: OvulationSource
        val uncertainty: Int
        if (observed != null) {
            ovulation = observed.first
            source = observed.second
            nextPeriod = ovulation.plusDays(luteal.toLong())
            uncertainty = when {
                stats.lutealCount >= 3 -> max(1, stats.lutealSd.roundToInt())
                else -> 2
            }.coerceAtMost(5)
        } else {
            nextPeriod = s.plusDays(cycleLen.toLong())
            val minOvu = periodEnd.plusDays(1)
            val candidate = nextPeriod.minusDays(luteal.toLong())
            ovulation = if (candidate.isBefore(minOvu)) minOvu else candidate
            source = OvulationSource.BERECHNET
            uncertainty = when {
                stats.usedCycleCount == 0 -> 3
                stats.usedCycleCount < 3 -> max(2, stats.cycleSd.roundToInt())
                else -> max(1, stats.cycleSd.roundToInt())
            }.coerceAtMost(7)
        }

        val fertileStart = ovulation.minusDays(5)
        val fertileEnd = ovulation.plusDays(1)
        val daysUntil = days(today, nextPeriod)
        val daysLate = if (daysUntil < 0) -daysUntil else 0

        val recentMucus = mucus.filter { !it.date.isBefore(today.minusDays(1)) && !it.date.isAfter(today) }
        val fertileMucusNow = recentMucus.any { it.type.fertility >= 3 }

        return CurrentCycle(
            start = s,
            cycleDay = days(s, today) + 1,
            phase = phaseOf(today, s, periodEnd, ovulation),
            periodRunning = periodRunning,
            nextPeriod = nextPeriod,
            uncertainty = uncertainty,
            daysUntilPeriod = daysUntil,
            daysLate = daysLate,
            ovulation = ovulation,
            ovulationSource = source,
            fertileStart = fertileStart,
            fertileEnd = fertileEnd,
            inFertileWindow = !today.isBefore(fertileStart) && !today.isAfter(fertileEnd),
            fertileMucusNow = fertileMucusNow
        )
    }

    fun phaseOf(day: LocalDate, cycleStart: LocalDate, periodEnd: LocalDate, ovulation: LocalDate): Phase = when {
        !day.isAfter(periodEnd) && !day.isBefore(cycleStart) -> Phase.MENSTRUATION
        abs(days(ovulation, day)) <= 1 -> Phase.EISPRUNG
        day.isBefore(ovulation) -> Phase.FOLLIKEL
        else -> Phase.LUTEAL
    }

    /** Vorhersagen für die nächsten [count] Zyklen ab der nächsten Periode. */
    fun upcoming(current: CurrentCycle, stats: CycleStats, today: LocalDate, count: Int = 3): List<PredictedCycle> {
        val cycleLen = stats.cycleLength.roundToInt().toLong()
        val luteal = stats.lutealLength.roundToInt().toLong()
        // Ist die Periode überfällig, verschiebt sich die Vorhersage auf heute.
        val first = if (current.nextPeriod.isBefore(today)) today else current.nextPeriod
        return (0 until count).map { k ->
            val start = first.plusDays(k * cycleLen)
            val ovu = start.plusDays(cycleLen - luteal)
            PredictedCycle(
                periodStart = start,
                periodEnd = start.plusDays((stats.periodLength - 1).toLong()),
                ovulation = ovu,
                fertileStart = ovu.minusDays(5),
                fertileEnd = ovu.plusDays(1)
            )
        }
    }

    // ─── Kalender ──────────────────────────────────────────────────────────

    fun calendarMarks(
        from: LocalDate,
        to: LocalDate,
        periods: List<PeriodRange>,
        marks: List<OvulationMark>,
        mucus: List<MucusObservation>,
        symptoms: List<SymptomLog>,
        stats: CycleStats,
        current: CurrentCycle?,
        today: LocalDate
    ): Map<LocalDate, DayMarks> {
        val map = HashMap<LocalDate, DayMarks>()
        fun inRange(d: LocalDate) = !d.isBefore(from) && !d.isAfter(to)
        fun update(d: LocalDate, f: (DayMarks) -> DayMarks) {
            if (inRange(d)) map[d] = f(map[d] ?: DayMarks())
        }
        fun eachDay(a: LocalDate, b: LocalDate, f: (LocalDate) -> Unit) {
            var d = a
            while (!d.isAfter(b)) { f(d); d = d.plusDays(1) }
        }

        // Vergangene Zyklen: Eisprung und fruchtbares Fenster
        for (c in stats.cycles) {
            eachDay(c.ovulation.minusDays(5), c.ovulation.plusDays(1)) { d -> update(d) { it.copy(fertile = true) } }
            update(c.ovulation) { it.copy(ovulation = c.ovulationSource != OvulationSource.BERECHNET, ovulationPredicted = c.ovulationSource == OvulationSource.BERECHNET) }
        }

        // Laufender Zyklus
        if (current != null) {
            val predicted = current.ovulationSource == OvulationSource.BERECHNET
            eachDay(current.fertileStart, current.fertileEnd) { d ->
                update(d) { if (predicted) it.copy(fertilePredicted = true) else it.copy(fertile = true) }
            }
            update(current.ovulation) {
                if (predicted) it.copy(ovulationPredicted = true) else it.copy(ovulation = true)
            }
            // Zukünftige Zyklen
            val horizonCycles = max(3, (days(today, to) / max(15, stats.cycleLength.roundToInt())) + 2)
            for (p in upcoming(current, stats, today, min(horizonCycles, 24))) {
                eachDay(p.periodStart, p.periodEnd) { d -> update(d) { it.copy(periodPredicted = true) } }
                eachDay(p.fertileStart, p.fertileEnd) { d -> update(d) { it.copy(fertilePredicted = true) } }
                update(p.ovulation) { it.copy(ovulationPredicted = true) }
            }
        }

        // Tatsächliche Perioden überschreiben Vorhersagen
        for (p in periods) {
            eachDay(p.start, effectiveEnd(p, today, stats.periodLength)) { d ->
                update(d) { it.copy(period = true, periodPredicted = false, fertile = false, fertilePredicted = false) }
            }
        }
        for (m in marks) update(m.date) { it.copy(ovulationMarked = true) }
        for (o in mucus) update(o.date) { it.copy(mucus = o.type) }
        for (s in symptoms) update(s.date) { it.copy(hasSymptoms = true) }
        return map
    }

    // ─── Hinweise ──────────────────────────────────────────────────────────

    fun alerts(stats: CycleStats, current: CurrentCycle?, symptoms: List<SymptomLog>): List<SmartAlert> {
        val alerts = mutableListOf<SmartAlert>()
        val recent = stats.cycles.filter { it.length in MIN_CYCLE..MAX_CYCLE }.takeLast(12).map { it.length }
        if (recent.size >= 2) {
            val avg = recent.average()
            val maxDiff = recent.maxOf { abs(it - avg) }
            if (maxDiff > 7) {
                alerts += SmartAlert(
                    "⚠️", "Unregelmäßige Zyklen",
                    "Deine Zyklen weichen um bis zu ${maxDiff.roundToInt()} Tage voneinander ab. " +
                        "Wenn das für dich ungewöhnlich ist, sprich es bei deiner Frauenärztin an.",
                    AlertType.WARNUNG
                )
            } else if (recent.size >= 3 && maxDiff <= 3) {
                alerts += SmartAlert(
                    "✅", "Regelmäßige Zyklen",
                    "Deine Zyklen sind sehr gleichmäßig (Ø ${"%.1f".format(avg)} Tage).",
                    AlertType.ERFOLG
                )
            }
        }
        if (current != null && current.daysLate > current.uncertainty && !current.periodRunning) {
            alerts += SmartAlert(
                "🔔", "Periode verspätet",
                "Die Periode ist seit ${current.daysLate} ${if (current.daysLate == 1) "Tag" else "Tagen"} überfällig " +
                    "(Zyklustag ${current.cycleDay}). Stress, Krankheit oder Veränderungen im Alltag können das verursachen.",
                AlertType.WARNUNG
            )
        }
        val lastFive = symptoms.sortedByDescending { it.date }.take(5)
        if (lastFive.size >= 5 && lastFive.count { it.severity >= 4 } >= 3) {
            alerts += SmartAlert(
                "💛", "Starke Beschwerden",
                "Du hast zuletzt mehrere starke Symptome eingetragen. Gönn dir Ruhe, trink genug und hol dir Unterstützung, wenn du sie brauchst.",
                AlertType.INFO
            )
        }
        return alerts
    }

    // ─── Symptom-Auswertung ────────────────────────────────────────────────

    fun topSymptoms(symptoms: List<SymptomLog>, limit: Int = 10): List<SymptomTrend> =
        symptoms.groupBy { it.name }
            .map { (name, list) -> SymptomTrend(name, list.first().category, list.size, list.map { it.severity }.average()) }
            .sortedByDescending { it.count }
            .take(limit)

    /** Wie oft trat ein Symptom in welcher Zyklusphase auf? */
    fun phaseCorrelation(
        symptoms: List<SymptomLog>,
        periods: List<PeriodRange>,
        stats: CycleStats,
        current: CurrentCycle?,
        today: LocalDate
    ): Map<String, Map<Phase, Int>> {
        val sorted = periods.sortedBy { it.start }
        val result = HashMap<String, MutableMap<Phase, Int>>()
        for (s in symptoms) {
            val period = sorted.lastOrNull { !it.start.isAfter(s.date) } ?: continue
            val cycle = stats.cycles.firstOrNull { it.start == period.start }
            val ovu = cycle?.ovulation
                ?: if (current != null && current.start == period.start) current.ovulation
                else continue
            val phase = phaseOf(s.date, period.start, effectiveEnd(period, today, stats.periodLength), ovu)
            val m = result.getOrPut(s.name) { mutableMapOf() }
            m[phase] = (m[phase] ?: 0) + 1
        }
        return result
    }
}
