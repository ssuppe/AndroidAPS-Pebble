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
        aapsLogger.debug(LTag.PEBBLE, "TargetUuidProvider: Saving Watchface UUID string: {}", uuidString)
        prefs.edit().putString(PREF_PEBBLE_UUID, uuidString).apply()
    }

    fun getControllerUuid(): UUID {
        val uuidString = prefs.getString(PREF_PEBBLE_CONTROLLER_UUID, DEFAULT_CONTROLLER_UUID_STRING)
        return try {
            val uuid = UUID.fromString(uuidString)
            aapsLogger.debug(LTag.PEBBLE, "TargetUuidProvider: Returning Controller UUID: {}", uuid)
            uuid
        } catch (e: Exception) {
            aapsLogger.warn(LTag.PEBBLE, "TargetUuidProvider: Malformed Controller UUID in prefs: {}. Returning default.", uuidString)
            UUID.fromString(DEFAULT_CONTROLLER_UUID_STRING)
        }
    }

    fun saveControllerUuid(uuidString: String) {
        aapsLogger.debug(LTag.PEBBLE, "TargetUuidProvider: Saving Controller UUID string: {}", uuidString)
        prefs.edit().putString(PREF_PEBBLE_CONTROLLER_UUID, uuidString).apply()
    }

    companion object {
        const val PREF_PEBBLE_UUID = "pebble_app_uuid"
        const val PREF_PEBBLE_CONTROLLER_UUID = "pebble_controller_uuid"
        private const val DEFAULT_UUID_STRING = "54D3008F-E144-4712-B201-24BC515C40BA"
        private const val DEFAULT_CONTROLLER_UUID_STRING = "A1B2C3D4-E5F6-7A8B-9C0D-1E2F3A4B5C6D"
    }
}