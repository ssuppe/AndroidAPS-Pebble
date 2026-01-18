package app.aaps.plugins.pebble

import app.aaps.plugins.pebble.data.EnrichedData
import com.getpebble.android.kit.util.PebbleDictionary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import app.aaps.core.interfaces.logging.AAPSLogger
import org.mockito.kotlin.mock

class PebbleDataMapperTest {

    private val logger: AAPSLogger = mock()
    private val mapper = PebbleDataMapper(logger)

    @Test
    fun testMap_populatesBgTrendIobCob() {
        val data = EnrichedData(
            bg = 120.0,
            trend = 1,
            iob = 1.5,
            cob = 20.0,
            time = 123456789000L // 123456789 seconds in millis
        )

        val dict = mapper.map(data)

        assertEquals(120, dict.getInteger(PebbleKeys.BG))
        assertEquals(1.toLong(), dict.getInteger(PebbleKeys.TREND))
        assertEquals(150.toLong(), dict.getInteger(PebbleKeys.IOB))
        assertEquals(2000.toLong(), dict.getInteger(PebbleKeys.COB))
        // Verify time is now in seconds (Step 2 update)
        assertEquals(123456789L, dict.getInteger(PebbleKeys.TIME))
    }

    @Test
    fun testMap_scalesFloatingPointValues() {
        val data = EnrichedData(
            bg = 100.0,
            trend = 0,
            iob = 1.234,
            cob = 10.5,
            time = 123456789000L
        )

        val dict = mapper.map(data)

        assertEquals(123.toLong(), dict.getInteger(PebbleKeys.IOB))
        assertEquals(1050.toLong(), dict.getInteger(PebbleKeys.COB))
    }

    @Test
    fun testMap_handlesNullValues() {
        val data = EnrichedData(
            bg = null,
            trend = null,
            iob = null,
            cob = null,
            time = 123456789000L
        )

        val dict = mapper.map(data)

        assertNull(dict.getInteger(PebbleKeys.BG))
        assertNull(dict.getInteger(PebbleKeys.TREND))
        assertNull(dict.getInteger(PebbleKeys.IOB))
        assertNull(dict.getInteger(PebbleKeys.COB))
        assertEquals(123456789L, dict.getInteger(PebbleKeys.TIME))
    }
}
