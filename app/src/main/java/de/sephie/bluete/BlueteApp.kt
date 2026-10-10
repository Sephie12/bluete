package de.sephie.bluete

import android.app.Application
import de.sephie.bluete.data.AppDatabase
import de.sephie.bluete.data.SettingsStore

class BlueteApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.create(this) }
    val settings: SettingsStore by lazy { SettingsStore(this) }
}
