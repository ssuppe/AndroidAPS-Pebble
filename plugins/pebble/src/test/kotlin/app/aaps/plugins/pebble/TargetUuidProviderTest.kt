package app.aaps.plugins.pebble

import android.content.SharedPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID
import app.aaps.core.interfaces.logging.AAPSLogger

class TargetUuidProviderTest {

    private val prefs: SharedPreferences = mock()
    private val logger: AAPSLogger = mock()
    private val provider = TargetUuidProvider(prefs, logger)

    @Test
    fun testDefaultUuid_isReturned_whenPreferenceEmpty() {
        whenever(prefs.getString(any(), any())).thenReturn("54D3008F-E144-4712-B201-24BC515C40BA")
        
        val uuid = provider.getTargetUuid()
        
        assertEquals(UUID.fromString("54D3008F-E144-4712-B201-24BC515C40BA"), uuid)
    }

    @Test
    fun testParsedUuid_isReturned_whenValid() {
        val validUuid = UUID.randomUUID().toString()
        whenever(prefs.getString(any(), any())).thenReturn(validUuid)
        
        val uuid = provider.getTargetUuid()
        
        assertEquals(UUID.fromString(validUuid), uuid)
    }

    @Test
    fun testDefaultUuid_isReturned_whenInvalid() {
        whenever(prefs.getString(any(), any())).thenReturn("invalid-uuid")
        
        val uuid = provider.getTargetUuid()
        
        assertEquals(UUID.fromString("54D3008F-E144-4712-B201-24BC515C40BA"), uuid)
    }

    @Test
    fun testDefaultControllerUuid_isReturned_whenPreferenceEmpty() {
        whenever(prefs.getString(org.mockito.kotlin.eq("pebble_controller_uuid"), org.mockito.kotlin.any())).thenReturn("A1B2C3D4-E5F6-7A8B-9C0D-1E2F3A4B5C6D")

        val uuid = provider.getControllerUuid()

        assertEquals(UUID.fromString("A1B2C3D4-E5F6-7A8B-9C0D-1E2F3A4B5C6D"), uuid)
    }

    @Test
    fun testCustomControllerUuid_isReturned_whenValid() {
        val validUuid = UUID.randomUUID().toString()
        whenever(prefs.getString(org.mockito.kotlin.eq("pebble_controller_uuid"), org.mockito.kotlin.any())).thenReturn(validUuid)

        val uuid = provider.getControllerUuid()

        assertEquals(UUID.fromString(validUuid), uuid)
    }

    @Test
    fun testDefaultControllerUuid_isReturned_whenInvalid() {
        whenever(prefs.getString(org.mockito.kotlin.eq("pebble_controller_uuid"), org.mockito.kotlin.any())).thenReturn("invalid-controller-uuid")

        val uuid = provider.getControllerUuid()

        assertEquals(UUID.fromString("A1B2C3D4-E5F6-7A8B-9C0D-1E2F3A4B5C6D"), uuid)
    }
}

