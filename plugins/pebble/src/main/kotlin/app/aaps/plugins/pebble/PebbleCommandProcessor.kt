package app.aaps.plugins.pebble

import android.content.Context
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.objects.database.TempTarget
import app.aaps.core.objects.wizard.BolusWizard
import com.getpebble.android.kit.util.PebbleDictionary
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class PebbleCommandProcessor @Inject constructor(
    private val bolusWizardProvider: Provider<BolusWizard>,
    private val constraintChecker: ConstraintsChecker,
    private val activePlugin: ActivePlugin,
    private val persistenceLayer: PersistenceLayer,
    private val profileFunction: ProfileFunction,
    private val profileUtil: ProfileUtil,
    private val preferences: Preferences,
    private val uiInteraction: UiInteraction,
    private val uel: UserEntryLogger,
    private val commandQueue: CommandQueue,
    private val rh: ResourceHelper,
    private val decimalFormatter: DecimalFormatter,
    private val aapsLogger: AAPSLogger,
    private val transport: IPebbleTransport,
    private val dateUtil: DateUtil,
    private val iobCobCalculator: IobCobCalculator
) {

    private var lastBolusWizard: BolusWizard? = null
    private var lastTransId: Int = -1

    fun processCommand(context: Context, controllerUuid: UUID, transactionId: Int, dict: PebbleDictionary) {
        val cmdTypeInt = dict.getInteger(PebbleKeys.KEY_CMD_TYPE)?.toInt() ?: return
        aapsLogger.debug(LTag.PEBBLE, "PebbleCommandProcessor: Received command code: {} for transId: {}", cmdTypeInt, transactionId)

        when (cmdTypeInt) {
            1 -> handleCalculateBolus(context, controllerUuid, transactionId, dict)
            2 -> handleConfirmBolus(context, controllerUuid, transactionId, dict)
            3 -> handleSetTempTarget(context, controllerUuid, transactionId, dict)
            4 -> handleCancelTempTarget(context, controllerUuid, transactionId)
            5 -> handleSnoozeAlarm(context, controllerUuid, transactionId)
            6 -> handleCancelActiveBolus(context, controllerUuid, transactionId)
            7 -> handleSetECarbs(context, controllerUuid, transactionId, dict)
            else -> aapsLogger.warn(LTag.PEBBLE, "PebbleCommandProcessor: Unknown command type {}", cmdTypeInt)
        }
    }

    private fun handleCalculateBolus(context: Context, controllerUuid: UUID, transactionId: Int, dict: PebbleDictionary) {
        val carbsInput = dict.getInteger(PebbleKeys.KEY_CARBS)?.toInt() ?: 0
        val percentage = dict.getInteger(PebbleKeys.KEY_PERCENTAGE)?.toInt() ?: 100
        val rawBgInput = dict.getInteger(PebbleKeys.KEY_BG)?.toDouble()

        val profile = profileFunction.getProfile() ?: run {
            sendErrorResponse(context, controllerUuid, transactionId, 1, "No active profile")
            return
        }

        val carbsAfterConstraints = constraintChecker.applyCarbsConstraints(ConstraintObject(carbsInput, aapsLogger)).value()
        val bgValue = if (rawBgInput != null) {
            if (profileFunction.getUnits() == GlucoseUnit.MMOLL && rawBgInput < 35) {
                rawBgInput * Constants.MMOLL_TO_MGDL
            } else {
                rawBgInput
            }
        } else {
            iobCobCalculator.ads.actualBg()?.valueToUnits(profileFunction.getUnits()) ?: 100.0
        }

        val cobInfo = iobCobCalculator.getCobInfo("PebbleCommandProcessor")
        val cob = cobInfo.displayCob ?: 0.0

        val wizard = bolusWizardProvider.get().doCalc(
            profile = profile,
            profileName = profileFunction.getProfileName(),
            tempTarget = persistenceLayer.getTemporaryTargetActiveAt(dateUtil.now()),
            carbs = carbsAfterConstraints,
            cob = cob,
            bg = bgValue,
            correction = 0.0,
            percentageCorrection = percentage,
            useBg = true,
            useCob = true,
            includeBolusIOB = true,
            includeBasalIOB = true,
            useSuperBolus = false,
            useTT = true,
            useTrend = true,
            useAlarm = false
        )

        lastBolusWizard = wizard
        lastTransId = transactionId

        val response = PebbleDictionary().apply {
            addInt32(PebbleKeys.KEY_CMD_TYPE, 1)
            addInt32(PebbleKeys.KEY_TRANS_ID, transactionId)
            addInt32(PebbleKeys.KEY_STATUS_CODE, 1) // PENDING_CONFIRMATION
            addInt32(PebbleKeys.KEY_CALC_RESULT, (wizard.calculatedTotalInsulin * 100).toInt())
            addString(PebbleKeys.KEY_MESSAGE, "Calculated: " + decimalFormatter.to2Decimal(wizard.calculatedTotalInsulin) + " U")
        }

        transport.sendData(context, controllerUuid, response)
    }

    private fun handleConfirmBolus(context: Context, controllerUuid: UUID, transactionId: Int, dict: PebbleDictionary) {
        val insulinRaw = dict.getInteger(PebbleKeys.KEY_INSULIN_AMOUNT)?.toDouble() ?: 0.0
        val amount = insulinRaw / 100.0
        val carbs = dict.getInteger(PebbleKeys.KEY_CARBS)?.toInt() ?: 0
        val carbTimeOffset = dict.getInteger(PebbleKeys.KEY_CARB_TIME_OFFSET)?.toInt() ?: 0

        val insulinAfterConstraints = constraintChecker.applyBolusConstraints(ConstraintObject(amount, aapsLogger)).value()
        if (abs(insulinAfterConstraints - amount) > 0.001) {
            sendErrorResponse(context, controllerUuid, transactionId, 2, "Bolus constraint error")
            return
        }

        val carbsTime = dateUtil.now() + (carbTimeOffset * 60000L)
        doBolus(amount, carbs, carbsTime)

        lastBolusWizard = null

        val response = PebbleDictionary().apply {
            addInt32(PebbleKeys.KEY_CMD_TYPE, 2)
            addInt32(PebbleKeys.KEY_TRANS_ID, transactionId)
            addInt32(PebbleKeys.KEY_STATUS_CODE, 0) // SUCCESS
            addString(PebbleKeys.KEY_MESSAGE, "Bolus " + decimalFormatter.to2Decimal(amount) + " U Sent")
        }

        transport.sendData(context, controllerUuid, response)
    }

    private fun handleSetTempTarget(context: Context, controllerUuid: UUID, transactionId: Int, dict: PebbleDictionary) {
        val presetType = dict.getInteger(PebbleKeys.KEY_PRESET_TYPE)?.toInt() ?: 0
        var duration = 0
        var targetBg = 100.0

        when (presetType) {
            1 -> { // EATING_SOON
                duration = preferences.get(IntKey.OverviewEatingSoonDuration)
                targetBg = preferences.get(UnitDoubleKey.OverviewEatingSoonTarget)
            }
            2 -> { // ACTIVITY
                duration = preferences.get(IntKey.OverviewActivityDuration)
                targetBg = preferences.get(UnitDoubleKey.OverviewActivityTarget)
            }
            3 -> { // HYPO
                duration = preferences.get(IntKey.OverviewHypoDuration)
                targetBg = preferences.get(UnitDoubleKey.OverviewHypoTarget)
            }
            4 -> { // CANCEL
                persistenceLayer.cancelTempTargets(dateUtil.now())
                sendSuccessResponse(context, controllerUuid, transactionId, 3, "Temp Target Cancelled")
                return
            }
            else -> { // MANUAL
                duration = dict.getInteger(PebbleKeys.KEY_DURATION)?.toInt() ?: 30
                targetBg = dict.getInteger(PebbleKeys.KEY_TEMP_TARGET_BG)?.toDouble() ?: 100.0
            }
        }

        val tt = TempTarget().apply {
            this.duration = duration
            this.lowTarget = targetBg
            this.highTarget = targetBg
            this.timestamp = dateUtil.now()
        }

        persistenceLayer.setTempTarget(tt)
        sendSuccessResponse(context, controllerUuid, transactionId, 3, "Temp Target Set")
    }

    private fun handleCancelTempTarget(context: Context, controllerUuid: UUID, transactionId: Int) {
        persistenceLayer.cancelTempTargets(dateUtil.now())
        sendSuccessResponse(context, controllerUuid, transactionId, 4, "Temp Target Cancelled")
    }

    private fun handleSnoozeAlarm(context: Context, controllerUuid: UUID, transactionId: Int) {
        uiInteraction.stopAlarm("Muted from Pebble")
        sendSuccessResponse(context, controllerUuid, transactionId, 5, "Alarm Muted")
    }

    private fun handleCancelActiveBolus(context: Context, controllerUuid: UUID, transactionId: Int) {
        activePlugin.activePump.stopBolusDelivering()
        sendSuccessResponse(context, controllerUuid, transactionId, 6, "Bolus Stopped")
    }

    private fun handleSetECarbs(context: Context, controllerUuid: UUID, transactionId: Int, dict: PebbleDictionary) {
        val carbs = dict.getInteger(PebbleKeys.KEY_CARBS)?.toInt() ?: 0
        val durationHours = dict.getInteger(PebbleKeys.KEY_DURATION)?.toInt() ?: 3
        val timeOffsetMins = dict.getInteger(PebbleKeys.KEY_CARB_TIME_OFFSET)?.toInt() ?: 0
        val eventTime = dateUtil.now() + (timeOffsetMins * 60000L)

        val detailedBolusInfo = DetailedBolusInfo().apply {
            this.carbs = carbs.toDouble()
            this.carbsDuration = T.hours(durationHours.toLong()).msecs()
            this.carbsTimestamp = eventTime
        }

        commandQueue.bolus(detailedBolusInfo, object : Callback() {})
        sendSuccessResponse(context, controllerUuid, transactionId, 7, "eCarbs Logged")
    }

    private fun doBolus(amount: Double, carbs: Int, carbsTime: Long) {
        val detailedBolusInfo = DetailedBolusInfo().apply {
            this.insulin = amount
            this.carbs = carbs.toDouble()
            this.carbsTimestamp = carbsTime
        }
        uel.log(
            action = Action.TREATMENT,
            source = Sources.Wear,
            listValues = listOfNotNull(
                ValueWithUnit.Insulin(amount).takeIf { amount != 0.0 },
                ValueWithUnit.Gram(carbs).takeIf { carbs != 0 }
            )
        )
        commandQueue.bolus(detailedBolusInfo, object : Callback() {})
    }

    private fun sendSuccessResponse(context: Context, controllerUuid: UUID, transactionId: Int, cmdType: Int, message: String) {
        val response = PebbleDictionary().apply {
            addInt32(PebbleKeys.KEY_CMD_TYPE, cmdType)
            addInt32(PebbleKeys.KEY_TRANS_ID, transactionId)
            addInt32(PebbleKeys.KEY_STATUS_CODE, 0) // SUCCESS
            addString(PebbleKeys.KEY_MESSAGE, message)
        }
        transport.sendData(context, controllerUuid, response)
    }

    private fun sendErrorResponse(context: Context, controllerUuid: UUID, transactionId: Int, cmdType: Int, errorMessage: String) {
        val response = PebbleDictionary().apply {
            addInt32(PebbleKeys.KEY_CMD_TYPE, cmdType)
            addInt32(PebbleKeys.KEY_TRANS_ID, transactionId)
            addInt32(PebbleKeys.KEY_STATUS_CODE, 2) // ERROR
            addString(PebbleKeys.KEY_MESSAGE, errorMessage)
        }
        transport.sendData(context, controllerUuid, response)
    }
}
