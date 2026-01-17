# ANDROIDAPS — Pebble Plugin Implementation — `todo.md`

**TL;DR**
Implement a new `plugins/pebble` module to uni-directionally sync loop data (BG, IOB, COB) to Pebble smartwatches via `PebbleKit`, mirroring the `WearPlugin` trigger architecture.

## Invariants (do not change)
1.  **UUID Consistency**: The UUID used in `PebbleKit.sendDataToPebble` **must** match the UUID defined in the target Pebble Watchapp's `package.json`.
2.  **Non-Blocking Transport**: All `PebbleKit` interactions (sending data, checking connection) must occur on background threads (IO Scheduler) to prevent Main Thread ANRs.
3.  **One-Way Sync (MVP)**: The implementation is strictly Phone-to-Watch. No command reception (Watch-to-Phone) is supported in this iteration.
4.  **License Compliance**: Must respect AAPS licensing and PebbleKit distribution rights.

## Assumptions & Scope
*   **Assumption**: Target Pebble Watchapp UUID is `54D3008F-0E46-46AC-9634-93D0D7130000` (Placeholder `{{PEBBLE_APP_UUID}}`).
*   **Assumption**: `com.getpebble:pebblekit:4.0.1` is available in Maven Central or local libs.
*   **Assumption**: `EventLoopUpdateGui` (via RxBus) is the canonical trigger for fresh loop data, consistent with `WearPlugin`.
*   **Scope**:
    *   Creation of `:plugins:pebble` module.
    *   Transmission of BG, Trend, IOB, COB, and Timestamp.
    *   Integration into AAPS Config Builder and Tab interface.
*   **Out of Scope**: Installing the watchface, Bi-directional communication (Bolus/Wizard), Historical graph data.

## Objectives
1.  **Isolation**: Deliver functionality as a standalone Gradle module (`:plugins:pebble`) with minimal core intrusion.
2.  **Reliability**: Ensure `PebbleKit` communication handles connection failures gracefully without crashing AAPS.
3.  **Efficiency**: Throttle updates to prevent watch battery drain (max 1 update per minute).
4.  **Visibility**: Provide a configuration UI (Plugin Tab) showing connection status and "Last Sent" timestamp.

## Risks & Mitigations
*   **Risk**: `PebbleKit` dependency causes build issues or runtime crashes on modern Android (12+).
    *   **Mitigation**: Wrap all `PebbleKit` calls in `try-catch` blocks specifically handling `SecurityException` (Bluetooth permissions) and `IllegalArgumentException`.
*   **Risk**: Data overload (Dictionary size limit).
    *   **Mitigation**: Strictly limit payload to defined keys. Do not serialize full JSON objects.
*   **Risk**: Main thread blocking during IPC.
    *   **Mitigation**: Enforce `RxBus.observeOn(Schedulers.io())` for the event consumer.

## Method Outline (idea → mechanism → trade-offs → go/no-go)
1.  **Mechanism**:
    *   Create `PebblePlugin` implementing `IPlugin` and `ConfigBuilderFunction`.
    *   Subscribe to `EventLoopUpdateGui`.
    *   On event: Check connection -> Gather Data (`StaticInjector` / `EnrichedLoopData`) -> Map to `PebbleDictionary` -> Send via `PebbleKit`.
2.  **Trade-offs**:
    *   *Polling vs Event*: Event-based (`EventLoopUpdateGui`) is chosen to sync with UI updates, matching user expectation.
    *   *Data Precision*: Doubles (IOB/COB) will be scaled to Integers (x100) or Strings for display, as `PebbleDictionary` primarily supports Int/String/Bytes.
3.  **Go/No-Go**: **Go**. The plugin architecture allows safe experimentation without destabilizing the core.

## Implementation Notes
*   **Dependencies**:
    *   `implementation("com.getpebble:pebblekit:4.0.1")`
    *   `implementation(project(":core:interfaces"))`
    *   `implementation(project(":core:data"))`
*   **Data Contract (`PebbleKeys`)**:
    *   `0`: BG (String or Int)
    *   `1`: Trend (String/Int)
    *   `2`: IOB (Int, scaled x100)
    *   `3`: COB (Int)
    *   `4`: Timestamp (Int/Long)
*   **Attach Point**: `app/src/main/java/.../ConfigBuilder.kt` (or equivalent DI module) to register the plugin.

## Acceptance Gates
*   [ ] Module `:plugins:pebble` compiles and tests pass.
*   [ ] "Pebble" appears in the Config Builder plugin list.
*   [ ] Enabling the plugin adds a "Pebble" tab to the main pager.
*   [ ] `EventLoopUpdateGui` triggers a log entry in `PebblePlugin`.
*   [ ] Mocked `PebbleKit` receives a populated `PebbleDictionary` with correct BG/IOB values.

## "Make-sure-you" Checklist
*   [ ] **Do** check `PebbleKit.isWatchConnected(context)` before doing work.
*   [ ] **Do** scale floating point numbers (IOB 1.5 -> 150) before adding to dictionary if using `addInt32`.
*   [ ] **Do not** use `android.util.Log` directly; use `AAPSLogger`.
*   [ ] **Do** handle `null` values from `OverviewData` (e.g., if no sensor is connected).
*   [ ] **Do** add `<uses-permission android:name="com.getpebble.provider.ACCESS" />` (or equivalent if required by SDK) to manifest.

## Project hygiene prep
1.  **Git**: Create branch `feat/plugin-pebble`.
2.  **Filesystem**: Create directory structure `plugins/pebble/src/main/kotlin/app/aaps/plugins/pebble`.
3.  **Gradle**: Add `:plugins:pebble` to `settings.gradle.kts` and `app/build.gradle.kts`.

## In-depth test plan

### 1. Unit Testing (`plugins/pebble/src/test/...`)
*   **`PebbleDataMapperTest`**:
    *   *Scenario*: Input `EnrichedLoopData` with BG=120, IOB=2.35.
    *   *Check*: `PebbleDictionary` contains key `0` value `120`, key `2` value `235` (assuming x100 scaling).
    *   *Scenario*: Input `EnrichedLoopData` with `null` BG.
    *   *Check*: Dictionary contains `0` value `0` or does not contain key `0`.
*   **`PebblePluginTest`**:
    *   *Scenario*: `EventLoopUpdateGui` received but `isEnabled` is false.
    *   *Check*: `PebbleKit.sendDataToPebble` is **not** called.

### 2. Integration Checks
*   **ConfigBuilder**: Verify the plugin shows up in the list and persists its state (Enabled/Disabled) across restarts.
*   **Tab Rendering**: Verify the fragment loads without crashing when the plugin is enabled.

## In-depth engineering plan

### Phase 1: Module Setup
1.  Create `plugins/pebble/build.gradle.kts`:
    ```kotlin
    plugins {
        id("com.android.library")
        id("kotlin-android")
    }
    dependencies {
        implementation(project(":core:interfaces"))
        implementation(project(":core:data"))
        implementation(project(":shared:impl")) // For RxBus
        implementation("com.getpebble:pebblekit:4.0.1")
        // ... test dependencies
    }
    ```
2.  Update `settings.gradle.kts` (if manual include required) and `app/build.gradle.kts` to `implementation(project(":plugins:pebble"))`.

### Phase 2: Data & Mapper
1.  Create `app.aaps.plugins.pebble.data.PebbleKeys`: Define integer constants matching the JSON contract.
2.  Create `app.aaps.plugins.pebble.util.PebbleDataMapper`:
    *   `fun map(data: EnrichedLoopData): PebbleDictionary`
    *   Implement scaling logic for IOB/COB.
    *   Handle BG unit conversion if necessary (AAPS usually works in mg/dl internally, normalize here).

### Phase 3: Plugin Implementation
1.  Create `app.aaps.plugins.pebble.PebblePlugin`:
    *   Annotate `@Singleton`? (Check DI pattern).
    *   Inherit `PluginBase`, `IPlugin`, `ConfigBuilderFunction`.
    *   Inject `RxBus`, `AAPSLogger`, `Context`.
2.  **Initialization**:
    *   In `initialize()`, `rxBus.register(EventLoopUpdateGui::class.java)`.
3.  **Event Handling**:
    *   `onEvent(EventLoopUpdateGui)`:
        *   `if (!isEnabled) return`
        *   `if (!PebbleKit.isWatchConnected(context)) return`
        *   `val dict = mapper.map(gatherData())`
        *   `PebbleKit.sendDataToPebble(context, UUID, dict)`
        *   `logger.debug(TAG, "Sent update to Pebble")`

### Phase 4: UI Components
1.  Create `app.aaps.plugins.pebble.ui.PebbleFragment`:
    *   Simple layout with `TextView`s for Connection Status and Last Update time.
    *   Subscribe to updates (can reuse `EventLoopUpdateGui` or a local event) to refresh the view.
2.  Update `PebblePlugin`:
    *   Implement `getTab()` returning `PebbleFragment`.

### Phase 5: Registration
1.  Locate `ConfigBuilder` logic (likely in `plugins/configuration` or `app` module).
2.  Add `PebblePlugin` to the list of available plugins.
3.  Ensure Dependency Injection (Dagger) knows about `PebblePlugin`.

### Phase 6: Final Polish
1.  Review `AndroidManifest.xml` (auto-merged) for any permission requirements.
2.  Run `./gradlew :plugins:pebble:testDebugUnitTest`.
3.  Build full app and verify no linkage errors.
