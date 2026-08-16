# Pebble Watchface Integration — Expanded Protocol Staging & TDD Plan

## TL;DR
Implement the expanded Pebble data sync protocol containing IOB details, COB, active basal rate, target lines, history logs, and units to mirror the Wear OS telemetry and format standards using a strict test-driven development workflow.

---

## 1. Invariants (do not change)
1. **TDD First**: All changes to business logic must be driven by writing failing tests (RED) and then implementing code to make them pass (GREEN).
2. **Thread Safety**: All PebbleKit transmissions must execute on the `aapsSchedulers.io` thread.
3. **Explicit Logging Channel**: All plugin logs must use `LTag.PEBBLE`.
4. **C-Safe Data Ranges**: The `TREND` value must be mapped to a safe integer ordinal corresponding to `TrendArrow` (0–9) to prevent array index out-of-bounds in Pebble's C runtime.

---

## 2. Assumptions & Scope
* **Watchface Assumption**: The Pebble watchface handles receiving keys `0` through `12` as specified in the protocol description.
* **Scope**: Support both `mg/dL` and `mmol/L` units by sending the `UNITS` key (`0` = mg/dL, `1` = mmol/L) and scaling the raw target bounds. History data uses raw values divided by `2` in `mg/dL`.

---

## 3. Objectives
1. **Expand Key definitions**: Add Keys 5 through 12 to `PebbleKeys.kt`.
2. **Scale targets and values**: Extract targets converted to `mg/dL` and supply the display unit configuration.
3. **Serialize history**: Send a 36-point history byte array scaled by dividing each value by 2.
4. **Clean Dependency Injection**: Inject all required telemetry interfaces into the `PebblePlugin` constructor.

---

## 4. Risks & Mitigations
* **Risk**: Missing dependencies (like `ProfileFunction` or `Preferences`) causing instantiation or compile failures.
  * *Mitigation*: Leverage Dagger DI bindings from the main graph directly in the Pebble module.
* **Risk**: Watchface crashing on zero value history entries.
  * *Mitigation*: Document that empty slots default to `0` representing missing telemetry.

---

## 5. Implementation Notes
* **AppMessage Protocol Keys**:
  * `0` (BG): Integer in mg/dL.
  * `1` (TREND): Integer ordinal (0 to 9) representing `TrendArrow` enum values.
  * `2` (IOB): String (e.g. `"0.32 U"`).
  * `3` (COB): String (e.g. `"0g"`).
  * `4` (TIME): Int32 representation of unix timestamp (seconds).
  * `5` (BASAL): String (e.g. `"0.90"`).
  * `6` (IOB_DETAIL): String (e.g. `"(0.02|0.31)"`).
  * `7` (DELTA): String (e.g. `"+3"` or `"+0.2"`).
  * `8` (AVG_DELTA): String (e.g. `"+5"` or `"+0.3"`).
  * `9` (GLUCOSE_HISTORY): Byte Array (36 bytes representing BG/2).
  * `10` (LOW_TARGET): Int32 low target mark in mg/dL.
  * `11` (HIGH_TARGET): Int32 high target mark in mg/dL.
  * `12` (UNITS): Int32 unit setting (`0` = mg/dL, `1` = mmol/L).

---

## 6. Acceptance Gates
* **Gate 1**: `./gradlew :plugins:pebble:test` compiles and passes all unit tests.
* **Gate 2**: All keys (0 through 12) are populated correctly in the mapping test suite.

---

## 7. In-depth Test Plan
* **Test Stage A (Data Mapping)**:
  * Create/update unit tests verifying mapping of the expanded fields (`basal`, `iobDetail`, `delta`, `avgDelta`, `lowTarget`, `highTarget`, `units`, and `history` byte array).
* **Test Stage B (Telemetry Extraction)**:
  * Verify `PebblePlugin` extracts and populates values on GUI update events.

---

## 8. In-depth Engineering Plan

### Step 1: Update PebbleKeys and EnrichedData
Add keys 5-12 to [`PebbleKeys.kt`](file:///home/clark/StudioProjects/AndroidAPS/plugins/pebble/src/main/kotlin/app/aaps/plugins/pebble/PebbleKeys.kt) and properties to [`EnrichedData.kt`](file:///home/clark/StudioProjects/AndroidAPS/plugins/pebble/src/main/kotlin/app/aaps/plugins/pebble/data/EnrichedData.kt).

### Step 2: Implement TDD for Data Mapping
Update [`PebbleDataMapperTest.kt`](file:///home/clark/StudioProjects/AndroidAPS/plugins/pebble/src/test/kotlin/app/aaps/plugins/pebble/PebbleDataMapperTest.kt) to test mapping of all new fields. Update [`PebbleDataMapper.kt`](file:///home/clark/StudioProjects/AndroidAPS/plugins/pebble/src/main/kotlin/app/aaps/plugins/pebble/PebbleDataMapper.kt) to pass.

### Step 3: Implement Telemetry Extraction and Injection
Modify [`PebblePlugin.kt`](file:///home/clark/StudioProjects/AndroidAPS/plugins/pebble/src/main/kotlin/app/aaps/plugins/pebble/PebblePlugin.kt) to inject `ProfileFunction`, `ProfileUtil`, `Preferences`, and `ProcessedTbrEbData`. Retrieve and format values matching Wear OS. Add local `deltaString` formatter logic.

### Step 4: Run Verification Tests
Execute `./gradlew :plugins:pebble:test` to confirm code correctness and test coverage.
