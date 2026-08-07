package app.aaps.plugins.pebble

import android.content.BroadcastReceiver
import android.content.Context
import android.content.SharedPreferences
import app.aaps.core.data.iob.CobInfo
import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.aps.AutosensDataStore
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
import app.aaps.plugins.pebble.data.EnrichedData
import com.getpebble.android.kit.util.PebbleDictionary
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
    private val prefs = mock<SharedPreferences>()
    private val mapper = mock<PebbleDataMapper>()
    private val fabricPrivacy = mock<FabricPrivacy>()

    private lateinit var plugin: PebblePlugin

    @BeforeEach
    fun setUp() {
        whenever(aapsSchedulers.io).thenReturn(Schedulers.trampoline())
        whenever(rxBus.toObservable(EventLoopUpdateGui::class.java)).thenReturn(Observable.empty())
        whenever(uuidProvider.getTargetUuid()).thenReturn(UUID.randomUUID())
        whenever(transport.registerAckHandler(any(), any(), any())).thenReturn(mock<BroadcastReceiver>())
        whenever(transport.registerNackHandler(any(), any(), any())).thenReturn(mock<BroadcastReceiver>())
        
        plugin = PebblePlugin(
            aapsLogger, rh, aapsSchedulers, rxBus, context,
            iobCobCalculator, glucoseStatusProvider, transport, uuidProvider, mapper, fabricPrivacy, prefs
        )
    }

    @Test
    fun testOnEvent_mapsData_andSendsToTransport() {
        // Stub connectivity
        whenever(transport.isWatchConnected(any())).thenReturn(true)

        val event = EventLoopUpdateGui()
        whenever(rxBus.toObservable(EventLoopUpdateGui::class.java)).thenReturn(Observable.just(event))
        
        val uuid = UUID.randomUUID()
        whenever(uuidProvider.getTargetUuid()).thenReturn(uuid)
        
        val glucoseStatus = mock<GlucoseStatus>()
        whenever(glucoseStatus.glucose).thenReturn(120.0)
        whenever(glucoseStatusProvider.getGlucoseStatusData(any())).thenReturn(glucoseStatus)
        
        val lastBg = mock<InMemoryGlucoseValue>()
        whenever(lastBg.trendArrow).thenReturn(TrendArrow.SINGLE_UP)
        val ads = mock<AutosensDataStore>()
        whenever(ads.lastBg()).thenReturn(lastBg)
        whenever(iobCobCalculator.ads).thenReturn(ads)
        
        val dict = PebbleDictionary()
        whenever(mapper.map(any())).thenReturn(dict)

        plugin.onStart()
        
        val captor = argumentCaptor<EnrichedData>()
        verify(mapper).map(captor.capture())
        val capturedData = captor.firstValue
        assertEquals(120.0, capturedData.bg)
        assertEquals(TrendArrow.SINGLE_UP.ordinal, capturedData.trend)

        verify(transport).sendData(eq(context), eq(uuid), eq(dict))
    }

    @Test
    fun testOnEvent_skipsSend_whenWatchDisconnected() {
        // Stub connectivity
        whenever(transport.isWatchConnected(any())).thenReturn(false)

        val event = EventLoopUpdateGui()
        whenever(rxBus.toObservable(EventLoopUpdateGui::class.java)).thenReturn(Observable.just(event))

        plugin.onStart()

        verify(mapper, never()).map(any())
        verify(transport, never()).sendData(any(), any(), any())
    }

    @Test
    fun testOnEvent_handlesExceptions_gracefully() {
        // Stub connectivity
        whenever(transport.isWatchConnected(any())).thenReturn(true)

        val event = EventLoopUpdateGui()
        whenever(rxBus.toObservable(EventLoopUpdateGui::class.java)).thenReturn(Observable.just(event))
        whenever(uuidProvider.getTargetUuid()).thenThrow(RuntimeException("Test Error"))

        plugin.onStart()
        
        verify(fabricPrivacy, atLeastOnce()).logException(any())
    }

    @Test
    fun testUuidChange_unregistersAndReRegistersHandlers() {
        val listenerCaptor = argumentCaptor<SharedPreferences.OnSharedPreferenceChangeListener>()
        
        plugin.onStart()
        
        verify(prefs).registerOnSharedPreferenceChangeListener(listenerCaptor.capture())
        val listener = listenerCaptor.firstValue
        
        val newUuid = UUID.randomUUID()
        whenever(uuidProvider.getTargetUuid()).thenReturn(newUuid)
        
        // Trigger preference change for target UUID key
        listener.onSharedPreferenceChanged(prefs, "pebble_app_uuid")
        
        // onStart registers 1 ACK and 1 NACK handler.
        // onSharedPreferenceChanged unregisters the 2 active receivers and registers 2 new ones.
        verify(transport, times(2)).registerAckHandler(any(), any(), any())
        verify(transport, times(2)).registerNackHandler(any(), any(), any())
        verify(transport, times(2)).unregisterReceiver(any(), any())
        
        plugin.onStop()
        
        verify(prefs).unregisterOnSharedPreferenceChangeListener(eq(listener))
        // onStop unregisters the 2 active receivers. Total unregister calls = 4
        verify(transport, times(4)).unregisterReceiver(any(), any())
    }
}
