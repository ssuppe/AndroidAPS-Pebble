package app.aaps.plugins.pebble

import android.content.BroadcastReceiver
import android.content.Context
import com.getpebble.android.kit.util.PebbleDictionary
import java.util.UUID

interface IPebbleTransport {
    fun sendData(context: Context, uuid: UUID, data: PebbleDictionary)
    fun registerAckHandler(context: Context, uuid: UUID, onAck: (Int) -> Unit): BroadcastReceiver
    fun registerNackHandler(context: Context, uuid: UUID, onNack: (Int) -> Unit): BroadcastReceiver
    fun unregisterReceiver(context: Context, receiver: BroadcastReceiver)
}
