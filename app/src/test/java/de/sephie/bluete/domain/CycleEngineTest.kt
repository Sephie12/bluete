package de.sephie.bluete.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CycleEngineTest {

    private val engine = CycleEngine()
    private fun d(s: String) = LocalDate.parse(s)

    /** Erzeugt Perioden mit festen Zykluslängen, beginnend bei [first]. */
    private fun periods(first: String, lengths: List<Int>, periodLen: Int = 5): List<PeriodRange> {
        var start = d(first)
        val list = mutableListOf<PeriodRange>()
        list += PeriodRange(0, start, start.plusDays(periodLen - 1L))
        lengths.forEachIndexed { i, l ->
            start = start.plusDays(l.toLong())
            list += PeriodRange(i + 1L, start, start.plusDays(periodLen - 1L))
        }
        return list
    }

    @Test
    fun ohneDatenGibtEsKeinenZyklus() {
        val stats = engine.stats(emptyList(), emptyList(), emptyList(), d("2026-10-10"))
        assertEquals(28.0, stats.cycleLength, 0.001)
        assertNull(engine.currentCycle(emptyList(), emptyList(), emptyList(), stats, d("2026-10-10")))
    }

    @Test
    fun regelmaessigeZyklenWieBloom() {
        val p = periods("2026-01-01", listOf(28, 28, 28, 28))
        val today = d("2026-05-10")
        val stats = engine.stats(p, emptyList(), emptyList(), today)
        assertEquals(28.0, stats.cycleLength, 0.001)
        assertEquals(5, stats.periodLength)
        val cur = engine.currentCycle(p, emptyList(), emptyList(), stats, today)!!
        // Letzte Periode 2026-04-23 → nächste 2026-05-21, Eisprung 14 Tage vorher
        assertEquals(d("2026-04-23"), cur.start)
        assertEquals(d("2026-05-21"), cur.nextPeriod)
        assertEquals(d("2026-05-07"), cur.ovulation)
        assertEquals(OvulationSource.BERECHNET, cur.ovulationSource)
        assertEquals(1, cur.uncertainty)
    }

    @Test
    fun neuereZyklenZaehlenMehr() {
        val p = periods("2026-01-01", listOf(26, 26, 26, 30, 30, 30))
        val stats = engine.stats(p, emptyList(), emptyList(), d("2026-08-01"))
        assertTrue("Mittel sollte näher an 30 liegen: ${stats.cycleLength}", stats.cycleLength > 28.0)
    }

    @Test
    fun ausreisserWirdAbgeschwaecht() {
        // Ein vergessener Eintrag erzeugt einen 45-Tage-Zyklus
        val p = periods("2026-01-01", listOf(28, 29, 28, 45, 28, 29))
        val stats = engine.stats(p, emptyList(), emptyList(), d("2026-08-20"))
        assertTrue("Ausreißer darf nicht stark ziehen: ${stats.cycleLength}", stats.cycleLength < 31.0)
    }

    @Test
    fun lutealphaseWirdAusEintraegenGelernt() {
        // Zyklen 28 Tage, Eisprung jeweils an Tag 17 → Lutealphase 12 Tage
        val p = periods("2026-01-01", listOf(28, 28, 28, 28))
        val marks = p.dropLast(1).map { OvulationMark(it.start.plusDays(16), setOf(OvulationSign.WAERME)) }
        val today = d("2026-04-25")
        val stats = engine.stats(p, marks, emptyList(), today)
        assertEquals(4, stats.lutealCount)
        // 4 × 12 und einmal Vorgabe 14 → 12,4
        assertEquals(12.4, stats.lutealLength, 0.01)
        val cur = engine.currentCycle(p, marks, emptyList(), stats, today)!!
        // Ohne Eintrag im laufenden Zyklus: Eisprung = nächste Periode − 12
        assertEquals(d("2026-05-21"), cur.nextPeriod)
        assertEquals(d("2026-05-09"), cur.ovulation)
    }

    @Test
    fun eisprungImLaufendenZyklusVerschiebtPeriode() {
        val p = periods("2026-01-01", listOf(28, 28, 28, 28))
        val past = p.dropLast(1).map { OvulationMark(it.start.plusDays(14), setOf(OvulationSign.WAERME)) }
        // Im laufenden Zyklus (Start 2026-04-23) kommt der Eisprung spät, an Tag 20
        val lateOvu = OvulationMark(d("2026-05-12"), setOf(OvulationSign.WAERME))
        val today = d("2026-05-14")
        val stats = engine.stats(p, past + lateOvu, emptyList(), today)
        val cur = engine.currentCycle(p, past + lateOvu, emptyList(), stats, today)!!
        assertEquals(OvulationSource.EINTRAG, cur.ovulationSource)
        assertEquals(d("2026-05-12"), cur.ovulation)
        // Gelernte Lutealphase 14 Tage → Periode am 2026-05-26 statt 2026-05-21
        assertEquals(d("2026-05-26"), cur.nextPeriod)
    }

    @Test
    fun lhTestBedeutetEisprungAmFolgetag() {
        val p = periods("2026-01-01", listOf(28))
        val marks = listOf(OvulationMark(d("2026-01-14"), setOf(OvulationSign.LH_TEST)))
        val stats = engine.stats(p, marks, emptyList(), d("2026-02-05"))
        assertEquals(d("2026-01-15"), stats.cycles.first().ovulation)
        assertEquals(14, stats.cycles.first().lutealLength)
    }

    @Test
    fun schleimErkenntEisprungErstNachBestaetigung() {
        val p = listOf(PeriodRange(1, d("2026-03-01"), d("2026-03-05")))
        val mucus = listOf(
            MucusObservation(d("2026-03-12"), Mucus.CREMIG),
            MucusObservation(d("2026-03-14"), Mucus.SPINNBAR)
        )
        val stats0 = engine.stats(p, emptyList(), mucus, d("2026-03-14"))
        // Am Tag des Höhepunkts noch nicht bestätigt
        val cur0 = engine.currentCycle(p, emptyList(), mucus, stats0, d("2026-03-14"))!!
        assertEquals(OvulationSource.BERECHNET, cur0.ovulationSource)
        assertTrue(cur0.fertileMucusNow)
        // Danach wird der Schleim wieder klebrig → Höhepunkt bestätigt
        val mucus2 = mucus + MucusObservation(d("2026-03-15"), Mucus.KLEBRIG)
        val cur1 = engine.currentCycle(p, emptyList(), mucus2, stats0, d("2026-03-15"))!!
        assertEquals(OvulationSource.SCHLEIM, cur1.ovulationSource)
        assertEquals(d("2026-03-14"), cur1.ovulation)
        assertEquals(d("2026-03-28"), cur1.nextPeriod)
    }

    @Test
    fun eintragHatVorrangVorSchleim() {
        val p = periods("2026-01-01", listOf(28))
        val marks = listOf(OvulationMark(d("2026-01-15"), setOf(OvulationSign.MITTELSCHMERZ)))
        val mucus = listOf(MucusObservation(d("2026-01-12"), Mucus.SPINNBAR))
        val stats = engine.stats(p, marks, mucus, d("2026-02-01"))
        assertEquals(d("2026-01-15"), stats.cycles.first().ovulation)
        assertEquals(OvulationSource.EINTRAG, stats.cycles.first().ovulationSource)
    }

    @Test
    fun verspaetungWirdErkannt() {
        val p = periods("2026-01-01", listOf(28, 28, 28))
        val today = d("2026-04-05") // Perioden: 01-01, 01-29, 02-26, 03-26
        val stats = engine.stats(p, emptyList(), emptyList(), today)
        val late = engine.currentCycle(p, emptyList(), emptyList(), stats, d("2026-04-28"))!!
        assertEquals(d("2026-04-23"), late.nextPeriod)
        assertEquals(5, late.daysLate)
        val alerts = engine.alerts(stats, late, emptyList())
        assertTrue(alerts.any { it.title == "Periode verspätet" })
        val up = engine.upcoming(late, stats, d("2026-04-28"))
        assertEquals(d("2026-04-28"), up.first().periodStart)
    }

    @Test
    fun offenePeriodeLaeuftBisHeute() {
        val p = listOf(PeriodRange(1, d("2026-10-08"), null))
        val today = d("2026-10-10")
        val stats = engine.stats(p, emptyList(), emptyList(), today)
        val cur = engine.currentCycle(p, emptyList(), emptyList(), stats, today)!!
        assertTrue(cur.periodRunning)
        assertEquals(Phase.MENSTRUATION, cur.phase)
        assertEquals(3, cur.cycleDay)
        val marks = engine.calendarMarks(d("2026-10-01"), d("2026-10-31"), p, emptyList(), emptyList(), emptyList(), stats, cur, today)
        assertTrue(marks[d("2026-10-10")]!!.period)
        assertNull(marks[d("2026-10-11")]?.takeIf { it.period })
        assertNotNull(marks[d("2026-10-22")]?.takeIf { it.ovulationPredicted })
    }

    @Test
    fun phasenKorrelation() {
        val p = periods("2026-01-01", listOf(28))
        val today = d("2026-02-05")
        val stats = engine.stats(p, emptyList(), emptyList(), today)
        val cur = engine.currentCycle(p, emptyList(), emptyList(), stats, today)
        val sym = listOf(
            SymptomLog(d("2026-01-02"), "physical", "Krämpfe", 4),
            SymptomLog(d("2026-01-25"), "emotional", "Reizbarkeit", 3)
        )
        val corr = engine.phaseCorrelation(sym, p, stats, cur, today)
        assertEquals(1, corr["Krämpfe"]!![Phase.MENSTRUATION])
        assertEquals(1, corr["Reizbarkeit"]!![Phase.LUTEAL])
    }
}
