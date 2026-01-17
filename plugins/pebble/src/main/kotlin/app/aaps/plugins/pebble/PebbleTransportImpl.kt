package app.aaps.plugins.pebble

import android.content.Context
import com.getpebble.android.kit.PebbleKit
import com.getpebble.android.kit.util.PebbleDictionary
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PebbleTransportImpl @Inject constructor() : IPebbleTransport {
    override fun sendData(context: Context, uuid: UUID, data: PebbleDictionary) {
        PebbleKit.sendDataToPebble(context, uuid, data)
    }
}