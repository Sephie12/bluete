package de.sephie.bluete.ui

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(state: UiState, vm: AppViewModel, lockAvailable: Boolean) {
    val s = state.settings
    var confirmLogout by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(Modifier.widthIn(max = 760.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Einstellungen", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)

            InfoCard(title = "Bloom-Server") {
                Text("Angemeldet als ${state.userName.ifBlank { s.email }}" + if (state.userName.isNotBlank()) " (${s.email})" else "")
                Text("Server: ${s.serverUrl}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(syncText(state.sync), color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { vm.refresh(showResult = true) }) { Text("Jetzt aktualisieren") }
                    OutlinedButton(onClick = { confirmLogout = true }) { Text("Abmelden") }
                }
                Text(
                    "Zykluslänge und Periodenlänge für den Anfang stellst du in Bloom unter Settings ein. Sobald genug Zyklen eingetragen sind, rechnet die App mit deinen echten Werten.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

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

            InfoCard(title = "Datenschutz") {
                SwitchRow(
                    if (lockAvailable) "App-Sperre (Fingerabdruck, Gesicht oder PIN)" else "App-Sperre (keine Bildschirmsperre eingerichtet)",
                    s.appLock && lockAvailable,
                    enabled = lockAvailable
                ) { v -> vm.updateSettings { it.copy(appLock = v) } }
                Text(
                    "Deine Daten liegen in Bloom auf deinem Server und sind dort mitgesichert. Auf dem Tablet liegt nur eine Kopie des letzten Stands zum Anzeigen ohne Heimnetz. Sie ist von Google-Backups ausgeschlossen und wird beim Abmelden gelöscht.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                "Blüte 2.0 · nur für den Eigenbedarf · Vorhersagen sind Schätzungen und kein Verhütungsmittel.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Abmelden?") },
            text = { Text("Deine Daten bleiben auf dem Server. Auf dem Tablet wird der gespeicherte Stand gelöscht.") },
            confirmButton = {
                TextButton(onClick = { confirmLogout = false; vm.logout() }) { Text("Abmelden") }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Abbrechen") } }
        )
    }
}

fun syncText(sync: de.sephie.bluete.data.SyncState): String {
    val last = sync.lastSync?.let {
        val t = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault())
        val fmt = java.time.format.DateTimeFormatter.ofPattern("d. MMM, HH:mm 'Uhr'", German)
        t.format(fmt)
    }
    return when {
        sync.refreshing -> "Wird aktualisiert …"
        sync.online && last != null -> "Verbunden · zuletzt aktualisiert $last"
        last != null -> "Nicht verbunden · Stand vom $last"
        else -> "Noch keine Daten geladen"
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
