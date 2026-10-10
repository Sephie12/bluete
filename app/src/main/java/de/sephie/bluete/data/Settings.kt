package de.sephie.bluete.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode(val label: String) { SYSTEM("Wie System"), HELL("Hell"), DUNKEL("Dunkel") }

const val DEFAULT_SERVER = "http://192.168.2.150:8080"

data class AppSettings(
    val serverUrl: String = DEFAULT_SERVER,
    /** Anmelde-Token der Blüte-API; leer = nicht angemeldet. */
    val token: String = "",
    val email: String = "",
    val showFertility: Boolean = true,
    val showTips: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appLock: Boolean = false
) {
    val loggedIn: Boolean get() = token.isNotEmpty()
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("einstellungen", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state

    private fun load() = AppSettings(
        serverUrl = prefs.getString("server", DEFAULT_SERVER) ?: DEFAULT_SERVER,
        token = prefs.getString("token", "") ?: "",
        email = prefs.getString("email", "") ?: "",
        showFertility = prefs.getBoolean("fruchtbarkeit", true),
        showTips = prefs.getBoolean("tipps", true),
        themeMode = runCatching { ThemeMode.valueOf(prefs.getString("design", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM),
        appLock = prefs.getBoolean("sperre", false)
    )

    @Synchronized
    fun update(change: (AppSettings) -> AppSettings) {
        val s = change(_state.value)
        prefs.edit()
            .putString("server", s.serverUrl)
            .putString("token", s.token)
            .putString("email", s.email)
            .putBoolean("fruchtbarkeit", s.showFertility)
            .putBoolean("tipps", s.showTips)
            .putString("design", s.themeMode.name)
            .putBoolean("sperre", s.appLock)
            .apply()
        _state.value = s
    }
}
