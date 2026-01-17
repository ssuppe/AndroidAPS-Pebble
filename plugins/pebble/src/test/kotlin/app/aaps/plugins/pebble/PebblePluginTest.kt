package app.aaps.plugins.pebble

import android.content.Context
import app.aaps.core.data.iob.CobInfo
import app.aaps.core.interfaces.aps.GlucoseStatus
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventLoopUpdateGui
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import com.getpebble.android.kit.util.PebbleDictionary
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.UUID

class PebblePluginTest {

    private val aapsLogger = mock<AAPSLogger>()
    private val rh = mock<ResourceHelper>()
    private val aapsSchedulers = mock<AapsSchedulers>()
    private val rxBus = mock<RxBus>()
    private val context = mock<Context>()
    private val iobCobCalculator = mock<IobCobCalculator>()
    private val glucoseStatusProvider = mock<GlucoseStatusProvider>()
    private val transport = mock<IPebbleTransport>()
    private val uuidProvider = mock<TargetUuidProvider>()
    private val mapper = mock<PebbleDataMapper>()
    private val fabricPrivacy = mock<FabricPrivacy>()

    private lateinit var plugin: PebblePlugin

    @BeforeEach
    fun setUp() {
        whenever(aapsSchedulers.io).thenReturn(Schedulers.trampoline())
        whenever(rxBus.toObservable(EventLoopUpdateGui::class.java)).thenReturn(Observable.empty())
        
        plugin = PebblePlugin(
            aapsLogger, rh, aapsSchedulers, rxBus, context,
            iobCobCalculator, glucoseStatusProvider, transport, uuidProvider, mapper, fabricPrivacy
        )
    }

    @Test
    fun testInitialize_subscribesTo_EventLoopUpdateGui() {
        plugin.onStart()
        verify(rxBus).toObservable(EventLoopUpdateGui::class.java)
    }

    @Test
    fun testOnEvent_mapsData_andSendsToTransport() {
        val event = EventLoopUpdateGui()
        whenever(rxBus.toObservable(EventLoopUpdateGui::class.java)).thenReturn(Observable.just(event))
        
        val uuid = UUID.randomUUID()
        whenever(uuidProvider.getTargetUuid()).thenReturn(uuid)
        
        val glucoseStatus = mock<GlucoseStatus>()
        whenever(glucoseStatus.glucose).thenReturn(120.0)
        whenever(glucoseStatus.delta).thenReturn(5.0)
        whenever(glucoseStatusProvider.getGlucoseStatusData(any())).thenReturn(glucoseStatus)
        
        val iobTotal = IobTotal(System.currentTimeMillis(), iob = 1.5)
        whenever(iobCobCalculator.calculateIobFromBolus()).thenReturn(iobTotal)
        
        val cobInfo = CobInfo(System.currentTimeMillis(), 20.0, 0.0)
        whenever(iobCobCalculator.getCobInfo(any())).thenReturn(cobInfo)
        
        val dict = PebbleDictionary()
        whenever(mapper.map(any())).thenReturn(dict)

        plugin.onStart()
        
        verify(transport).sendData(eq(context), eq(uuid), eq(dict))
    }

    @Test
    fun testOnEvent_handlesExceptions_gracefully() {
        val event = EventLoopUpdateGui()
        whenever(rxBus.toObservable(EventLoopUpdateGui::class.java)).thenReturn(Observable.just(event))
        whenever(uuidProvider.getTargetUuid()).thenThrow(RuntimeException("Test Error"))

        plugin.onStart()
        
        verify(fabricPrivacy, atLeastOnce()).logException(any())
    }
}
