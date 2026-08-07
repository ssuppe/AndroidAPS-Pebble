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
    fun testMap_populatesBgTrendTime_withCorrectTrendArrow() {
        val data = EnrichedData(
            bg = 120.0,
            trend = 1,
            time = 123456789000L // 123456789 seconds in millis
        )

        val dict = mapper.map(data)

        assertEquals(120, dict.getInteger(PebbleKeys.BG))
        assertEquals(1.toLong(), dict.getInteger(PebbleKeys.TREND))
        assertNull(dict.getInteger(PebbleKeys.IOB))
        assertNull(dict.getInteger(PebbleKeys.COB))
        assertEquals(123456789L, dict.getInteger(PebbleKeys.TIME))
    }

    @Test
    fun testMap_handlesNullValues() {
        val data = EnrichedData(
            bg = null,
            trend = null,
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
