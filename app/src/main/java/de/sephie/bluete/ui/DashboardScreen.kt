package de.sephie.bluete.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.sephie.bluete.domain.AlertType
import de.sephie.bluete.domain.CurrentCycle
import de.sephie.bluete.domain.OvulationSource
import de.sephie.bluete.domain.Tips
import de.sephie.bluete.ui.theme.LocalCycleColors
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
    state: UiState,
    vm: AppViewModel,
    onOpenDay: (LocalDate) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column {
                Text("Hallo ${state.userName.ifBlank { "Sephie" }} 🌸", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(state.today.long(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val cur = state.current
            if (cur == null) {
                EmptyState(state, onOpenDay, vm)
                return@Column
            }
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        MainColumn(state, cur, vm, onOpenDay)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SideColumn(state, cur)
                    }
                }
            } else {
                MainColumn(state, cur, vm, onOpenDay)
                SideColumn(state, cur)
            }
        }
    }
}

@Composable
private fun EmptyState(state: UiState, onOpenDay: (LocalDate) -> Unit, vm: AppViewModel) {
    InfoCard(title = "Willkommen!") {
        Text("In Bloom sind noch keine Perioden eingetragen. Trag deine letzte Periode ein, dann berechnet die App alles Weitere. Hat sie an einem anderen Tag begonnen, tippe im Kalender auf diesen Tag.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.startPeriod(state.today) }) { Text("Periode hat heute begonnen") }
            OutlinedButton(onClick = { onOpenDay(state.today) }) { Text("Heute eintragen") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MainColumn(state: UiState, cur: CurrentCycle, vm: AppViewModel, onOpenDay: (LocalDate) -> Unit) {
    val colors = LocalCycleColors.current
    val phaseColor = colors.phase(cur.phase)
    val tips = Tips.forPhase(cur.phase)

    // Hauptkarte
    InfoCard(container = phaseColor.copy(alpha = 0.16f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(cur.phase.emoji, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.size(10.dp))
            Column {
                Text(cur.phase.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Zyklustag ${cur.cycleDay}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val headline = when {
            cur.periodRunning -> "Periode läuft – Tag ${cur.cycleDay}"
            cur.daysLate > 0 -> "Periode seit ${tagen(cur.daysLate)} überfällig"
            cur.daysUntilPeriod == 0 -> "Periode heute erwartet"
            cur.daysUntilPeriod == 1 -> "Periode morgen erwartet"
            else -> "Periode in ${tagen(cur.daysUntilPeriod)}"
        }
        Text(headline, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (!cur.periodRunning) {
            Text(
                "Voraussichtlich ${cur.nextPeriod.long()} (± ${tage(cur.uncertainty)})",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(tips.description)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (cur.periodRunning) {
                Button(onClick = { endRunning(state, vm, state.today) }) { Text("Periode heute beendet") }
                OutlinedButton(onClick = { endRunning(state, vm, state.today.minusDays(1)) }) { Text("Gestern beendet") }
            } else {
                Button(onClick = { vm.periodActions(state.today, state).lastOrNull()?.run?.invoke() }) {
                    Text("Periode hat heute begonnen")
                }
            }
            FilledTonalButton(onClick = { onOpenDay(state.today) }) { Text("Heute eintragen") }
        }
    }

    // Fruchtbarkeit
    if (state.settings.showFertility) {
        InfoCard(title = "Eisprung & fruchtbare Tage") {
            val inWindow = cur.inFertileWindow
            if (inWindow) {
                Badge("Du bist gerade im fruchtbaren Fenster", colors.fertile)
            }
            if (cur.fertileMucusNow) {
                Badge("Dein Zervixschleim deutet auf fruchtbare Tage hin", colors.ovulationSoft)
            }
            val ovuText = when (cur.ovulationSource) {
                OvulationSource.BERECHNET -> "Eisprung erwartet: ${cur.ovulation.long()}"
                else -> "Eisprung: ${cur.ovulation.long()} (${cur.ovulationSource.label})"
            }
            Text(ovuText, fontWeight = FontWeight.Medium)
            Text("Fruchtbares Fenster: ${range(cur.fertileStart, cur.fertileEnd)}")
            Text(
                "Spürst du den Eisprung (z. B. Wärme im Unterleib), trag ihn im Tag ein. Dann wird die nächste Periode ab dem Eisprung berechnet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // Hinweise
    for (a in state.alerts) {
        val c = when (a.type) {
            AlertType.WARNUNG -> colors.ovulationSoft
            AlertType.ERFOLG -> colors.fertile
            AlertType.INFO -> colors.periodSoft
        }
        InfoCard(container = c) {
            Text("${a.icon}  ${a.title}", fontWeight = FontWeight.SemiBold)
            Text(a.message)
        }
    }

    // Vorhersagen
    InfoCard(title = "Nächste Zyklen") {
        state.upcoming.forEachIndexed { i, p ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(colors.period)
                Text("  Periode: ${range(p.periodStart, p.periodEnd)}", fontWeight = FontWeight.Medium)
            }
            if (state.settings.showFertility) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(colors.ovulation)
                    Text("  Eisprung: ${p.ovulation.short()}")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(colors.follicular)
                    Text("  Fruchtbar: ${range(p.fertileStart, p.fertileEnd)}")
                }
            }
        }
    }

    // Grundlage der Berechnung
    state.stats?.let { s ->
        InfoCard(title = "So genau ist die Vorhersage") {
            Text(
                if (s.usedCycleCount == 0) "Noch keine abgeschlossenen Zyklen – es gilt die Vorgabe aus Bloom von ${state.defaultCycleLength} Tagen."
                else "Grundlage: ${s.usedCycleCount} ${if (s.usedCycleCount == 1) "Zyklus" else "Zyklen"}, Ø ${"%.1f".format(s.cycleLength)} Tage (neuere zählen stärker)."
            )
            Text(
                if (s.lutealCount == 0) "Lutealphase: Standardwert 14 Tage. Mit Eisprung-Einträgen lernt die App deinen eigenen Wert."
                else "Deine Lutealphase: Ø ${s.lutealLength.roundToInt()} Tage aus ${s.lutealCount} beobachteten Eisprüngen."
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SideColumn(state: UiState, cur: CurrentCycle) {
    val recent = state.symptoms.sortedByDescending { it.date }.take(8)
    InfoCard(title = "Letzte Symptome") {
        if (recent.isEmpty()) {
            Text("Noch keine Symptome eingetragen.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        for (s in recent) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.date.dayMonth(), Modifier.widthIn(min = 64.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(s.name, Modifier.weight(1f))
                SeverityDots(s.severity)
            }
        }
    }

    if (state.settings.showTips) {
        val tips = Tips.forPhase(cur.phase)
        InfoCard(title = "Für dich in dieser Phase") {
            Text(tips.encouragement, style = MaterialTheme.typography.bodyLarge)
        }
        InfoCard(title = "🏃 Bewegung") {
            Text(tips.exerciseTip)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tips.exerciseExamples.forEach { SuggestionChip(onClick = {}, label = { Text(it) }) }
            }
        }
        InfoCard(title = "🥗 Ernährung") {
            Text(tips.nutritionTip)
            Text("Wichtige Nährstoffe", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tips.keyNutrients.forEach { SuggestionChip(onClick = {}, label = { Text(it) }) }
            }
            Text("Gut jetzt", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tips.foods.forEach { SuggestionChip(onClick = {}, label = { Text(it) }) }
            }
        }
    }
}

private fun endRunning(state: UiState, vm: AppViewModel, end: LocalDate) {
    val open = state.periods.filter { it.start <= state.today }.maxByOrNull { it.start } ?: return
    vm.setPeriod(open, open.start, if (end.isBefore(open.start)) open.start else end)
}

@Composable
fun Dot(color: Color, size: Int = 10) {
    androidx.compose.foundation.layout.Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun SeverityDots(severity: Int) {
    val c = LocalCycleColors.current.period
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 1..5) Dot(if (i <= severity) c else c.copy(alpha = 0.2f), 8)
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Text(
        text,
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Medium
    )
}
