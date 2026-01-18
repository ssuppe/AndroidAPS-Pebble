package app.aaps.plugins.pebble

import android.content.BroadcastReceiver
import android.content.Context
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventLoopUpdateGui
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.interfaces.logging.LTag
import app.aaps.plugins.pebble.data.EnrichedData
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PebblePlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    private val aapsSchedulers: AapsSchedulers,
    private val rxBus: RxBus,
    private val context: Context,
    private val iobCobCalculator: IobCobCalculator,
    private val glucoseStatusProvider: GlucoseStatusProvider,
    private val transport: IPebbleTransport,
    private val uuidProvider: TargetUuidProvider,
    private val mapper: PebbleDataMapper,
    private val fabricPrivacy: FabricPrivacy
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.SYNC)
        .fragmentClass(PebbleFragment::class.java.name)
        .pluginName(R.string.pebble)
        .shortName(R.string.pebble_short)
        .description(R.string.pebble_description),
    aapsLogger, rh
) {

    private val disposable = CompositeDisposable()
    private var ackReceiver: BroadcastReceiver? = null
    private var nackReceiver: BroadcastReceiver? = null

    override fun onStart() {
        super.onStart()
        aapsLogger.debug(LTag.PEBBLE, "PebblePlugin onStart: Subscribing to EventLoopUpdateGui")
        disposable += rxBus
            .toObservable(EventLoopUpdateGui::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ 
                aapsLogger.debug(LTag.PEBBLE, "PebblePlugin: Received EventLoopUpdateGui")
                sendData() 
            }, { e ->
                aapsLogger.error(LTag.PEBBLE, "PebblePlugin: Error in subscription", e)
                fabricPrivacy.logException(e)
            })

        registerReceivers()
    }

    override fun onStop() {
        aapsLogger.debug(LTag.PEBBLE, "PebblePlugin onStop: Clearing disposables")
        disposable.clear()
        unregisterReceivers()
        super.onStop()
    }

    private fun registerReceivers() {
        try {
            val uuid = uuidProvider.getTargetUuid()
            
            ackReceiver = transport.registerAckHandler(context, uuid) { transactionId ->
                aapsLogger.info(LTag.PEBBLE, "PebblePlugin: ACK received for transaction {}", transactionId)
            }

            nackReceiver = transport.registerNackHandler(context, uuid) { transactionId ->
                aapsLogger.warn(LTag.PEBBLE, "PebblePlugin: NACK received for transaction {}", transactionId)
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.PEBBLE, "PebblePlugin: Error registering receivers", e)
        }
    }

    private fun unregisterReceivers() {
        ackReceiver?.let {
            transport.unregisterReceiver(context, it)
            ackReceiver = null
        }
        nackReceiver?.let {
            transport.unregisterReceiver(context, it)
            nackReceiver = null
        }
    }

    private fun sendData() {
        try {
            aapsLogger.debug(LTag.PEBBLE, "PebblePlugin: Preparing data to send")
            val bgStatus = glucoseStatusProvider.getGlucoseStatusData()
            val iobTotal = iobCobCalculator.calculateIobFromBolus()
            val cobInfo = iobCobCalculator.getCobInfo("PebblePlugin")
            
            val data = EnrichedData(
                bg = bgStatus?.glucose,
                trend = bgStatus?.delta?.toInt(),
                iob = iobTotal.iob,
                cob = cobInfo.displayCob ?: 0.0,
                time = System.currentTimeMillis()
            )
            
            aapsLogger.debug(LTag.PEBBLE, "PebblePlugin: EnrichedData: {}", data)
            val dict = mapper.map(data)
            val uuid = uuidProvider.getTargetUuid()
            
            aapsLogger.debug(LTag.PEBBLE, "PebblePlugin: Sending dictionary to transport for UUID: {}", uuid)
            transport.sendData(context, uuid, dict)
            aapsLogger.debug(LTag.PEBBLE, "PebblePlugin: Data handed off to Pebble App successfully")
        } catch (e: Exception) {
            aapsLogger.error(LTag.PEBBLE, "PebblePlugin: Failed to send data", e)
            fabricPrivacy.logException(e)
        }
    }
}
