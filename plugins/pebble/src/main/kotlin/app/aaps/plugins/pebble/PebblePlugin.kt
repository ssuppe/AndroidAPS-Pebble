package app.aaps.plugins.pebble

import android.content.BroadcastReceiver
import android.content.Context
import android.content.SharedPreferences
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.model.TrendArrow
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
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.objects.extensions.round
import app.aaps.core.objects.extensions.toStringShort
import app.aaps.core.objects.extensions.generateCOBString
import kotlin.math.abs

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
    private val fabricPrivacy: FabricPrivacy,
    private val prefs: SharedPreferences,
    private val profileFunction: ProfileFunction,
    private val profileUtil: ProfileUtil,
    private val preferences: Preferences,
    private val processedTbrEbData: ProcessedTbrEbData,
    private val config: Config,
    private val decimalFormatter: DecimalFormatter
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

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "pebble_app_uuid") {
            aapsLogger.info(LTag.PEBBLE, "PebblePlugin: Target UUID changed. Re-registering receivers.")
            unregisterReceivers()
            registerReceivers()
        }
    }

    override fun onStart() {
        super.onStart()
        aapsLogger.debug(LTag.PEBBLE, "PebblePlugin onStart: Subscribing to EventLoopUpdateGui")
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
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
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
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
            val isConnected = transport.isWatchConnected(context)
            aapsLogger.debug(LTag.PEBBLE, "PebblePlugin: Connection check: isWatchConnected = {}", isConnected)
            if (!isConnected) {
                return
            }

            aapsLogger.debug(LTag.PEBBLE, "PebblePlugin: Preparing data to send")
            val bgStatus = glucoseStatusProvider.getGlucoseStatusData()
            val lastBg = iobCobCalculator.ads.lastBg()
            val trendOrdinal = lastBg?.trendArrow?.ordinal ?: TrendArrow.NONE.ordinal

            val profile = profileFunction.getProfile()
            var iobSum: String? = null
            var iobDetail: String? = null
            var cobString: String? = null
            var currentBasal: String? = null
            var delta: String? = null
            var avgDelta: String? = null
            var lowLine = 70
            var highLine = 180
            val units = profileFunction.getUnits()
            val unitsValue = if (units == GlucoseUnit.MGDL) 0 else 1

            if (config.appInitialized && profile != null) {
                // 1. IOB (Bolus + Basal)
                val bolusIob = iobCobCalculator.calculateIobFromBolus().round()
                val basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round()
                iobSum = decimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob) + " U"
                iobDetail = "(${decimalFormatter.to2Decimal(bolusIob.iob)}|${decimalFormatter.to2Decimal(basalIob.basaliob)})"

                // 2. COB
                cobString = iobCobCalculator.getCobInfo("WatcherUpdaterService").generateCOBString(decimalFormatter)

                // 3. Basal Rate
                currentBasal = processedTbrEbData.getTempBasalIncludingConvertedExtended(System.currentTimeMillis())?.toStringShort(rh) 
                    ?: decimalFormatter.to2Decimal(profile.getBasal())

                // 4. Targets
                lowLine = profileUtil.convertToMgdl(preferences.get(UnitDoubleKey.OverviewLowMark), units).toInt()
                highLine = profileUtil.convertToMgdl(preferences.get(UnitDoubleKey.OverviewHighMark), units).toInt()

                // 5. Delta & Avg Delta
                val glucoseStatus = glucoseStatusProvider.getGlucoseStatusData(true)
                if (glucoseStatus != null) {
                    delta = deltaString(glucoseStatus.delta, glucoseStatus.delta * Constants.MGDL_TO_MMOLL, units)
                    avgDelta = deltaString(glucoseStatus.shortAvgDelta, glucoseStatus.shortAvgDelta * Constants.MGDL_TO_MMOLL, units)
                }
            }

            // 6. Glucose History (36-point BG/2 array scaled and right-aligned)
            val readings = iobCobCalculator.ads.getBgReadingsDataTableCopy()
            val sortedReadings = readings.filter { it.isValid }.sortedByDescending { it.timestamp }

            val historySize = minOf(sortedReadings.size, 36)
            val historyBytes = ByteArray(36) { 0.toByte() }

            for (i in 0 until historySize) {
                val reading = sortedReadings[i]
                val targetIndex = 36 - 1 - i
                val scaledValue = (reading.value / 2).toInt().coerceIn(0, 255)
                historyBytes[targetIndex] = scaledValue.toByte()
            }

            val data = EnrichedData(
                bg = bgStatus?.glucose,
                trend = trendOrdinal,
                time = System.currentTimeMillis(),
                iob = iobSum,
                cob = cobString,
                basal = currentBasal,
                iobDetail = iobDetail,
                delta = delta,
                avgDelta = avgDelta,
                history = historyBytes,
                lowTarget = lowLine,
                highTarget = highLine,
                units = unitsValue
            )
            
            aapsLogger.debug(LTag.PEBBLE, "PebblePlugin: EnrichedData: {}", data)
            val dict = mapper.map(data)
            val uuid = uuidProvider.getTargetUuid()
            
            aapsLogger.debug(LTag.PEBBLE, "PebblePlugin: Sending dictionary to transport for UUID: {}", uuid)
            transport.sendData(context, uuid, dict)
            aapsLogger.debug(LTag.PEBBLE, "PebblePlugin: Intent broadcast to Pebble App (Check ACKs for delivery)")
        } catch (e: Exception) {
            aapsLogger.error(LTag.PEBBLE, "PebblePlugin: Failed to send data", e)
            fabricPrivacy.logException(e)
        }
    }

    private fun deltaString(deltaMGDL: Double, deltaMMOL: Double, units: GlucoseUnit): String {
        var deltaStr = if (deltaMGDL >= 0) "+" else "-"
        deltaStr += if (units == GlucoseUnit.MGDL) {
            decimalFormatter.to0Decimal(abs(deltaMGDL))
        } else {
            decimalFormatter.to1Decimal(abs(deltaMMOL))
        }
        return deltaStr
    }
}
