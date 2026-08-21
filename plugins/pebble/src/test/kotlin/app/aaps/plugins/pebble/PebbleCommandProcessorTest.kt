package app.aaps.plugins.pebble

import android.content.Context
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.objects.wizard.BolusWizard
import com.getpebble.android.kit.util.PebbleDictionary
import io.reactivex.rxjava3.core.Single
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID
import javax.inject.Provider

class PebbleCommandProcessorTest {

    private val bolusWizardProvider: Provider<BolusWizard> = mock()
    private val bolusWizard: BolusWizard = mock()
    private val constraintChecker: ConstraintsChecker = mock()
    private val activePlugin: ActivePlugin = mock()
    private val pumpPlugin: Pump = mock()
    private val pumpDescription: PumpDescription = mock()
    private val persistenceLayer: PersistenceLayer = mock()
    private val profileFunction: ProfileFunction = mock()
    private val profileUtil: ProfileUtil = mock()
    private val profile: Profile = mock()
    private val preferences: Preferences = mock()
    private val uiInteraction: UiInteraction = mock()
    private val uel: UserEntryLogger = mock()
    private val commandQueue: CommandQueue = mock()
    private val rh: ResourceHelper = mock()
    private val decimalFormatter: DecimalFormatter = mock()
    private val aapsLogger: AAPSLogger = mock()
    private val transport: IPebbleTransport = mock()
    private val dateUtil: DateUtil = mock()
    private val context: Context = mock()
    private val iobCobCalculator: IobCobCalculator = mock()

    private lateinit var processor: PebbleCommandProcessor

    @BeforeEach
    fun setUp() {
        whenever(bolusWizardProvider.get()).thenReturn(bolusWizard)
        whenever(pumpPlugin.isInitialized()).thenReturn(true)
        whenever(pumpDescription.pumpType).thenReturn(PumpType.GENERIC_AAPS)
        whenever(pumpPlugin.pumpDescription).thenReturn(pumpDescription)
        whenever(activePlugin.activePump).thenReturn(pumpPlugin)


        whenever(profileFunction.getProfile()).thenReturn(profile)
        whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        whenever(decimalFormatter.to2Decimal(any())).thenReturn("1.50")
        whenever(rh.gs(any())).thenReturn("Success")
        whenever(dateUtil.now()).thenReturn(1000000L)

        whenever(constraintChecker.applyBolusConstraints(any())).thenAnswer { invocation ->
            invocation.getArgument(0) as ConstraintObject<Double>
        }
        whenever(constraintChecker.applyCarbsConstraints(any())).thenAnswer { invocation ->
            invocation.getArgument(0) as ConstraintObject<Int>
        }
        whenever(persistenceLayer.insertAndCancelCurrentTemporaryTarget(any(), any(), any(), any(), any())).thenReturn(Single.just(mock()))
        whenever(persistenceLayer.cancelCurrentTemporaryTargetIfAny(any(), any(), any(), any(), any())).thenReturn(Single.just(mock()))



        processor = PebbleCommandProcessor(
            bolusWizardProvider,
            constraintChecker,
            activePlugin,
            persistenceLayer,
            profileFunction,
            profileUtil,
            preferences,
            uiInteraction,
            uel,
            commandQueue,
            rh,
            decimalFormatter,
            aapsLogger,
            transport,
            dateUtil,
            iobCobCalculator
        )
    }

    @Test
    fun testSnoozeAlarm_callsUiInteraction() {
        val controllerUuid = UUID.randomUUID()
        val dict = PebbleDictionary()
        dict.addInt32(PebbleKeys.KEY_CMD_TYPE, 5) // SNOOZE_ALARM
        dict.addInt32(PebbleKeys.KEY_TRANS_ID, 123)

        processor.processCommand(context, controllerUuid, 123, dict)

        verify(uiInteraction).stopAlarm("Muted from Pebble")
        val captor = argumentCaptor<PebbleDictionary>()
        verify(transport).sendData(eq(context), eq(controllerUuid), captor.capture())
        assertEquals(0, captor.firstValue.getInteger(PebbleKeys.KEY_STATUS_CODE)?.toInt())
    }

    @Test
    fun testCancelActiveBolus_callsStopBolusDelivering() {
        val controllerUuid = UUID.randomUUID()
        val dict = PebbleDictionary()
        dict.addInt32(PebbleKeys.KEY_CMD_TYPE, 6) // CANCEL_ACTIVE_BOLUS
        dict.addInt32(PebbleKeys.KEY_TRANS_ID, 456)

        processor.processCommand(context, controllerUuid, 456, dict)

        verify(pumpPlugin).stopBolusDelivering()
        val captor = argumentCaptor<PebbleDictionary>()
        verify(transport).sendData(eq(context), eq(controllerUuid), captor.capture())
        assertEquals(0, captor.firstValue.getInteger(PebbleKeys.KEY_STATUS_CODE)?.toInt())
    }

    @Test
    fun testCancelActiveTempTarget_callsPersistenceLayer() {
        val controllerUuid = UUID.randomUUID()
        val dict = PebbleDictionary()
        dict.addInt32(PebbleKeys.KEY_CMD_TYPE, 4) // CANCEL_ACTIVE_TEMP_TARGET
        dict.addInt32(PebbleKeys.KEY_TRANS_ID, 789)

        processor.processCommand(context, controllerUuid, 789, dict)

        verify(persistenceLayer).cancelTempTargets(any())
        val captor = argumentCaptor<PebbleDictionary>()
        verify(transport).sendData(eq(context), eq(controllerUuid), captor.capture())
        assertEquals(0, captor.firstValue.getInteger(PebbleKeys.KEY_STATUS_CODE)?.toInt())
    }

    @Test
    fun testSetTempTarget_presetEatingSoon_looksUpPreferences() {
        val controllerUuid = UUID.randomUUID()
        whenever(preferences.get(eq(IntKey.OverviewEatingSoonDuration))).thenReturn(45)
        whenever(preferences.get(eq(UnitDoubleKey.OverviewEatingSoonTarget))).thenReturn(90.0)

        val dict = PebbleDictionary()
        dict.addInt32(PebbleKeys.KEY_CMD_TYPE, 3) // SET_TEMP_TARGET
        dict.addInt32(PebbleKeys.KEY_TRANS_ID, 101)
        dict.addInt32(PebbleKeys.KEY_PRESET_TYPE, 1) // EATING_SOON

        processor.processCommand(context, controllerUuid, 101, dict)

        verify(persistenceLayer).setTempTarget(any())
        val captor = argumentCaptor<PebbleDictionary>()
        verify(transport).sendData(eq(context), eq(controllerUuid), captor.capture())
        assertEquals(0, captor.firstValue.getInteger(PebbleKeys.KEY_STATUS_CODE)?.toInt())
    }
}
