# Pebble Watch Integration — MVP Design & TDD Plan

## TL;DR
Implement a test-driven, one-way MVP Pebble plugin to sync blood glucose (mg/dL) and trend arrows via PebbleKit, with robust logging and dynamic receiver lifecycles.

---

## 1. Invariants (do not change)
1. **TDD First**: All changes to logic must be driven by writing failing tests (RED) and then implementing code to make them pass (GREEN).
2. **Thread Safety**: All PebbleKit transmissions must execute on the `aapsSchedulers.io` thread.
3. **Explicit Logging Channel**: All plugin logs must use `LTag.PEBBLE`.
4. **C-Safe Data Ranges**: The `TREND` value must be a safe, positive integer ordinal mapping to `TrendArrow` (0–9) to prevent out-of-bounds array access in Pebble's C runtime.

---

## 2. Architectural Alignment with AndroidAPS
To ensure the Pebble integration is maintainable and blends naturally with the existing AndroidAPS codebase, the following patterns are adhered to:

- **Plugin Lifecycle & Dagger injection**: The Pebble plugin inherits from [`PluginBase`](file:///home/clark/StudioProjects/AndroidAPS/core/interfaces/src/main/kotlin/app/aaps/core/interfaces/plugin/PluginBase.kt) and is registered in the dependency graph via [`PluginsListModule`](file:///home/clark/StudioProjects/AndroidAPS/app/src/main/kotlin/app/aaps/di/PluginsListModule.kt) under `@AllConfigs @IntoMap @IntKey(365)`. This is the identical pattern used by the Wear OS and Tizen plugins.
- **Logging System**: AAPS uses an enum-based logging filter system ([`LTag.kt`](file:///home/clark/StudioProjects/AndroidAPS/core/interfaces/src/main/kotlin/app/aaps/core/interfaces/logging/LTag.kt)). By channelling all of our logs through `LTag.PEBBLE`, we allow users and developers to toggle Pebble logging on/off dynamically from AAPS's "Maintenance & Logging" menu.
- **Preferences Isolation**: In AAPS, core app settings are mapped via a type-safe `Preferences` enum-based key wrapper. However, standalone plugins frequently inject standard Android `SharedPreferences` directly to store custom settings (such as the target UUID in [`TargetUuidProvider`](file:///home/clark/StudioProjects/AndroidAPS/plugins/pebble/src/main/kotlin/app/aaps/plugins/pebble/TargetUuidProvider.kt)). This keeps the Pebble plugin isolated in its library module without cluttering the global database enum namespace.
- **Reactive Scheduling**: Like the Wear OS and Tizen plugins, we listen to the AAPS event bus via `rxBus.toObservable(EventLoopUpdateGui::class.java)` and force all processing/transport broadcasts to execute off the main thread via `observeOn(aapsSchedulers.io)`.

---

## 3. PebbleKit Android SDK & Web Best Practices
The protocol design takes into consideration standard constraints from the PebbleKit Android SDK and watchface performance considerations:

- **AppMessage Protocol Constraint**:
  - AppMessages are restricted to standard integer types (`int8`, `int16`, `int32` signed/unsigned), strings, or byte arrays.
  - Pebble has **no native floating-point support** inside AppMessage packets. This aligns with our design choice of multiplying floating values like IOB/COB by `100` to represent them as integers, or sending formatted text strings.
- **Intent-Based Relay**:
  - PebbleKit Android communicates with the official Pebble runtime (or Gadgetbridge / Rebble app) using broadcast intents (`com.getpebble.action.app.SEND`). 
  - The phone-side Pebble app is what serializes the intent payload to binary data, maintains the Bluetooth RFCOMM link, and handles packet retransmission. Therefore, our Android module does not need complex Bluetooth socket management.
- **Reliability (ACK/NACK and Transaction IDs)**:
  - PebbleKit requires the watch to send an ACK/NACK back for every message received to prevent protocol timeouts.
  - To properly map responses, we use `PebbleKit.registerReceivedAckHandler` and `PebbleKit.registerReceivedNackHandler` targeting the watchface's UUID.
  - By subscribing to dynamic changes in the UUID, we ensure that if a user switches watchfaces, we unregister old broadcast receivers to prevent intent leaks.

---

## 4. Assumptions & Scope
- **Watchface Assumption**: The Pebble watchface has a matching UUID and listens to keys `0` (BG integer), `1` (Trend arrow index), and `4` (Time in seconds).
- **Scope Limitations**: 
  - Units are strictly limited to `mg/dL` for this MVP.
  - IOB and COB keys will be omitted or disabled.
  - Bi-directional commands (such as bolusing from watch) are out of scope.
  - The phone-side companion app manages state, and connection status is handled via `PebbleKit.isWatchConnected(context)`.

---

## 5. Objectives
1. **Correct Trend Mapping**: Map `PebbleKeys.TREND` to the actual `TrendArrow.ordinal` (0-9) instead of raw BG delta to prevent watch crashes.
2. **Verify Watch Connection**: Log and check watch connection status prior to transmission, skipping send when disconnected.
3. **Dynamic UUID Support**: Automatically unregister and re-register ACK/NACK broadcast receivers when the app UUID is updated in the settings fragment.
4. **Verbose Logging**: Ensure comprehensive logging of all data payloads, connection checks, and ACK/NACK events using `LTag.PEBBLE`.

---

## 6. Risks & Mitigations
- **Risk 1**: User inputs malformed UUIDs or trailing spaces, crashing the plugin or fragment.
  - *Mitigation*: Call `.trim()` on the input string and catch `IllegalArgumentException` in the settings fragment.
- **Risk 2**: Log spamming when the watch is disconnected.
  - *Mitigation*: Log connection failures at the `info` or `debug` level (not `warn`/`error`) to keep log files clean.

---

## 7. Method Outline
- **Concept**: Sync blood glucose to Pebble watch.
- **Mechanism**: Subscribe to `EventLoopUpdateGui` -> check Bluetooth connection status -> retrieve last blood glucose and trend arrow -> build Pebble Dictionary -> send to Pebble via Android Intent.
- **Trade-offs**: Simple, one-way push minimizes battery drain and removes complex watchface state syncing, but prevents the watch from querying historical graphs or entering pump commands.

---

## 8. Implementation Notes
- **AppMessage Protocol Keys**:
  - Key `0` (BG): Integer in mg/dL.
  - Key `1` (Trend): Integer ordinal (0 to 9) representing `TrendArrow` enum values.
  - Key `4` (Time): Int32 representation of unix timestamp (seconds).
- **Logging**:
  - AAPS users can toggle log categories. The Pebble plugin will write logs under `LTag.PEBBLE`, enabling deep debugging by selecting the `PEBBLE` channel in AAPS settings.
  - Log events:
    - Data package preparation details (e.g. `Sending BG: 120, Trend: 5 (FLAT)`).
    - Watch connection state updates (connected vs disconnected).
    - Received ACK/NACK transaction IDs.

---

## 9. Acceptance Gates
- **Gate 1**: `./gradlew :plugins:pebble:test` compiles and passes 100% of unit tests.
- **Gate 2**: Changing the UUID in `PebbleFragment` automatically triggers receiver updates without memory leaks.
- **Gate 3**: The Pebble log channel (`LTag.PEBBLE`) outputs verbose debug statements.

---

## 10. "Make-sure-you" Checklist
- [ ] **Do** use `iobCobCalculator.ads.lastBg()` to fetch the correct `trendArrow`.
- [ ] **Do** unregister old ACK/NACK receivers in the `SharedPreferences` change listener prior to registering new ones.
- [ ] **Do** trim string inputs in the fragment.
- [ ] **Do** call `dict.addInt32()` for time after dividing milliseconds by 1000.

---

## 11. In-depth Test Plan

### Test Workstream A: Data Mapping (TDD)
- **Test A1 (`testMap_populatesBgTrendTime_withCorrectTrendArrow`)**: Verify that `PebbleDataMapper` correctly converts `EnrichedData.trend` ordinal to Key `1`, and ignores IOB/COB if they are null.
- **Test A2 (`testMap_ignoresNullBgAndTrend`)**: Ensure no crash occurs and only `TIME` is populated when BG and Trend are null.

### Test Workstream B: Orchestration & Connectivity (TDD)
- **Test B1 (`testOnEvent_skipsSend_whenWatchDisconnected`)**: Mock `isWatchConnected` to return `false` and verify `transport.sendData` is never called.
- **Test B2 (`testOnEvent_sendsCorrectBgAndTrendArrowOrdinal`)**: Mock `lastBg` returning a specific `TrendArrow` (e.g., `SINGLE_UP`), verify the mapped ordinal `3` is passed.

### Test Workstream C: Lifecycle & UUID Dynamic Re-registration (TDD)
- **Test C1 (`testUuidChange_unregistersAndReRegistersHandlers`)**: Verify that updating the target UUID in shared preferences triggers unregistering the old receivers and registering new ones with the new UUID.

---

## 12. In-depth Engineering Plan

### Step 1: Update `EnrichedData` structure
Modify `plugins/pebble/src/main/kotlin/app/aaps/plugins/pebble/data/EnrichedData.kt` to represent the correct Trend Arrow ordinal rather than delta.
```kotlin
data class EnrichedData(
    val bg: Double?,
    val trend: Int?, // Trend Arrow Ordinal (0-9)
    val time: Long
)
```

### Step 2: Implement TDD for Data Mapping (`PebbleDataMapperTest.kt` & `PebbleDataMapper.kt`)
1. Update `PebbleDataMapperTest.kt` (RED) to verify that `EnrichedData` mapping ignores IOB and COB keys.
2. Modify `PebbleDataMapper.kt` (GREEN) to remove IOB and COB serialization code, and confirm it only maps BG, TREND, and TIME.

### Step 3: Implement TDD for UUID Change and Connection (`PebblePluginTest.kt` & `PebblePlugin.kt`)
1. Add test case for watch disconnection checking (RED).
2. Add test case verifying re-registration of receivers on UUID update (RED).
3. In `PebblePlugin.kt` (GREEN):
   - Listen to preference changes using `SharedPreferences.OnSharedPreferenceChangeListener`.
   - Update `sendData()` to log connection state at the `debug` level, and retrieve `trendArrow` ordinal via `iobCobCalculator.ads.lastBg()?.trendArrow?.ordinal`.
   - When UUID preference changes, trigger unregistering and registering receivers.

### Step 4: Fix UI Whitespace Input (`PebbleFragment.kt`)
1. Update `PebbleFragment.kt` to trim input:
   ```kotlin
   val uuidString = binding.pebbleUuid.text.toString().trim()

---

## 13. Addendum — MVP Execution Adjustments

During the implementation of the one-way MVP Pebble watch integration, the following adjustments and clarifications were made from the initial design options:

1. **Protocol Scope Restriction**:
   - Although the Code Review (`pebble_plugin_review.md`) recommended adding `BG_STRING` (Key 5) and `UNITS` (Key 6) for mmol/L users, these were omitted from the MVP to keep the protocol minimal and focused. The MVP strictly broadcasts:
     - `BG` (Key 0): Integer in mg/dL.
     - `TREND` (Key 1): Integer ordinal (0–9) representing `TrendArrow` enum values.
     - `TIME` (Key 4): Int32 representation of unix timestamp (seconds).
   - IOB and COB keys (Keys 2 and 3) were removed from `EnrichedData` and `PebbleDataMapper` serialization to adhere to the MVP's strict one-way requirement.

2. **Clean Dependency Injection of SharedPreferences**:
   - Rather than using static calls to `PreferenceManager.getDefaultSharedPreferences(context)` inside the plugin, the `SharedPreferences` instance is injected directly into `PebblePlugin`'s constructor. This aligns with AndroidAPS's clean dependency architecture and allowed us to easily mock the preference registry during testing.

3. **Dynamic UUID Change Test Coverage**:
   - Added `testUuidChange_unregistersAndReRegistersHandlers` to verify that when `pebble_app_uuid` changes, the plugin unregisters its old `BroadcastReceiver` handlers and starts listening on the new UUID. Mocks for the `TargetUuidProvider` and `IPebbleTransport` receivers were added to the test setup to prevent lifecycle crash scenarios during testing.
   ```
2. Verify invalid UUID format triggers a toast, and correct UUID persists.
