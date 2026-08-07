package app.aaps.plugins.pebble

import android.content.BroadcastReceiver
import android.content.Context
import com.getpebble.android.kit.PebbleKit
import com.getpebble.android.kit.util.PebbleDictionary
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PebbleTransportImpl @Inject constructor(
    private val aapsLogger: AAPSLogger
) : IPebbleTransport {
    override fun sendData(context: Context, uuid: UUID, data: PebbleDictionary) {
        aapsLogger.debug(LTag.PEBBLE, "PebbleTransportImpl: Calling PebbleKit.sendDataToPebble for UUID: {} with payload: {}", uuid, data.toJsonString())
        try {
            PebbleKit.sendDataToPebble(context, uuid, data)
            aapsLogger.debug(LTag.PEBBLE, "PebbleTransportImpl: PebbleKit.sendDataToPebble called successfully")
        } catch (e: Exception) {
            aapsLogger.error(LTag.PEBBLE, "PebbleTransportImpl: Error calling PebbleKit", e)
        }
    }

    override fun registerAckHandler(context: Context, uuid: UUID, onAck: (Int) -> Unit): BroadcastReceiver {
        aapsLogger.debug(LTag.PEBBLE, "PebbleTransportImpl: Registering ACK handler for UUID: {}", uuid)
        val receiver = object : PebbleKit.PebbleAckReceiver(uuid) {
            override fun receiveAck(context: Context?, transactionId: Int) {
                aapsLogger.debug(LTag.PEBBLE, "PebbleTransportImpl: Received ACK for transactionId: {}", transactionId)
                onAck(transactionId)
            }
        }
        PebbleKit.registerReceivedAckHandler(context, receiver)
        return receiver
    }

    override fun registerNackHandler(context: Context, uuid: UUID, onNack: (Int) -> Unit): BroadcastReceiver {
        aapsLogger.debug(LTag.PEBBLE, "PebbleTransportImpl: Registering NACK handler for UUID: {}", uuid)
        val receiver = object : PebbleKit.PebbleNackReceiver(uuid) {
            override fun receiveNack(context: Context?, transactionId: Int) {
                aapsLogger.debug(LTag.PEBBLE, "PebbleTransportImpl: Received NACK for transactionId: {}", transactionId)
                onNack(transactionId)
            }
        }
        PebbleKit.registerReceivedNackHandler(context, receiver)
        return receiver
    }

    override fun unregisterReceiver(context: Context, receiver: BroadcastReceiver) {
        aapsLogger.debug(LTag.PEBBLE, "PebbleTransportImpl: Unregistering receiver: {}", receiver)
        try {
            context.unregisterReceiver(receiver)
            aapsLogger.debug(LTag.PEBBLE, "PebbleTransportImpl: Receiver unregistered successfully")
        } catch (e: Exception) {
            aapsLogger.warn(LTag.PEBBLE, "PebbleTransportImpl: Error unregistering receiver: {}", e.message)
        }
    }

    override fun isWatchConnected(context: Context): Boolean {
        aapsLogger.debug(LTag.PEBBLE, "PebbleTransportImpl: Checking isWatchConnected status")
        return try {
            val connected = PebbleKit.isWatchConnected(context)
            aapsLogger.debug(LTag.PEBBLE, "PebbleTransportImpl: isWatchConnected returned: {}", connected)
            connected
        } catch (e: Exception) {
            aapsLogger.warn(LTag.PEBBLE, "PebbleTransportImpl: Error checking watch connection: {}", e.message)
            false
        }
    }
}