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
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.objects.extensions.generateCOBString

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

    private val profileFunction = mock<ProfileFunction>()
    private val profileUtil = mock<ProfileUtil>()
    private val preferences = mock<Preferences>()
    private val processedTbrEbData = mock<ProcessedTbrEbData>()
    private val config = mock<Config>()
    private val decimalFormatter = mock<DecimalFormatter>()
    private val commandProcessor = mock<PebbleCommandProcessor>()

    private lateinit var plugin: PebblePlugin

    @BeforeEach
    fun setUp() {
        whenever(aapsSchedulers.io).thenReturn(Schedulers.trampoline())
        whenever(rxBus.toObservable(EventLoopUpdateGui::class.java)).thenReturn(Observable.empty())
        whenever(uuidProvider.getTargetUuid()).thenReturn(UUID.randomUUID())
        whenever(uuidProvider.getControllerUuid()).thenReturn(UUID.randomUUID())
        whenever(transport.registerAckHandler(any(), any(), any())).thenReturn(mock<BroadcastReceiver>())
        whenever(transport.registerNackHandler(any(), any(), any())).thenReturn(mock<BroadcastReceiver>())
        whenever(transport.registerDataHandler(any(), any(), any())).thenReturn(mock<BroadcastReceiver>())
        
        whenever(config.appInitialized).thenReturn(true)
        val profile = mock<Profile>()
        whenever(profile.getBasal()).thenReturn(0.90)
        whenever(profileFunction.getProfile()).thenReturn(profile)
        whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        whenever(preferences.get(any<UnitDoubleKey>())).thenReturn(70.0)
        
        val defaultIob = IobTotal(time = 0)
        whenever(iobCobCalculator.calculateIobFromBolus()).thenReturn(defaultIob)
        whenever(iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended()).thenReturn(defaultIob)
        
        val defaultCob = CobInfo(timestamp = 0, displayCob = 0.0, futureCarbs = 0.0)
        whenever(iobCobCalculator.getCobInfo(any())).thenReturn(defaultCob)
        
        whenever(decimalFormatter.to2Decimal(any())).thenReturn("0.00")
        whenever(decimalFormatter.to0Decimal(any())).thenReturn("0")
        whenever(decimalFormatter.to1Decimal(any())).thenReturn("0.0")
        
        plugin = PebblePlugin(
            aapsLogger, rh, aapsSchedulers, rxBus, context,
            iobCobCalculator, glucoseStatusProvider, transport, uuidProvider, mapper, fabricPrivacy, prefs,
            profileFunction, profileUtil, preferences, processedTbrEbData, config, decimalFormatter, commandProcessor
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
        
        // onStart registers 1 ACK, 1 NACK, and 1 Data handler.
        // onSharedPreferenceChanged unregisters the 3 active receivers and registers 3 new ones.
        verify(transport, times(2)).registerAckHandler(any(), any(), any())
        verify(transport, times(2)).registerNackHandler(any(), any(), any())
        verify(transport, times(2)).registerDataHandler(any(), any(), any())
        verify(transport, times(3)).unregisterReceiver(any(), any())
        
        plugin.onStop()
        
        verify(prefs).unregisterOnSharedPreferenceChangeListener(eq(listener))
        // onStop unregisters the 3 active receivers. Total unregister calls = 6
        verify(transport, times(6)).unregisterReceiver(any(), any())
    }


    @Test
    fun testSendData_extractsAndSendsAllEnrichedMetrics() {
        whenever(transport.isWatchConnected(any())).thenReturn(true)
        
        // Mock profile and units
        val profile = mock<Profile>()
        whenever(profile.getBasal()).thenReturn(0.90)
        whenever(profileFunction.getProfile()).thenReturn(profile)
        whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        
        // Mock targets double preferences
        whenever(preferences.get(eq(UnitDoubleKey.OverviewLowMark))).thenReturn(70.0)
        whenever(preferences.get(eq(UnitDoubleKey.OverviewHighMark))).thenReturn(180.0)
        whenever(profileUtil.convertToMgdl(70.0, GlucoseUnit.MGDL)).thenReturn(70.0)
        whenever(profileUtil.convertToMgdl(180.0, GlucoseUnit.MGDL)).thenReturn(180.0)
        
        // Mock treatments/IOB/COB
        val bolusIob = IobTotal(time = 0, iob = 0.02)
        val basalIob = IobTotal(time = 0, basaliob = 0.31)
        whenever(iobCobCalculator.calculateIobFromBolus()).thenReturn(bolusIob)
        whenever(iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended()).thenReturn(basalIob)
        whenever(decimalFormatter.to2Decimal(0.02 + 0.31)).thenReturn("0.33")
        whenever(decimalFormatter.to2Decimal(0.02)).thenReturn("0.02")
        whenever(decimalFormatter.to2Decimal(0.31)).thenReturn("0.31")
        
        val cobInfo = CobInfo(timestamp = 0, displayCob = 15.0, futureCarbs = 0.0)
        whenever(iobCobCalculator.getCobInfo(any())).thenReturn(cobInfo)
        whenever(decimalFormatter.to0Decimal(15.0)).thenReturn("15")
        
        // Mock active basal rate
        whenever(processedTbrEbData.getTempBasalIncludingConvertedExtended(any())).thenReturn(null)
        whenever(decimalFormatter.to2Decimal(0.90)).thenReturn("0.90")
        
        // Mock deltas
        val glucoseStatus = mock<GlucoseStatus>()
        whenever(glucoseStatus.glucose).thenReturn(120.0)
        whenever(glucoseStatus.delta).thenReturn(3.0)
        whenever(glucoseStatus.shortAvgDelta).thenReturn(5.0)
        whenever(glucoseStatusProvider.getGlucoseStatusData()).thenReturn(glucoseStatus)
        whenever(glucoseStatusProvider.getGlucoseStatusData(true)).thenReturn(glucoseStatus)
        whenever(decimalFormatter.to0Decimal(3.0)).thenReturn("3")
        whenever(decimalFormatter.to0Decimal(5.0)).thenReturn("5")

        // Mock recent BG readings (history)
        val ads = mock<AutosensDataStore>()
        val lastBg = mock<InMemoryGlucoseValue>()
        whenever(lastBg.trendArrow).thenReturn(TrendArrow.SINGLE_UP)
        whenever(ads.lastBg()).thenReturn(lastBg)
        
        val testReading = GV(
            timestamp = System.currentTimeMillis() - 5000,
            value = 120.0,
            trendArrow = TrendArrow.SINGLE_UP,
            raw = 120.0,
            noise = 0.0,
            sourceSensor = SourceSensor.DEXCOM_G6_NATIVE
        )
        whenever(ads.getBgReadingsDataTableCopy()).thenReturn(listOf(testReading))
        whenever(iobCobCalculator.ads).thenReturn(ads)
        
        val dict = PebbleDictionary()
        whenever(mapper.map(any())).thenReturn(dict)

        val event = EventLoopUpdateGui()
        whenever(rxBus.toObservable(EventLoopUpdateGui::class.java)).thenReturn(Observable.just(event))

        plugin.onStart()

        val captor = argumentCaptor<EnrichedData>()
        verify(mapper).map(captor.capture())
        val capturedData = captor.firstValue

        assertEquals(120.0, capturedData.bg)
        assertEquals(TrendArrow.SINGLE_UP.ordinal, capturedData.trend)
        assertEquals("0.33 U", capturedData.iob)
        assertEquals("(0.02|0.31)", capturedData.iobDetail)
        assertEquals("15g", capturedData.cob)
        assertEquals("0.90", capturedData.basal)
        assertEquals(70, capturedData.lowTarget)
        assertEquals(180, capturedData.highTarget)
        assertEquals("+3", capturedData.delta)
        assertEquals("+5", capturedData.avgDelta)
        assertEquals(0, capturedData.units)
        org.junit.jupiter.api.Assertions.assertNotNull(capturedData.history)
        assertEquals(60.toByte(), capturedData.history!![35])
    }

    @Test
    fun testGlucoseHistorySerialization_correctlyAlignsAndScales() {
        whenever(transport.isWatchConnected(any())).thenReturn(true)
        
        // Mock profile and units
        val profile = mock<Profile>()
        whenever(profile.getBasal()).thenReturn(0.90)
        whenever(profileFunction.getProfile()).thenReturn(profile)
        whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        
        // Mock treatments/IOB/COB
        val bolusIob = IobTotal(time = 0)
        val basalIob = IobTotal(time = 0)
        whenever(iobCobCalculator.calculateIobFromBolus()).thenReturn(bolusIob)
        whenever(iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended()).thenReturn(basalIob)
        whenever(decimalFormatter.to2Decimal(any())).thenReturn("0.00")
        
        val cobInfo = CobInfo(timestamp = 0, displayCob = 0.0, futureCarbs = 0.0)
        whenever(iobCobCalculator.getCobInfo(any())).thenReturn(cobInfo)
        
        // Mock active basal rate
        whenever(processedTbrEbData.getTempBasalIncludingConvertedExtended(any())).thenReturn(null)
        
        // Mock recent BG readings (3 readings: value 100 at t0, 110 at t-5m, 120 at t-10m)
        val ads = mock<AutosensDataStore>()
        val lastBg = mock<InMemoryGlucoseValue>()
        whenever(lastBg.trendArrow).thenReturn(TrendArrow.NONE)
        whenever(ads.lastBg()).thenReturn(lastBg)
        
        val t0 = System.currentTimeMillis()
        val readingsList = listOf(
            GV(timestamp = t0, value = 100.0, trendArrow = TrendArrow.NONE, raw = null, noise = null, sourceSensor = SourceSensor.DEXCOM_G6_NATIVE),
            GV(timestamp = t0 - 300000, value = 110.0, trendArrow = TrendArrow.NONE, raw = null, noise = null, sourceSensor = SourceSensor.DEXCOM_G6_NATIVE),
            GV(timestamp = t0 - 600000, value = 120.0, trendArrow = TrendArrow.NONE, raw = null, noise = null, sourceSensor = SourceSensor.DEXCOM_G6_NATIVE)
        )
        whenever(ads.getBgReadingsDataTableCopy()).thenReturn(readingsList)
        whenever(iobCobCalculator.ads).thenReturn(ads)
        
        val dict = PebbleDictionary()
        whenever(mapper.map(any())).thenReturn(dict)

        val event = EventLoopUpdateGui()
        whenever(rxBus.toObservable(EventLoopUpdateGui::class.java)).thenReturn(Observable.just(event))

        plugin.onStart()

        val captor = argumentCaptor<EnrichedData>()
        verify(mapper).map(captor.capture())
        val capturedData = captor.firstValue

        org.junit.jupiter.api.Assertions.assertNotNull(capturedData.history)
        val history = capturedData.history!!
        assertEquals(36, history.size)
        // Latest reading should be at the right edge (index 35): 100 / 2 = 50
        assertEquals(50.toByte(), history[35])
        // Previous reading at index 34: 110 / 2 = 55
        assertEquals(55.toByte(), history[34])
        // Older reading at index 33: 120 / 2 = 60
        assertEquals(60.toByte(), history[33])
        // The rest should be 0
        for (i in 0 until 33) {
            assertEquals(0.toByte(), history[i])
        }
    }
}
