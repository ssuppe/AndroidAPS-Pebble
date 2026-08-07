# ANDROIDAPS — Pebble Plugin Implementation

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


OTHER INFORMATION

Here's how to use the Pebble Android SDK:
PebbleKit Android
PebbleKit Android is a Java library that works with the Pebble SDK and can be embedded in any Android application. Using the classes and methods in this library, an Android companion app can find and exchange data with a Pebble watch.

This section assumes that the reader is familiar with basic Android development and Android Studio as an integrated development environment. Refer to the Android Documentation for more information on installing the Android SDK.

Most PebbleKit Android methods require a Context parameter. An app can use getApplicationContext(), which is available from any Activity implementation.

Setting Up PebbleKit Android
Add PebbleKit Android to an Android Studio project in the app/build.gradle file:

dependencies {
compile 'com.getpebble:pebblekit:4.0.1'
}
Sending Messages from Android
Since Android apps are built separately from their companion Pebble apps, there is no way for the build system to automatically create matching appmessage keys. You must therefore manually specify them in package.json, like so:

{
"ContactName": 0,
"Age": 1
}
These numeric values can then be used as appmessage keys in your Android app.

Messages are constructed with the PebbleDictionary class and sent to a C watchapp or watchface using the PebbleKit class. The first step is to create a PebbleDictionary object:

// Create a new dictionary
PebbleDictionary dict = new PebbleDictionary();
Data items are added to the PebbleDictionary using key-value pairs with the methods made available by the object, such as addString() and addInt32(). An example is shown below:

// The key representing a contact name is being transmitted
final int AppKeyContactName = 0;
final int AppKeyAge = 1;

// Get data from the app
final String contactName = getContact();
final int age = getAge();

// Add data to the dictionary
dict.addString(AppKeyContactName, contactName);
dict.addInt32(AppKeyAge, age);
Finally, the dictionary is sent to the C app by calling sendDataToPebble() with a UUID matching that of the C app that will receive the data:

final UUID appUuid = UUID.fromString("EC7EE5C6-8DDF-4089-AA84-C3396A11CC95");

// Send the dictionary
PebbleKit.sendDataToPebble(getApplicationContext(), appUuid, dict);
Once delivered, this dictionary will be available in the C app via the AppMessageInboxReceived callback, as detailed in Sending and Receiving Data.

Receiving Messages on Android
Receiving messages from Pebble in a PebbleKit Android app requires a listener to be registered in the form of a PebbleDataReceiver object, which extends BroadcastReceiver:

// Create a new receiver to get AppMessages from the C app
PebbleDataReceiver dataReceiver = new PebbleDataReceiver(appUuid) {

@Override
public void receiveData(Context context, int transaction_id,
PebbleDictionary dict) {
// A new AppMessage was received, tell Pebble
PebbleKit.sendAckToPebble(context, transaction_id);
}

};
Important

PebbleKit apps must manually send an acknowledgement (Ack) to Pebble to inform it that the message was received successfully. Failure to do this will cause timeouts.

Once created, this receiver should be registered in onResume(), overridden from Activity:

@Override
public void onResume() {
super.onResume();

// Register the receiver
PebbleKit.registerReceivedDataHandler(getApplicationContext(), dataReceiver);
}
Note: To avoid getting callbacks after the Activity or Service has exited, apps should attempt to unregister the receiver in onPause() with unregisterReceiver().

With a receiver in place, data can be read from the provided PebbleDictionary using analogous methods such as getString() and getInteger(). Before reading the value of a key, the app should first check that it exists using a != null check.

The example shown below shows how to read an integer from the message, in the scenario that the watch is sending an age value to the Android companion app:

@Override
public void receiveData(Context context, int transaction_id,
PebbleDictionary dict) {
// If the tuple is present...
Long ageValue = dict.getInteger(AppKeyAge);
if(ageValue != null) {
// Read the integer value
int age = ageValue.intValue();
}
}
Other Capabilities
In addition to sending and receiving messages, PebbleKit Android also allows more intricate interactions with Pebble. See the PebbleKit Android Documentation for a complete list of available methods. Some examples are shown below of what is possible:

Checking if the watch is connected:

boolean connected = PebbleKit.isWatchConnected(getApplicationContext());
Registering for connection events with registerPebbleConnectedReceiver() and registerPebbleDisconnectedReceiver(), and a suitable BroadcastReceiver.

PebbleKit.registerPebbleConnectedReceiver(getApplicationContext(),
new BroadcastReceiver() {

@Override
public void onReceive(Context context, Intent intent) { }

});
Registering for Ack/Nack events with registerReceivedAckHandler() and registerReceivedNackHandler().

PebbleKit.registerReceivedAckHandler(getApplicationContext(),
new PebbleKit.PebbleAckReceiver(appUuid) {

@Override
public void receiveAck(Context context, int i) { }

});
Launching and killing the watchapp with startAppOnPebble() and closeAppOnPebble().

PebbleKit.startAppOnPebble(getApplicationContext(), appUuid);

You can also visit https://developer.rebble.io/guides/communication/using-pebblekit-android/ for more information