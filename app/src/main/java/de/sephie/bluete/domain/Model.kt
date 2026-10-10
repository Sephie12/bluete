package de.sephie.bluete.domain

import java.time.LocalDate

/** Eine Periode. [end] ist null, solange sie noch läuft. */
data class PeriodRange(val id: Long, val start: LocalDate, val end: LocalDate?)

/** Anzeichen, an denen man einen Eisprung bemerken kann. */
enum class OvulationSign(val key: String, val label: String) {
    WAERME("waerme", "Wärmegefühl im Unterleib"),
    LH_TEST("lh_test", "Ovulationstest positiv"),
    MITTELSCHMERZ("mittelschmerz", "Mittelschmerz"),
    SONSTIGES("sonstiges", "Sonstiges Anzeichen");

    companion object {
        fun fromKey(key: String): OvulationSign? = entries.firstOrNull { it.key == key }
    }
}

/** Ein selbst eingetragener Eisprung an einem Tag. */
data class OvulationMark(val date: LocalDate, val signs: Set<OvulationSign>)

/**
 * Zervixschleim, sortiert nach Fruchtbarkeit.
 * [fertility] 0 = unfruchtbar … 4 = höchste Fruchtbarkeit.
 */
enum class Mucus(val key: String, val label: String, val fertility: Int) {
    TROCKEN("dry", "Trocken", 0),
    KLEBRIG("sticky", "Klebrig", 1),
    CREMIG("creamy", "Cremig", 2),
    WAESSRIG("watery", "Wässrig", 3),
    SPINNBAR("egg-white", "Spinnbar (wie Eiweiß)", 4);

    companion object {
        fun fromKey(key: String): Mucus? = entries.firstOrNull { it.key == key }
    }
}

data class MucusObservation(val date: LocalDate, val type: Mucus)

data class SymptomLog(
    val date: LocalDate,
    val category: String,
    val name: String,
    val severity: Int
)

/** Woher die Eisprung-Angabe eines Zyklus stammt. */
enum class OvulationSource(val label: String) {
    EINTRAG("von dir eingetragen"),
    SCHLEIM("aus Zervixschleim erkannt"),
    BERECHNET("berechnet")
}

enum class Phase(val label: String, val emoji: String) {
    MENSTRUATION("Menstruation", "🌺"),
    FOLLIKEL("Follikelphase", "🌱"),
    EISPRUNG("Eisprung", "🌸"),
    LUTEAL("Lutealphase", "🌙")
}

/** Ein abgeschlossener Zyklus (Periodenbeginn bis Tag vor der nächsten Periode). */
data class CompletedCycle(
    val start: LocalDate,
    val nextStart: LocalDate,
    val length: Int,
    val periodLength: Int?,
    val ovulation: LocalDate,
    val ovulationSource: OvulationSource,
    /** Nur gesetzt, wenn der Eisprung beobachtet (nicht berechnet) wurde. */
    val lutealLength: Int?
)

data class CycleStats(
    val cycles: List<CompletedCycle>,
    /** Zyklen, die in die Berechnung eingehen (plausible Länge). */
    val usedCycleCount: Int,
    val cycleLength: Double,
    val cycleSd: Double,
    val minCycle: Int?,
    val maxCycle: Int?,
    val periodLength: Int,
    val lutealLength: Double,
    val lutealSd: Double,
    val lutealCount: Int
)

data class PredictedCycle(
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val ovulation: LocalDate,
    val fertileStart: LocalDate,
    val fertileEnd: LocalDate
)

data class CurrentCycle(
    val start: LocalDate,
    val cycleDay: Int,
    val phase: Phase,
    val periodRunning: Boolean,
    val nextPeriod: LocalDate,
    /** Unsicherheit der Vorhersage in Tagen (±). */
    val uncertainty: Int,
    val daysUntilPeriod: Int,
    val daysLate: Int,
    val ovulation: LocalDate,
    val ovulationSource: OvulationSource,
    val fertileStart: LocalDate,
    val fertileEnd: LocalDate,
    val inFertileWindow: Boolean,
    /** Heute oder gestern wurde fruchtbarer Schleim (wässrig/spinnbar) beobachtet. */
    val fertileMucusNow: Boolean
)

data class DayMarks(
    val period: Boolean = false,
    val periodPredicted: Boolean = false,
    val fertile: Boolean = false,
    val fertilePredicted: Boolean = false,
    val ovulation: Boolean = false,
    val ovulationPredicted: Boolean = false,
    val ovulationMarked: Boolean = false,
    val hasSymptoms: Boolean = false,
    val mucus: Mucus? = null
)

data class SmartAlert(val icon: String, val title: String, val message: String, val type: AlertType)

enum class AlertType { WARNUNG, ERFOLG, INFO }

data class SymptomTrend(val name: String, val category: String, val count: Int, val avgSeverity: Double)
