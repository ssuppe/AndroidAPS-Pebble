package app.aaps.plugins.pebble

import app.aaps.plugins.pebble.data.EnrichedData
import com.getpebble.android.kit.util.PebbleDictionary
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PebbleDataMapper @Inject constructor(
    private val aapsLogger: AAPSLogger
) {
    fun map(data: EnrichedData): PebbleDictionary {
        aapsLogger.debug(LTag.PEBBLE, "PebbleDataMapper: Mapping data: {}", data)
        val dict = PebbleDictionary()
        data.bg?.let { 
            dict.addInt32(PebbleKeys.BG, it.toInt())
            aapsLogger.debug(LTag.PEBBLE, "PebbleDataMapper: Added BG: {}", it.toInt())
        }
        data.trend?.let { 
            dict.addInt32(PebbleKeys.TREND, it) 
            aapsLogger.debug(LTag.PEBBLE, "PebbleDataMapper: Added Trend: {}", it)
        }
        data.iob?.let { 
            val scaled = (it * 100).toInt()
            dict.addInt32(PebbleKeys.IOB, scaled)
            aapsLogger.debug(LTag.PEBBLE, "PebbleDataMapper: Added IOB (scaled): {}", scaled)
        }
        data.cob?.let { 
            val scaled = (it * 100).toInt()
            dict.addInt32(PebbleKeys.COB, scaled)
            aapsLogger.debug(LTag.PEBBLE, "PebbleDataMapper: Added COB (scaled): {}", scaled)
        }
        dict.addInt32(PebbleKeys.TIME, (data.time / 1000).toInt())
        aapsLogger.debug(LTag.PEBBLE, "PebbleDataMapper: Added Time (seconds): {}", (data.time / 1000).toInt())
        return dict
    }
}
