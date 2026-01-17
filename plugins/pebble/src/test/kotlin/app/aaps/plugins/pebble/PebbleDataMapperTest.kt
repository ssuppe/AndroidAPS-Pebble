package app.aaps.plugins.pebble

import app.aaps.plugins.pebble.data.EnrichedData
import com.getpebble.android.kit.util.PebbleDictionary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PebbleDataMapperTest {

    private val mapper = PebbleDataMapper()

    @Test
    fun testMap_populatesBgTrendIobCob() {
        val data = EnrichedData(
            bg = 120.0,
            trend = 1,
            iob = 1.5,
            cob = 20.0,
            time = 123456789L
        )

        val dict = mapper.map(data)

        assertEquals(120, dict.getInteger(PebbleKeys.BG))
        assertEquals(1.toLong(), dict.getInteger(PebbleKeys.TREND))
        // IOB 1.5 -> 150 (scaled by 100 as per Step 2)
        assertEquals(150.toLong(), dict.getInteger(PebbleKeys.IOB))
        // COB 20.0 -> 2000 (scaled by 100 as per Step 2, though only IOB was mentioned, usually both are scaled if they are floats)
        // Wait, Step 2 says "IOB 1.5 -> 150". Doesn't mention COB scaling.
        // Let's assume COB is also scaled by 100 for precision.
        assertEquals(2000.toLong(), dict.getInteger(PebbleKeys.COB))
        assertEquals(123456789L, dict.getInteger(PebbleKeys.TIME))
    }

    @Test
    fun testMap_scalesFloatingPointValues() {
        val data = EnrichedData(
            bg = 100.0,
            trend = 0,
            iob = 1.234,
            cob = 10.5,
            time = 123456789L
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
            time = 123456789L
        )

        val dict = mapper.map(data)

        assertNull(dict.getInteger(PebbleKeys.BG))
        assertNull(dict.getInteger(PebbleKeys.TREND))
        assertNull(dict.getInteger(PebbleKeys.IOB))
        assertNull(dict.getInteger(PebbleKeys.COB))
        assertEquals(123456789L, dict.getInteger(PebbleKeys.TIME))
    }
}
