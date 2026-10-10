package de.sephie.bluete.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode(val label: String) { SYSTEM("Wie System"), HELL("Hell"), DUNKEL("Dunkel") }

data class AppSettings(
    /** Vorgaben, solange noch keine eigenen Zyklen vorliegen. */
    val defaultCycleLength: Int = 28,
    val defaultPeriodLength: Int = 5,
    val showFertility: Boolean = true,
    val showTips: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appLock: Boolean = false
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("einstellungen", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state

    private fun load() = AppSettings(
        defaultCycleLength = prefs.getInt("zykluslaenge", 28),
        defaultPeriodLength = prefs.getInt("periodenlaenge", 5),
        showFertility = prefs.getBoolean("fruchtbarkeit", true),
        showTips = prefs.getBoolean("tipps", true),
        themeMode = runCatching { ThemeMode.valueOf(prefs.getString("design", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM),
        appLock = prefs.getBoolean("sperre", false)
    )

    fun update(change: (AppSettings) -> AppSettings) {
        val s = change(_state.value)
        prefs.edit()
            .putInt("zykluslaenge", s.defaultCycleLength)
            .putInt("periodenlaenge", s.defaultPeriodLength)
            .putBoolean("fruchtbarkeit", s.showFertility)
            .putBoolean("tipps", s.showTips)
            .putString("design", s.themeMode.name)
            .putBoolean("sperre", s.appLock)
            .apply()
        _state.value = s
    }
}
