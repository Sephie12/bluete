package de.sephie.bluete.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.sephie.bluete.data.ThemeMode
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(state: UiState, vm: AppViewModel, lockAvailable: Boolean, showMessage: (String) -> Unit) {
    val s = state.settings
    var confirmDelete by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.export(uri, showMessage)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.import(uri, showMessage)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(Modifier.widthIn(max = 760.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Einstellungen", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)

            InfoCard(title = "Anzeige") {
                SwitchRow("Fruchtbarkeit und Eisprung anzeigen", s.showFertility) { v -> vm.updateSettings { it.copy(showFertility = v) } }
                SwitchRow("Tipps im Dashboard anzeigen", s.showTips) { v -> vm.updateSettings { it.copy(showTips = v) } }
                Text("Design", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { m ->
                        FilterChip(selected = s.themeMode == m, onClick = { vm.updateSettings { it.copy(themeMode = m) } }, label = { Text(m.label) })
                    }
                }
            }

            InfoCard(title = "Startwerte") {
                Text(
                    "Gelten nur, solange noch keine eigenen Zyklen vorliegen. Danach rechnet die App mit deinen Daten.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Stepper("Zykluslänge", s.defaultCycleLength, 20..45) { v -> vm.updateSettings { it.copy(defaultCycleLength = v) } }
                Stepper("Periodenlänge", s.defaultPeriodLength, 2..10) { v -> vm.updateSettings { it.copy(defaultPeriodLength = v) } }
            }

            InfoCard(title = "Datenschutz") {
                SwitchRow(
                    if (lockAvailable) "App-Sperre (Fingerabdruck, Gesicht oder PIN)" else "App-Sperre (keine Bildschirmsperre eingerichtet)",
                    s.appLock && lockAvailable,
                    enabled = lockAvailable
                ) { v -> vm.updateSettings { it.copy(appLock = v) } }
                Text(
                    "Alle Daten bleiben auf diesem Tablet. Die App hat keine Internet-Berechtigung und ist von Google-Backups ausgeschlossen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            InfoCard(title = "Sicherung & Import") {
                Text("Speichere regelmäßig eine Sicherung, z. B. auf deinem Heimserver. Ohne Sicherung sind die Daten weg, wenn die App gelöscht wird.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { exportLauncher.launch("bluete-sicherung-${LocalDate.now()}.json") }) { Text("Sicherung speichern") }
                    FilledTonalButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
                        Text("Sicherung oder Bloom-Export laden")
                    }
                }
                Text(
                    "Bloom-Daten übernehmen: In Bloom unter Settings den Datenexport als JSON herunterladen und die Datei hier laden. " +
                        "Perioden, Symptome und Zervixschleim werden übernommen und die Symptome ins Deutsche übersetzt. Vorhandene Einträge bleiben erhalten.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            InfoCard(title = "Alle Daten löschen") {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Alles löschen …") }
            }

            Text(
                "Blüte 1.0 · nur für den Eigenbedarf · Vorhersagen sind Schätzungen und kein Verhütungsmittel.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Wirklich alles löschen?") },
            text = { Text("Alle Perioden, Eisprünge, Schleim-Einträge und Symptome werden gelöscht. Das lässt sich nicht rückgängig machen. Speichere vorher eine Sicherung.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteAll { showMessage("Alle Daten wurden gelöscht.") }
                }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } }
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        OutlinedButton(onClick = { onChange((value - 1).coerceIn(range)) }) { Text("−") }
        Text("  ${tage(value)}  ", fontWeight = FontWeight.Medium)
        OutlinedButton(onClick = { onChange((value + 1).coerceIn(range)) }) { Text("+") }
    }
}
