package app.aaps.plugins.pebble

import android.content.SharedPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.util.UUID

class TargetUuidProviderTest {

    private val sharedPreferences = mock(SharedPreferences::class.java)
    private val provider = TargetUuidProvider(sharedPreferences)
    private val defaultUuidString = "54D3008F-0E46-46AC-9634-93D0D7130000"
    private val defaultUuid = UUID.fromString(defaultUuidString)

    @Test
    fun testDefaultUuid_isReturned_whenPreferenceEmpty() {
        `when`(sharedPreferences.getString("pebble_plugin_target_uuid", null)).thenReturn(null)
        assertEquals(defaultUuid, provider.getTargetUuid())
    }

    @Test
    fun testParsedUuid_isReturned_whenValid() {
        val validUuidString = "EC7EE5C6-8DDF-4089-AA84-C3396A11CC95"
        `when`(sharedPreferences.getString("pebble_plugin_target_uuid", null)).thenReturn(validUuidString)
        assertEquals(UUID.fromString(validUuidString), provider.getTargetUuid())
    }

    @Test
    fun testDefaultUuid_isReturned_whenInvalid() {
        `when`(sharedPreferences.getString("pebble_plugin_target_uuid", null)).thenReturn("invalid-uuid")
        assertEquals(defaultUuid, provider.getTargetUuid())
    }
}
