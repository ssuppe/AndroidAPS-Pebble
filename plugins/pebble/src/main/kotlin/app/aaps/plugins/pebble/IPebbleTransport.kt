package app.aaps.plugins.pebble

import android.content.Context
import com.getpebble.android.kit.util.PebbleDictionary
import java.util.UUID

interface IPebbleTransport {
    fun sendData(context: Context, uuid: UUID, data: PebbleDictionary)
}
