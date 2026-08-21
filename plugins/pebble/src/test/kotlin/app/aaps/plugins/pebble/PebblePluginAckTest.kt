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
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.interfaces.profile.Profile

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

    private val profileFunction = mock<ProfileFunction>()
    private val profileUtil = mock<ProfileUtil>()
    private val preferences = mock<Preferences>()
    private val processedTbrEbData = mock<ProcessedTbrEbData>()
    private val config = mock<Config>()
    private val commandProcessor = mock<PebbleCommandProcessor>()

    private lateinit var plugin: PebblePlugin

    @BeforeEach
    fun setUp() {
        whenever(aapsSchedulers.io).thenReturn(Schedulers.trampoline())
        whenever(uuidProvider.getTargetUuid()).thenReturn(UUID.randomUUID())
        whenever(uuidProvider.getControllerUuid()).thenReturn(UUID.randomUUID())
        whenever(rxBus.toObservable(EventLoopUpdateGui::class.java)).thenReturn(Observable.empty())
        
        // Mock the transport to return a dummy receiver so the plugin stores it
        whenever(transport.registerAckHandler(any(), any(), any())).thenReturn(mock<BroadcastReceiver>())
        whenever(transport.registerNackHandler(any(), any(), any())).thenReturn(mock<BroadcastReceiver>())
        whenever(transport.registerDataHandler(any(), any(), any())).thenReturn(mock<BroadcastReceiver>())
        
        // Default to connected for existing tests
        whenever(transport.isWatchConnected(any())).thenReturn(true)

        whenever(config.appInitialized).thenReturn(true)
        val profile = mock<Profile>()
        whenever(profile.getBasal()).thenReturn(0.90)
        whenever(profileFunction.getProfile()).thenReturn(profile)
        whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        whenever(preferences.get(any<UnitDoubleKey>())).thenReturn(70.0)

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
            prefs,
            profileFunction,
            profileUtil,
            preferences,
            processedTbrEbData,
            config,
            decimalFormatter,
            commandProcessor
        )
    }

    @Test
    fun testOnStart_registersAckAndNackHandlers() {
        plugin.onStart()

        verify(transport).registerAckHandler(any(), any(), any())
        verify(transport).registerNackHandler(any(), any(), any())
        verify(transport).registerDataHandler(any(), any(), any())
    }

    @Test
    fun testOnStop_unregistersHandlers() {
        plugin.onStart() // Start first to register
        plugin.onStop()

        // Should be called 3 times: ACK, NACK, Data receivers
        verify(transport, org.mockito.kotlin.times(3)).unregisterReceiver(any(), any())
    }
}

