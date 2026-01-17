package app.aaps.plugins.pebble

import android.content.SharedPreferences
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TargetUuidProvider @Inject constructor(private val sharedPreferences: SharedPreferences) {

    companion object {
        const val PREF_KEY = "pebble_plugin_target_uuid"
        const val DEFAULT_UUID = "54D3008F-0E46-46AC-9634-93D0D7130000"
    }

    fun getTargetUuid(): UUID {
        val uuidString = sharedPreferences.getString(PREF_KEY, null) ?: DEFAULT_UUID
        return try {
            UUID.fromString(uuidString)
        } catch (e: IllegalArgumentException) {
            UUID.fromString(DEFAULT_UUID)
        }
    }

    fun saveTargetUuid(uuidString: String) {
        sharedPreferences.edit().putString(PREF_KEY, uuidString).apply()
    }
}