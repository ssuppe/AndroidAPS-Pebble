package app.aaps.plugins.pebble

import android.content.SharedPreferences
import java.util.UUID
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TargetUuidProvider @Inject constructor(
    private val prefs: SharedPreferences,
    private val aapsLogger: AAPSLogger
) {
    fun getTargetUuid(): UUID {
        val uuidString = prefs.getString(PREF_PEBBLE_UUID, DEFAULT_UUID_STRING)
        return try {
            val uuid = UUID.fromString(uuidString)
            aapsLogger.debug(LTag.PEBBLE, "TargetUuidProvider: Returning UUID: {}", uuid)
            uuid
        } catch (e: Exception) {
            aapsLogger.warn(LTag.PEBBLE, "TargetUuidProvider: Malformed UUID in prefs: {}. Returning default.", uuidString)
            UUID.fromString(DEFAULT_UUID_STRING)
        }
    }

    fun saveTargetUuid(uuidString: String) {
        aapsLogger.debug(LTag.PEBBLE, "TargetUuidProvider: Saving UUID string: {}", uuidString)
        prefs.edit().putString(PREF_PEBBLE_UUID, uuidString).apply()
    }

    companion object {
        private const val PREF_PEBBLE_UUID = "pebble_app_uuid"
        private const val DEFAULT_UUID_STRING = "54D3008F-E144-4712-B201-24BC515C40BA"
    }
}