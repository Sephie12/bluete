package de.sephie.bluete.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.sephie.bluete.domain.DayMarks
import de.sephie.bluete.ui.theme.CycleColors
import de.sephie.bluete.ui.theme.LocalCycleColors
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

private const val PAGE_CENTER = 600

@Composable
fun CalendarScreen(state: UiState, onOpenDay: (LocalDate) -> Unit) {
    val baseMonth = remember { YearMonth.now() }
    val pager = rememberPagerState(initialPage = PAGE_CENTER) { PAGE_CENTER * 2 }
    val scope = rememberCoroutineScope()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        val calendar: @Composable (Modifier) -> Unit = { mod ->
            Column(mod) {
                val month = baseMonth.plusMonths((pager.currentPage - PAGE_CENTER).toLong())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Vormonat")
                    }
                    Text(
                        month.atDay(1).monthTitle(),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    IconButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Nächster Monat")
                    }
                }
                TextButton(
                    onClick = { scope.launch { pager.animateScrollToPage(PAGE_CENTER) } },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) { Text("Heute") }
                Row(Modifier.fillMaxWidth()) {
                    listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEach {
                        Text(
                            it, Modifier.weight(1f), textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalPager(state = pager, verticalAlignment = Alignment.Top) { page ->
                    MonthGrid(baseMonth.plusMonths((page - PAGE_CENTER).toLong()), state, onOpenDay)
                }
            }
        }

        if (wide) {
            Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                calendar(Modifier.weight(1.4f).widthIn(max = 760.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Legend(state.settings.showFertility)
                    MonthSummary(state, baseMonth.plusMonths((pager.currentPage - PAGE_CENTER).toLong()))
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                calendar(Modifier)
                Legend(state.settings.showFertility)
                MonthSummary(state, baseMonth.plusMonths((pager.currentPage - PAGE_CENTER).toLong()))
            }
        }
    }
}

@Composable
private fun MonthGrid(month: YearMonth, state: UiState, onOpenDay: (LocalDate) -> Unit) {
    val first = month.atDay(1)
    val gridStart = first.minusDays((first.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
    val gridEnd = gridStart.plusDays(41)
    val marks = remember(state, month) {
        val stats = state.stats ?: return@remember emptyMap<LocalDate, DayMarks>()
        state.engine.calendarMarks(
            gridStart, gridEnd, state.periods, state.marks, state.mucus, state.symptoms,
            stats, state.current, state.today
        )
    }
    val weeks = (ChronoUnit.DAYS.between(gridStart, month.atEndOfMonth()).toInt() / 7) + 1
    Column(Modifier.fillMaxWidth()) {
        for (w in 0 until weeks) {
            Row(Modifier.fillMaxWidth()) {
                for (d in 0 until 7) {
                    val date = gridStart.plusDays((w * 7 + d).toLong())
                    DayCell(
                        date = date,
                        inMonth = date.month == month.month,
                        isToday = date == state.today,
                        marks = marks[date] ?: DayMarks(),
                        showFertility = state.settings.showFertility,
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenDay(date) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    marks: DayMarks,
    showFertility: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val c = LocalCycleColors.current
    val onSurface = MaterialTheme.colorScheme.onSurface
    val fertileBg = when {
        !showFertility -> Color.Transparent
        marks.fertile -> c.fertile
        marks.fertilePredicted -> c.fertile.copy(alpha = 0.45f)
        else -> Color.Transparent
    }
    val fill: Color? = when {
        marks.period -> c.period
        showFertility && marks.ovulation -> c.ovulation
        else -> null
    }
    val ring: Color? = when {
        marks.periodPredicted -> c.period
        showFertility && marks.ovulationPredicted && fill == null -> c.ovulation
        else -> null
    }
    val todayColor = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(fertileBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .fillMaxSize(0.78f)
                .drawBehind {
                    val r = size.minDimension / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    if (fill != null) drawCircle(fill, r, center)
                    if (ring != null) drawCircle(
                        ring, r - 2.dp.toPx(), center,
                        style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
                    )
                    if (isToday) drawCircle(todayColor, r + 1.dp.toPx(), center, style = Stroke(2.5.dp.toPx()))
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                date.dayOfMonth.toString(),
                color = when {
                    fill != null -> c.onStrong
                    !inMonth -> onSurface.copy(alpha = 0.35f)
                    else -> onSurface
                },
                fontWeight = if (isToday || fill != null) FontWeight.Bold else FontWeight.Normal
            )
        }
        // Kleine Markierungen unten in der Zelle
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (marks.hasSymptoms) Dot(c.luteal, 5)
            if (marks.mucus != null && showFertility) Dot(c.follicular, 5)
            if (marks.ovulationMarked && showFertility) Dot(c.ovulation, 5)
        }
    }
}

@Composable
private fun Legend(showFertility: Boolean) {
    val c = LocalCycleColors.current
    InfoCard(title = "Legende") {
        LegendRow(c, "Periode") { Dot(c.period, 16) }
        LegendRow(c, "Periode erwartet") { Ring(c.period) }
        if (showFertility) {
            LegendRow(c, "Fruchtbares Fenster") {
                Box(Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(c.fertile))
            }
            LegendRow(c, "Eisprung (eingetragen oder erkannt)") { Dot(c.ovulation, 16) }
            LegendRow(c, "Eisprung erwartet") { Ring(c.ovulation) }
        }
        Spacer(Modifier.size(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Dot(c.luteal, 7); Text("  Symptome", style = MaterialTheme.typography.bodySmall) }
            if (showFertility) {
                Row(verticalAlignment = Alignment.CenterVertically) { Dot(c.follicular, 7); Text("  Schleim", style = MaterialTheme.typography.bodySmall) }
                Row(verticalAlignment = Alignment.CenterVertically) { Dot(c.ovulation, 7); Text("  Eisprung-Zeichen", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun LegendRow(@Suppress("UNUSED_PARAMETER") c: CycleColors, label: String, icon: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { icon() }
        Text("  $label")
    }
}

@Composable
private fun Ring(color: Color) {
    Box(
        Modifier.size(16.dp).drawBehind {
            drawCircle(
                color, size.minDimension / 2f - 1.dp.toPx(),
                style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
            )
        }
    )
}

@Composable
private fun MonthSummary(state: UiState, month: YearMonth) {
    val first = month.atDay(1)
    val last = month.atEndOfMonth()
    fun inMonth(d: LocalDate) = !d.isBefore(first) && !d.isAfter(last)
    val lines = mutableListOf<String>()
    state.periods.filter { inMonth(it.start) }.forEach { p ->
        lines += "🌺 Periode ab ${p.start.short()}" + (p.end?.let { " bis ${it.short()}" } ?: " (läuft)")
    }
    state.marks.filter { inMonth(it.date) }.forEach { m ->
        lines += "🌸 Eisprung eingetragen: ${m.date.short()} – ${m.signs.joinToString { it.label }}"
    }
    state.current?.let { cur ->
        if (state.settings.showFertility && inMonth(cur.ovulation) && cur.ovulationSource == de.sephie.bluete.domain.OvulationSource.BERECHNET) {
            lines += "🔮 Eisprung erwartet: ${cur.ovulation.short()}"
        }
        if (!cur.periodRunning && inMonth(cur.nextPeriod)) lines += "🔮 Periode erwartet: ${cur.nextPeriod.short()} (± ${tage(cur.uncertainty)})"
    }
    state.upcoming.drop(1).filter { inMonth(it.periodStart) }.forEach { lines += "🔮 Periode erwartet: ${it.periodStart.short()}" }
    val symptomDays = state.symptoms.filter { inMonth(it.date) }.map { it.date }.distinct().size
    if (symptomDays > 0) lines += "📝 Symptome an ${tagen(symptomDays)} eingetragen"

    InfoCard(title = month.atDay(1).monthTitle()) {
        if (lines.isEmpty()) Text("Keine Einträge in diesem Monat.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        lines.forEach { Text(it) }
        Text(
            "Tipp: Tippe auf einen Tag, um Periode, Eisprung, Schleim und Symptome einzutragen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
