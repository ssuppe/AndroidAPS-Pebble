package app.aaps.plugins.pebble

import app.aaps.plugins.pebble.data.EnrichedData
import com.getpebble.android.kit.util.PebbleDictionary
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PebbleDataMapper @Inject constructor() {
    fun map(data: EnrichedData): PebbleDictionary {
        val dict = PebbleDictionary()
        data.bg?.let { dict.addInt32(PebbleKeys.BG, it.toInt()) }
        data.trend?.let { dict.addInt32(PebbleKeys.TREND, it) }
        data.iob?.let { dict.addInt32(PebbleKeys.IOB, (it * 100).toInt()) }
        data.cob?.let { dict.addInt32(PebbleKeys.COB, (it * 100).toInt()) }
        dict.addInt32(PebbleKeys.TIME, data.time.toInt())
        return dict
    }
}
