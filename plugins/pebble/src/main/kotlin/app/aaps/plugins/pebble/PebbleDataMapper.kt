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
        
        // 1. BG, Trend, and Time
        data.bg?.let { 
            dict.addInt32(PebbleKeys.BG, it.toInt())
            aapsLogger.debug(LTag.PEBBLE, "PebbleDataMapper: Added BG: {}", it.toInt())
        }
        data.trend?.let { 
            dict.addInt32(PebbleKeys.TREND, it) 
            aapsLogger.debug(LTag.PEBBLE, "PebbleDataMapper: Added Trend: {}", it)
        }
        dict.addInt32(PebbleKeys.TIME, (data.time / 1000).toInt())
        aapsLogger.debug(LTag.PEBBLE, "PebbleDataMapper: Added Time (seconds): {}", (data.time / 1000).toInt())
        
        // 2. Active Treatments (IOB/COB/Basal)
        data.iob?.let { 
            dict.addString(PebbleKeys.IOB, it) 
            aapsLogger.debug(LTag.PEBBLE, "PebbleDataMapper: Added IOB: {}", it)
        }
        data.cob?.let { 
            dict.addString(PebbleKeys.COB, it) 
            aapsLogger.debug(LTag.PEBBLE, "PebbleDataMapper: Added COB: {}", it)
        }
        data.basal?.let { 
            dict.addString(PebbleKeys.BASAL, it) 
            aapsLogger.debug(LTag.PEBBLE, "PebbleDataMapper: Added Basal: {}", it)
        }
        data.iobDetail?.let { 
            dict.addString(PebbleKeys.IOB_DETAIL, it) 
            aapsLogger.debug(LTag.PEBBLE, "PebbleDataMapper: Added IOB Detail: {}", it)
        }
        
        // 3. Deltas
        data.delta?.let { 
            dict.addString(PebbleKeys.DELTA, it) 
            aapsLogger.debug(LTag.PEBBLE, "PebbleDataMapper: Added Delta: {}", it)
        }
        data.avgDelta?.let { 
            dict.addString(PebbleKeys.AVG_DELTA, it) 
            aapsLogger.debug(LTag.PEBBLE, "PebbleDataMapper: Added Avg Delta: {}", it)
        }
        
        // 4. Targets & Units
        data.lowTarget?.let { 
            dict.addInt32(PebbleKeys.LOW_TARGET, it) 
            aapsLogger.debug(LTag.PEBBLE, "PebbleDataMapper: Added Low Target: {}", it)
        }
        data.highTarget?.let { 
            dict.addInt32(PebbleKeys.HIGH_TARGET, it) 
            aapsLogger.debug(LTag.PEBBLE, "PebbleDataMapper: Added High Target: {}", it)
        }
        data.units?.let { 
            dict.addInt32(PebbleKeys.UNITS, it) 
            aapsLogger.debug(LTag.PEBBLE, "PebbleDataMapper: Added Units: {}", it)
        }
        
        // 5. Glucose History
        data.history?.let { 
            dict.addBytes(PebbleKeys.GLUCOSE_HISTORY, it) 
            aapsLogger.debug(LTag.PEBBLE, "PebbleDataMapper: Added History bytes")
        }

        return dict
    }
}
