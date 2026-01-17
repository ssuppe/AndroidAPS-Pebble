package app.aaps.plugins.pebble

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

    override fun onStart() {
        super.onStart()
        disposable += rxBus
            .toObservable(EventLoopUpdateGui::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ sendData() }, fabricPrivacy::logException)
    }

    override fun onStop() {
        disposable.clear()
        super.onStop()
    }

    private fun sendData() {
        try {
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
            
            val dict = mapper.map(data)
            val uuid = uuidProvider.getTargetUuid()
            transport.sendData(context, uuid, dict)
        } catch (e: Exception) {
            fabricPrivacy.logException(e)
        }
    }
}
