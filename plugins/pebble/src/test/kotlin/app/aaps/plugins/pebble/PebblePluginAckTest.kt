package app.aaps.plugins.pebble

import android.content.Context
import android.content.SharedPreferences
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import com.getpebble.android.kit.PebbleKit
import io.reactivex.rxjava3.schedulers.Schedulers
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

import app.aaps.core.interfaces.rx.events.EventLoopUpdateGui
import io.reactivex.rxjava3.core.Observable

import android.content.BroadcastReceiver

class PebblePluginAckTest {

    private val aapsLogger: AAPSLogger = mock()
    private val rh: ResourceHelper = mock()
    private val aapsSchedulers: AapsSchedulers = mock()
    private val rxBus: RxBus = mock()
    private val context: Context = mock()
    private val iobCobCalculator: IobCobCalculator = mock()
    private val glucoseStatusProvider: GlucoseStatusProvider = mock()
    private val transport: IPebbleTransport = mock()
    private val uuidProvider: TargetUuidProvider = mock()
    private val prefs: SharedPreferences = mock()
    private val mapper: PebbleDataMapper = mock()
    private val fabricPrivacy: FabricPrivacy = mock()

    private lateinit var plugin: PebblePlugin

    @BeforeEach
    fun setUp() {
        whenever(aapsSchedulers.io).thenReturn(Schedulers.trampoline())
        whenever(uuidProvider.getTargetUuid()).thenReturn(UUID.randomUUID())
        whenever(rxBus.toObservable(EventLoopUpdateGui::class.java)).thenReturn(Observable.empty())
        
        // Mock the transport to return a dummy receiver so the plugin stores it
        whenever(transport.registerAckHandler(any(), any(), any())).thenReturn(mock<BroadcastReceiver>())
        whenever(transport.registerNackHandler(any(), any(), any())).thenReturn(mock<BroadcastReceiver>())
        
        // Default to connected for existing tests
        whenever(transport.isWatchConnected(any())).thenReturn(true)

        plugin = PebblePlugin(
            aapsLogger,
            rh,
            aapsSchedulers,
            rxBus,
            context,
            iobCobCalculator,
            glucoseStatusProvider,
            transport,
            uuidProvider,
            mapper,
            fabricPrivacy,
            prefs
        )
    }

    @Test
    fun testOnStart_registersAckAndNackHandlers() {
        plugin.onStart()

        verify(transport).registerAckHandler(any(), any(), any())
        verify(transport).registerNackHandler(any(), any(), any())
    }

    @Test
    fun testOnStop_unregistersHandlers() {
        // We assume the transport implementation might return a receiver object that needs unregistering, 
        // or we might just verify a generic 'unregister' call if we abstract it that way.
        // For now, let's assume the abstraction handles the receiver instance management internally 
        // or via a returned handle. Let's start simple: the transport should have an unregister method.
        
        plugin.onStart() // Start first to register
        plugin.onStop()

        // Should be called twice, once for ACK receiver and once for NACK receiver
        verify(transport, org.mockito.kotlin.times(2)).unregisterReceiver(any(), any())
    }
}
