# ANDROIDAPS — Pebble Plugin Implementation — `todo.md`

**TL;DR**
Implement a new `plugins/pebble` module to uni-directionally sync loop data (BG, IOB, COB) to Pebble smartwatches via `PebbleKit`. Development follows a strict **Iterative TDD (Red-Green-Refactor)** process.

## Invariants
1.  **Thread Safety**: All `PebbleKit` interactions must occur on `Schedulers.io()`.
2.  **TDD First**: Logic is defined by failing tests (RED) before implementation (GREEN).
3.  **One-Way Sync (MVP)**: Strictly Phone-to-Watch.

## Iterative Implementation Plan

### Step 1: Module Skeleton & Build Configuration
**Goal**: Establish the module structure and ensure compilation.
*   [ ] **Action**: Create directory `plugins/pebble`.
*   [ ] **Action**: Create `plugins/pebble/build.gradle.kts`.
    *   Apply `android-library`, `kotlin-android`.
    *   Dependencies: `core:interfaces`, `core:data`, `shared:impl`, `core:ui`, `com.getpebble:pebblekit:4.0.1`.
*   [ ] **Action**: Register module in `settings.gradle.kts` and `app/build.gradle.kts`.
*   [ ] **Verification**: `./gradlew :plugins:pebble:assembleDebug` completes successfully.

### Step 2: Data Mapping Logic (Pure Kotlin TDD)
**Goal**: Implement logic to transform AAPS `EnrichedData` into a `PebbleDictionary` structure.
*   [ ] **RED (Test)**: Create `PebbleDataMapperTest`.
    *   `testMap_populatesBgTrendIobCob()`: Verify keys 0 (BG), 1 (Trend), 2 (IOB), 3 (COB), 4 (Time).
    *   `testMap_scalesFloatingPointValues()`: Verify IOB 1.5 -> 150.
    *   `testMap_handlesNullValues()`: Ensure no crash if BG is null.
*   [ ] **GREEN (Impl)**: Create `PebbleDataMapper`.
    *   Implement `map(data: EnrichedData): PebbleDictionary`.
*   [ ] **Refactor**: Extract keys to `PebbleKeys` object.

### Step 3: UUID Configuration Logic (Pure Kotlin TDD)
**Goal**: encapsulate logic for validating and retrieving the target Watch UUID.
*   [ ] **RED (Test)**: Create `TargetUuidProviderTest`.
    *   `testDefaultUuid_isReturned_whenPreferenceEmpty()`: Default `54D3008F...`.
    *   `testParsedUuid_isReturned_whenValid()`: UUID.fromString logic.
    *   `testDefaultUuid_isReturned_whenInvalid()`: Exception handling for malformed strings.
*   [ ] **GREEN (Impl)**: Create `TargetUuidProvider`.
    *   Inject `SharedPreferences`.

### Step 4: Transport Layer Abstraction (Isolation)
**Goal**: Wrap the static `PebbleKit` to allow unit testing of the sending logic without a real device/library shim.
*   [ ] **Action**: Define interface `IPebbleTransport`.
    *   `fun sendData(context: Context, uuid: UUID, data: PebbleDictionary)`
*   [ ] **Action**: Implement `PebbleTransportImpl` (Real implementation wrapping `PebbleKit`).

### Step 5: Plugin Orchestration (The "Loop")
**Goal**: Connect the RxBus event to the Data Mapper and Transport.
*   [ ] **RED (Test)**: Create `PebblePluginTest`.
    *   Mock `RxBus`, `SharedPreferences`, `IPebbleTransport`, `PebbleDataMapper`.
    *   `testInitialize_subscribesTo_EventLoopUpdateGui()`.
    *   `testOnEvent_mapsData_andSendsToTransport()`.
    *   `testOnEvent_handlesExceptions_gracefully()`.
*   [ ] **GREEN (Impl)**: Implement `PebblePlugin` class.
    *   Implement `IPlugin`, `ConfigBuilderFunction`.
    *   Wire up the RxJava chain on `Schedulers.io()`.

### Step 6: UI - Configuration Fragment
**Goal**: Allow user to view and edit the Target UUID.
*   [ ] **Action**: Create `res/layout/pebble_fragment.xml`.
*   [ ] **Action**: Create `PebbleFragment`.
    *   Load UUID from `TargetUuidProvider`.
    *   On Save: Validate string (using `UUID.fromString` check), save to Prefs, show Toast.
*   [ ] **Action**: Link `PebblePlugin.getTab()` to this fragment.

### Step 7: Main App Integration
**Goal**: Enable the plugin in the main application.
*   [ ] **Action**: Add `PebblePlugin` to `ConfigBuilder` in `app/src/...`.
*   [ ] **Verification**: Launch app, enable Pebble plugin, verify tab appears.

## Verification Checklist (Manual)
*   [ ] **Config**: Open Pebble Tab -> Enter valid UUID -> Save -> Restart -> Verify persisted.
*   [ ] **Config**: Enter invalid UUID -> Save -> Verify error toast/no save.
*   [ ] **Transport**: With Pebble app installed (or simulated), verify logcat shows "Sending data to <UUID>" on loop update.

## "Make-sure-you" Checklist
*   [ ] **Do** use `Observable.fromCallable` for the PebbleKit call to ensure it runs on the IO scheduler.
*   [ ] **Do** catch `IllegalArgumentException` when parsing user-input UUIDs.
*   [ ] **Do** keep the `PebbleKeys` indices stable (0=BG, 1=Trend, etc).
