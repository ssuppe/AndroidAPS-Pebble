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

    @Test
    fun testEnrichedDataCompilation() {
        val data = EnrichedData(
            bg = 120.0,
            trend = 1,
            time = 123456789000L,
            iob = "0.32 U",
            cob = "0g",
            basal = "0.90",
            iobDetail = "(0.02|0.31)",
            delta = "+3",
            avgDelta = "+5",
            history = ByteArray(36) { 0 },
            lowTarget = 70,
            highTarget = 180,
            units = 0
        )
        assertEquals("0.32 U", data.iob)
        assertEquals("0g", data.cob)
        assertEquals("0.90", data.basal)
        assertEquals("(0.02|0.31)", data.iobDetail)
        assertEquals("+3", data.delta)
        assertEquals("+5", data.avgDelta)
        assertEquals(36, data.history?.size)
        assertEquals(70, data.lowTarget)
        assertEquals(180, data.highTarget)
        assertEquals(0, data.units)
    }

    @Test
    fun testMap_populatesAllExpandedKeys_withCorrectFormats() {
        val historyBytes = ByteArray(36) { idx -> idx.toByte() }
        val data = EnrichedData(
            bg = 120.0,
            trend = 1,
            time = 123456789000L,
            iob = "0.32 U",
            cob = "0g",
            basal = "0.90",
            iobDetail = "(0.02|0.31)",
            delta = "+3",
            avgDelta = "+5",
            history = historyBytes,
            lowTarget = 70,
            highTarget = 180,
            units = 0
        )

        val dict = mapper.map(data)

        assertEquals(120, dict.getInteger(PebbleKeys.BG))
        assertEquals(1L, dict.getInteger(PebbleKeys.TREND))
        assertEquals(123456789L, dict.getInteger(PebbleKeys.TIME))
        assertEquals("0.32 U", dict.getString(PebbleKeys.IOB))
        assertEquals("0g", dict.getString(PebbleKeys.COB))
        assertEquals("0.90", dict.getString(PebbleKeys.BASAL))
        assertEquals("(0.02|0.31)", dict.getString(PebbleKeys.IOB_DETAIL))
        assertEquals("+3", dict.getString(PebbleKeys.DELTA))
        assertEquals("+5", dict.getString(PebbleKeys.AVG_DELTA))
        assertEquals(70, dict.getInteger(PebbleKeys.LOW_TARGET))
        assertEquals(180, dict.getInteger(PebbleKeys.HIGH_TARGET))
        assertEquals(0, dict.getInteger(PebbleKeys.UNITS))
        
        val actualHistory = dict.getBytes(PebbleKeys.GLUCOSE_HISTORY)
        org.junit.jupiter.api.Assertions.assertNotNull(actualHistory)
        org.junit.jupiter.api.Assertions.assertArrayEquals(historyBytes, actualHistory)
    }

    @Test
    fun testMap_handlesNullFields_gracefully() {
        val data = EnrichedData(
            bg = null,
            trend = null,
            time = 123456789000L,
            iob = null,
            cob = null,
            basal = null,
            iobDetail = null,
            delta = null,
            avgDelta = null,
            history = null,
            lowTarget = null,
            highTarget = null,
            units = null
        )

        val dict = mapper.map(data)

        assertNull(dict.getInteger(PebbleKeys.BG))
        assertNull(dict.getInteger(PebbleKeys.TREND))
        assertEquals(123456789L, dict.getInteger(PebbleKeys.TIME))
        assertNull(dict.getString(PebbleKeys.IOB))
        assertNull(dict.getString(PebbleKeys.COB))
        assertNull(dict.getString(PebbleKeys.BASAL))
        assertNull(dict.getString(PebbleKeys.IOB_DETAIL))
        assertNull(dict.getString(PebbleKeys.DELTA))
        assertNull(dict.getString(PebbleKeys.AVG_DELTA))
        assertNull(dict.getBytes(PebbleKeys.GLUCOSE_HISTORY))
        assertNull(dict.getInteger(PebbleKeys.LOW_TARGET))
        assertNull(dict.getInteger(PebbleKeys.HIGH_TARGET))
        assertNull(dict.getInteger(PebbleKeys.UNITS))
    }
}
