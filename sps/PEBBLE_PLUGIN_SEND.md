# ANDROIDAPS — Pebble Plugin Implementation — `todo.md`

**TL;DR**
Implement a new `plugins/pebble` module to uni-directionally sync loop data (BG, IOB, COB) to Pebble smartwatches via `PebbleKit`, with a user-configurable UUID.

## Invariants
1.  **Thread Safety**: All `PebbleKit` interactions (sending data) must occur on `Schedulers.io()` to avoid Main Thread blocking.
2.  **UUID Consistency**: The target Watchapp UUID must be configurable by the user to support different watchfaces, defaulting to `54D3008F-0E46-46AC-9634-93D0D7130000`.
3.  **One-Way Sync (MVP)**: Strictly Phone-to-Watch. No command reception (Watch-to-Phone) logic is required for this iteration.

## Assumptions & Scope
*   **Assumption**: `com.getpebble:pebblekit:4.0.1` is resolvable.
*   **Assumption**: `EventLoopUpdateGui` (via RxBus) is the canonical trigger for fresh loop data (mirroring `WearPlugin`).
*   **Scope**:
    *   Module creation (`:plugins:pebble`).
    *   Transmission of BG, Trend, IOB, COB, Timestamp.
    *   Configuration UI (Tab) allowing UUID editing and connection status viewing.
*   **Out of Scope**: Installing watchfaces, history graphs, bi-directional commands.

## Objectives
1.  **Isolation**: Functionality exists entirely within `:plugins:pebble`.
2.  **Flexibility**: Users can change the target UUID in the plugin settings without recompiling.
3.  **Reliability**: Graceful handling of connection failures (no crashes).
4.  **Efficiency**: Throttle updates (max 1 per minute) to preserve battery.

## Risks & Mitigations
*   **Risk**: Invalid User Input for UUID causes crashes.
    *   **Mitigation**: Validate UUID string format in the UI before saving to `SharedPreferences`. Wrap `UUID.fromString()` in try-catch during send.
*   **Risk**: `PebbleKit` dependency issues on Android 14+.
    *   **Mitigation**: Wrap generic `PebbleKit` calls in try-catch blocks to handle potential `SecurityException` or `IllegalArgumentException`.
*   **Risk**: R8/ProGuard stripping `PebbleKit`.
    *   **Mitigation**: Add consumer ProGuard rules if the library doesn't include them.

## Method Outline
1.  **Mechanism**:
    *   `PebblePlugin` subscribes to `EventLoopUpdateGui`.
    *   On Event: Check `isEnabled` -> Gather Data (`OverviewData`, `IobCobCalculator`) -> Map to `PebbleDictionary`.
    *   Fetch Target UUID from `SharedPreferences`.
    *   Send via `PebbleKit`.
2.  **UI**:
    *   `PebbleFragment` provides a layout to view status and edit the Target UUID.

## Implementation Notes
*   **Dependencies**:
    *   `implementation("com.getpebble:pebblekit:4.0.1")`
    *   `implementation(project(":core:interfaces"))`
    *   `implementation(project(":core:data"))`
    *   `implementation(project(":shared:impl"))`
    *   `implementation(project(":core:ui"))`
*   **Data Contract (`PebbleKeys`)**:
    *   `0`: BG (Int)
    *   `1`: Trend (String/Int)
    *   `2`: IOB (Int, scaled x100)
    *   `3`: COB (Int)
    *   `4`: Timestamp (Int/Long)
*   **Preferences Keys**:
    *   `pebble_target_uuid`: String.

## Acceptance Gates
*   [ ] Module `:plugins:pebble` compiles.
*   [ ] "Pebble" appears in Config Builder.
*   [ ] Enabling plugin adds "Pebble" tab.
*   [ ] User can edit UUID in the tab; invalid UUIDs are rejected/not saved.
*   [ ] `EventLoopUpdateGui` triggers data send to the configured UUID.
*   [ ] `PebbleKit` receives correct BG/IOB values in mock tests.

## "Make-sure-you" Checklist
*   [ ] **Do** validate the UUID string in the UI (EditText watcher or Save button).
*   [ ] **Do** use `SharedPreference` default value `54D3008F-0E46-46AC-9634-93D0D7130000` if the pref is empty.
*   [ ] **Do** scale floats (IOB 1.5 -> 150) for integer transport.
*   [ ] **Do** wrap `PebbleKit` calls in `Observable.fromCallable { ... }.subscribeOn(Schedulers.io())`.

## Project hygiene prep
1.  **Git**: Branch `feat/plugin-pebble`.
2.  **Structure**: Create `plugins/pebble/src/main/kotlin/...` and `plugins/pebble/src/main/res/...`.
3.  **Gradle**: Update `settings.gradle.kts` and `app/build.gradle.kts`.

## In-depth test plan

### 1. Unit Tests (`plugins/pebble/src/test/...`)
*   **`PebbleDataMapperTest`**:
    *   Verify BG, IOB, COB mapping.
    *   Verify scaling logic (e.g., IOB 1.25 -> 125).
    *   Verify null handling (BG null -> 0 or omit key).
*   **`UUIDLogicTest`**:
    *   Test `UUID.fromString` robustness with whitespace/invalid chars.

### 2. Integration Checks
*   **UI Flow**: Open Tab -> Change UUID -> Save -> Restart App -> Verify UUID persisted.
*   **Transport**: Log verification that `sendDataToPebble` is called with the *new* UUID after it is changed.

## In-depth engineering plan

### Phase 1: Module Setup
1.  **Create Directory**: `plugins/pebble`.
2.  **Build Script** (`plugins/pebble/build.gradle.kts`):
    *   Plugins: `android-library`, `kotlin-android`.
    *   Deps: `core:interfaces`, `core:data`, `shared:impl`, `core:ui`, `pebblekit`.
3.  **Registration**:
    *   Add `include(":plugins:pebble")` to `settings.gradle.kts`.
    *   Add `implementation(project(":plugins:pebble"))` to `app/build.gradle.kts`.

### Phase 2: Data & Logic
1.  **Keys**: `object PebbleKeys { const val BG = 0 ... }`.
2.  **Mapper**: `class PebbleDataMapper @Inject constructor()`.
    *   `fun map(data: EnrichedData): PebbleDictionary`.
3.  **Plugin Class**: `class PebblePlugin @Inject constructor(...) : IPlugin, ConfigBuilderFunction`.
    *   Deps: `SharedPreferences`, `RxBus`, `OverviewData`, `IobCobCalculator`.
    *   `initialize()`: Subscribe to `EventLoopUpdateGui`.
    *   `onEvent`:
        ```kotlin
        val uuidStr = prefs.getString("pebble_target_uuid", DEFAULT_UUID)
        val uuid = UUID.fromString(uuidStr) // Wrap in try/catch fallback
        val dict = mapper.map(...)
        PebbleKit.sendDataToPebble(context, uuid, dict)
        ```

### Phase 3: UI Implementation
1.  **Resources**:
    *   `res/values/strings.xml`: Labels for "Target UUID", "Save", "Connection Status".
    *   `res/layout/pebble_fragment.xml`: `LinearLayout` with `TextView` (status), `EditText` (UUID), `Button` (Save).
2.  **Fragment**: `class PebbleFragment : Fragment()`.
    *   Inject `SharedPreferences`.
    *   `onCreateView`: Inflate layout.
    *   `onViewCreated`:
        *   Load UUID from prefs -> Set to EditText.
        *   Save Button: Validate UUID format -> Put to Prefs -> Show Toast.
3.  **Linkage**: Return `PebbleFragment` in `PebblePlugin.getTab()`.

### Phase 4: Integration
1.  **ConfigBuilder**:
    *   Open `app/src/main/java/app/aaps/plugins/main/ConfigBuilder.kt` (or similar).
    *   Add `PebblePlugin` to the list of plugins.
2.  **Manifest**:
    *   Ensure `plugins/pebble/src/main/AndroidManifest.xml` exists (package `app.aaps.plugins.pebble`).

### Phase 5: Final Verification
1.  **ProGuard**: Check if `pebblekit` needs `-keep class com.getpebble.android.kit.** { *; }`. Add to consumer rules if needed.
2.  **Compile & Run**: Verify no build errors and Plugin Tab loads.
