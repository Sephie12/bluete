package de.sephie.bluete

import android.app.Application
import de.sephie.bluete.data.Repository
import de.sephie.bluete.data.SettingsStore

class BlueteApp : Application() {
    val settings: SettingsStore by lazy { SettingsStore(this) }
    val repository: Repository by lazy { Repository(this, settings) }
}
