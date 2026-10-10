package de.sephie.bluete.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.sephie.bluete.domain.Mucus
import de.sephie.bluete.domain.OvulationSign
import de.sephie.bluete.domain.SymptomCatalog
import de.sephie.bluete.domain.SymptomCategory
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DayEditor(
    date: LocalDate,
    state: UiState,
    vm: AppViewModel,
    onClose: () -> Unit
) {
    // Ausgangswerte für diesen Tag
    val initialSigns = remember(date) { state.marks.firstOrNull { it.date == date }?.signs ?: emptySet() }
    val initialMucus = remember(date) { state.mucus.firstOrNull { it.date == date }?.type }
    val signs = remember(date) { mutableStateMapOf<OvulationSign, Boolean>().apply { initialSigns.forEach { put(it, true) } } }
    var mucus by remember(date) { mutableStateOf(initialMucus) }
    val selected = remember(date) {
        mutableStateMapOf<String, Pair<SymptomCategory, Int>>().apply {
            state.symptoms.filter { it.date == date }.forEach { put(it.name, SymptomCategory.fromKey(it.category) to it.severity) }
        }
    }
    var newName by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf(SymptomCategory.KOERPER) }
    val busy by vm.busy.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Schließen") } },
                title = { Text(date.long()) },
                actions = {
                    TextButton(enabled = !busy, onClick = {
                        vm.saveDay(date, signs.filterValues { it }.keys, mucus, selected.toMap(), onClose)
                    }) { Text("Speichern", fontWeight = FontWeight.SemiBold) }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(Modifier.widthIn(max = 760.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                // ─── Periode ───
                InfoCard(title = "🌺 Periode") {
                    val inPeriod = state.periods.any { p ->
                        !date.isBefore(p.start) && !date.isAfter(
                            state.engine.effectiveEnd(p, state.today, state.stats?.periodLength ?: 5)
                        )
                    }
                    Text(
                        if (inPeriod) "An diesem Tag ist Periode eingetragen." else "An diesem Tag ist keine Periode eingetragen.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val actions = vm.periodActions(date, state)
                    if (actions.isEmpty()) {
                        Text("Für zukünftige Tage kann keine Periode eingetragen werden.", style = MaterialTheme.typography.bodySmall)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        actions.forEach { a ->
                            OutlinedButton(onClick = a.run) { Text(a.label) }
                        }
                    }
                    Text(
                        "Änderungen an der Periode werden sofort an Bloom gesendet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (state.settings.showFertility) {
                    // ─── Eisprung ───
                    InfoCard(title = "🌸 Eisprung bemerkt?") {
                        Text(
                            "Wähle, woran du den Eisprung bemerkt hast. Das macht die Vorhersage genauer.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OvulationSign.entries.forEach { s ->
                                FilterChip(
                                    selected = signs[s] == true,
                                    onClick = { signs[s] = signs[s] != true },
                                    label = { Text(s.label) }
                                )
                            }
                        }
                        if (signs[OvulationSign.LH_TEST] == true) {
                            Text(
                                "Ein positiver Ovulationstest wird als Eisprung am Folgetag gewertet.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    // ─── Zervixschleim ───
                    InfoCard(title = "💧 Zervixschleim") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = mucus == null, onClick = { mucus = null }, label = { Text("Keine Angabe") })
                            Mucus.entries.forEach { m ->
                                FilterChip(selected = mucus == m, onClick = { mucus = m }, label = { Text(m.label) })
                            }
                        }
                        Text(
                            "Wässriger oder spinnbarer Schleim zeigt fruchtbare Tage an. Der letzte Tag davon gilt als Eisprung, wenn du keinen eigenen Eintrag machst.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // ─── Symptome ───
                InfoCard(title = "📝 Symptome") {
                    SymptomCategory.entries.forEach { cat ->
                        val names = SymptomCatalog.builtIn.filter { it.category == cat }.map { it.name } +
                            state.customSymptoms.filter { it.category == cat }.map { it.name }
                        if (names.isEmpty()) return@forEach
                        Text(cat.label, style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            names.distinct().forEach { name ->
                                FilterChip(
                                    selected = selected.containsKey(name),
                                    onClick = {
                                        if (selected.containsKey(name)) selected.remove(name) else selected[name] = cat to 3
                                    },
                                    label = { Text(name) }
                                )
                            }
                        }
                    }
                }

                if (selected.isNotEmpty()) {
                    InfoCard(title = "Stärke") {
                        selected.keys.sorted().forEach { name ->
                            val (cat, sev) = selected[name]!!
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(name, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                    Text(SymptomCatalog.severityLabels[sev], color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    for (i in 1..5) {
                                        FilterChip(
                                            selected = sev == i,
                                            onClick = { selected[name] = cat to i },
                                            label = { Text("$i") }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ─── Eigene Symptome ───
                InfoCard(title = "Eigenes Symptom anlegen") {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SymptomCategory.entries.forEach { c ->
                            FilterChip(selected = newCategory == c, onClick = { newCategory = c }, label = { Text(c.label) })
                        }
                    }
                    Button(
                        enabled = newName.isNotBlank(),
                        onClick = {
                            val n = newName.trim()
                            vm.addCustomSymptom(n, newCategory)
                            selected[n] = newCategory to 3
                            newName = ""
                        }
                    ) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                        Text("  Anlegen und auswählen")
                    }
                    if (state.customSymptoms.isNotEmpty()) {
                        Text("Deine eigenen Symptome (zum Entfernen antippen):", style = MaterialTheme.typography.bodySmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            state.customSymptoms.forEach { c ->
                                InputChip(
                                    selected = false,
                                    onClick = { vm.deleteCustomSymptom(c.name) },
                                    label = { Text(c.name) },
                                    trailingIcon = { Icon(Icons.Filled.Close, "Entfernen", Modifier.size(16.dp)) }
                                )
                            }
                        }
                    }
                }

                Button(
                    enabled = !busy,
                    onClick = {
                        vm.saveDay(date, signs.filterValues { it }.keys, mucus, selected.toMap(), onClose)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (busy) "Wird gespeichert …" else "Speichern") }
            }
        }
    }
}
