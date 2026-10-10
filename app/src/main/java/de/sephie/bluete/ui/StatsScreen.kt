package de.sephie.bluete.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.sephie.bluete.domain.OvulationSource
import de.sephie.bluete.domain.Phase
import de.sephie.bluete.ui.theme.LocalCycleColors
import kotlin.math.roundToInt

@Composable
fun StatsScreen(state: UiState) {
    val stats = state.stats
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Statistik", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            if (stats == null || stats.cycles.isEmpty()) {
                InfoCard {
                    Text("Sobald zwei Perioden eingetragen sind, siehst du hier deine Zyklen.")
                }
                return@Column
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Tile("Ø Zyklus", "${"%.1f".format(stats.cycleLength)} T", Modifier.weight(1f))
                Tile("Kürzester", "${stats.minCycle ?: "–"} T", Modifier.weight(1f))
                Tile("Längster", "${stats.maxCycle ?: "–"} T", Modifier.weight(1f))
                Tile("Ø Periode", "${stats.periodLength} T", Modifier.weight(1f))
                if (wide) Tile("Lutealphase", "${stats.lutealLength.roundToInt()} T", Modifier.weight(1f))
            }
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CycleChart(state); CycleHistory(state)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        TopSymptoms(state); PhaseTable(state)
                    }
                }
            } else {
                Tile("Lutealphase", "${stats.lutealLength.roundToInt()} Tage", Modifier.fillMaxWidth())
                CycleChart(state); CycleHistory(state); TopSymptoms(state); PhaseTable(state)
            }
        }
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CycleChart(state: UiState) {
    val stats = state.stats ?: return
    val cycles = stats.cycles.takeLast(12)
    val c = LocalCycleColors.current
    val grid = MaterialTheme.colorScheme.outlineVariant
    val avgColor = MaterialTheme.colorScheme.primary
    InfoCard(title = "Zykluslängen (letzte ${cycles.size})") {
        val maxLen = (cycles.maxOf { it.length }.coerceAtLeast(stats.cycleLength.roundToInt()) + 3).toFloat()
        val minLen = (cycles.minOf { it.length } - 5).coerceAtLeast(0).toFloat()
        Canvas(Modifier.fillMaxWidth().height(180.dp)) {
            val n = cycles.size
            val slot = size.width / n
            val barW = slot * 0.6f
            fun y(v: Float) = size.height - (v - minLen) / (maxLen - minLen) * size.height
            for (i in 0..3) {
                val yy = size.height * i / 3f
                drawLine(grid, Offset(0f, yy), Offset(size.width, yy), 1f)
            }
            cycles.forEachIndexed { i, cy ->
                val top = y(cy.length.toFloat())
                val col = if (cy.length in 15..60) c.period else c.period.copy(alpha = 0.35f)
                drawRoundRect(
                    col,
                    topLeft = Offset(i * slot + (slot - barW) / 2f, top),
                    size = Size(barW, size.height - top),
                    cornerRadius = CornerRadius(8f, 8f)
                )
            }
            val ay = y(stats.cycleLength.toFloat())
            drawLine(avgColor, Offset(0f, ay), Offset(size.width, ay), 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
        }
        Row(Modifier.fillMaxWidth()) {
            cycles.forEach {
                Text("${it.length}", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
            }
        }
        Text(
            "Gestrichelt: gewichteter Durchschnitt (${"%.1f".format(stats.cycleLength)} Tage)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CycleHistory(state: UiState) {
    val stats = state.stats ?: return
    InfoCard(title = "Zyklusverlauf") {
        Row {
            Text("Beginn", Modifier.weight(1.3f), style = MaterialTheme.typography.labelLarge)
            Text("Länge", Modifier.weight(0.8f), style = MaterialTheme.typography.labelLarge)
            Text("Periode", Modifier.weight(0.8f), style = MaterialTheme.typography.labelLarge)
            Text("Eisprung", Modifier.weight(1.4f), style = MaterialTheme.typography.labelLarge)
        }
        stats.cycles.reversed().take(24).forEach { cy ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row {
                Text(cy.start.dayMonth() + " ${cy.start.year % 100}", Modifier.weight(1.3f))
                Text("${cy.length} T", Modifier.weight(0.8f))
                Text(cy.periodLength?.let { "$it T" } ?: "–", Modifier.weight(0.8f))
                Text(
                    cy.ovulation.dayMonth() + when (cy.ovulationSource) {
                        OvulationSource.EINTRAG -> " ✔"
                        OvulationSource.SCHLEIM -> " 💧"
                        OvulationSource.BERECHNET -> " (ber.)"
                    },
                    Modifier.weight(1.4f)
                )
            }
        }
        Text(
            "✔ = eingetragen, 💧 = aus Schleim erkannt, ber. = berechnet",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TopSymptoms(state: UiState) {
    val top = state.engine.topSymptoms(state.symptoms)
    InfoCard(title = "Häufigste Symptome") {
        if (top.isEmpty()) Text("Noch keine Symptome eingetragen.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        val maxCount = top.maxOfOrNull { it.count } ?: 1
        val c = LocalCycleColors.current
        top.forEach { t ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.name, Modifier.width(150.dp))
                Box(Modifier.weight(1f).height(14.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth(t.count.toFloat() / maxCount)
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(c.luteal)
                    )
                }
                Text("  ${t.count}× · Ø ${"%.1f".format(t.avgSeverity)}", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun PhaseTable(state: UiState) {
    val stats = state.stats ?: return
    val corr = state.engine.phaseCorrelation(state.symptoms, state.periods, stats, state.current, state.today)
    if (corr.isEmpty()) return
    val c = LocalCycleColors.current
    InfoCard(title = "Symptome nach Zyklusphase") {
        Row {
            Text("", Modifier.weight(1.6f))
            Phase.entries.forEach { p ->
                Text(p.emoji, Modifier.weight(1f), textAlign = TextAlign.Center)
            }
        }
        val maxCell = corr.values.flatMap { it.values }.maxOrNull() ?: 1
        corr.entries.sortedByDescending { e -> e.value.values.sum() }.take(12).forEach { (name, m) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Text(name, Modifier.weight(1.6f), style = MaterialTheme.typography.bodySmall)
                Phase.entries.forEach { p ->
                    val v = m[p] ?: 0
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(2.dp)
                            .height(26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(c.phase(p).copy(alpha = if (v == 0) 0.06f else 0.2f + 0.8f * v / maxCell)),
                        contentAlignment = Alignment.Center
                    ) { if (v > 0) Text("$v", style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
        Text(
            Phase.entries.joinToString("  ") { "${it.emoji} ${it.label}" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
