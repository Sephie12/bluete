package de.sephie.bluete

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.sephie.bluete.ui.AppViewModel
import de.sephie.bluete.ui.CalendarScreen
import de.sephie.bluete.ui.DashboardScreen
import de.sephie.bluete.ui.DayEditor
import de.sephie.bluete.ui.SettingsScreen
import de.sephie.bluete.ui.StatsScreen
import de.sephie.bluete.ui.theme.BlueteTheme
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class Tab(val label: String, val icon: ImageVector) {
    UEBERSICHT("Übersicht", Icons.Filled.Home),
    KALENDER("Kalender", Icons.Filled.DateRange),
    STATISTIK("Statistik", Icons.AutoMirrored.Filled.List),
    EINSTELLUNGEN("Einstellungen", Icons.Filled.Settings)
}

class MainActivity : FragmentActivity() {

    private var locked by mutableStateOf(true)
    private val authenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    private fun lockAvailable(): Boolean =
        BiometricManager.from(this).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    private fun lockEnabled(): Boolean =
        (application as BlueteApp).settings.state.value.appLock && lockAvailable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        locked = lockEnabled()
        setContent {
            val vm: AppViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()
            BlueteTheme(settings.themeMode) {
                LaunchedEffect(settings.appLock) {
                    if (settings.appLock) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (locked && settings.appLock) {
                        LockScreen(onUnlock = ::authenticate)
                        LaunchedEffect(Unit) { authenticate() }
                    } else {
                        AppContent(vm, lockAvailable())
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Datum aktualisieren, falls die App über Nacht offen war
        androidx.lifecycle.ViewModelProvider(this)[AppViewModel::class.java].refreshToday()
    }

    override fun onStop() {
        super.onStop()
        if (lockEnabled()) locked = true
    }

    private fun authenticate() {
        if (!lockAvailable()) {
            locked = false
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    locked = false
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Blüte entsperren")
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(info)
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🌸", style = MaterialTheme.typography.displayLarge)
        Text("Blüte ist gesperrt", style = MaterialTheme.typography.titleLarge)
        Button(onClick = onUnlock, modifier = Modifier.padding(top = 24.dp)) { Text("Entsperren") }
    }
}

@Composable
private fun AppContent(vm: AppViewModel, lockAvailable: Boolean) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.UEBERSICHT) }
    var editDay by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }

    if (!state.loaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val day = editDay
    if (day != null) {
        BackHandler { editDay = null }
        DayEditor(LocalDate.parse(day), state, vm, onClose = { editDay = null })
        return
    }

    val openDay: (LocalDate) -> Unit = { editDay = it.toString() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (!wide) {
                    NavigationBar {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = tab == t,
                                onClick = { tab = t },
                                icon = { Icon(t.icon, null) },
                                label = { Text(t.label) }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Row(Modifier.padding(padding).fillMaxSize()) {
                if (wide) {
                    NavigationRail(header = { Text("🌸", style = MaterialTheme.typography.headlineMedium) }) {
                        Tab.entries.forEach { t ->
                            NavigationRailItem(
                                selected = tab == t,
                                onClick = { tab = t },
                                icon = { Icon(t.icon, null) },
                                label = { Text(t.label) }
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxSize()) {
                    when (tab) {
                        Tab.UEBERSICHT -> DashboardScreen(state, vm, openDay)
                        Tab.KALENDER -> CalendarScreen(state, openDay)
                        Tab.STATISTIK -> StatsScreen(state)
                        Tab.EINSTELLUNGEN -> SettingsScreen(state, vm, lockAvailable, showMessage)
                    }
                }
            }
        }
    }
}
