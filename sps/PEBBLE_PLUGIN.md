You are an expert software engineer technical lead with a PhD in computer science. Your task is to help develop a thorough, step-by-step implementation plan for a junior engineer, using the following documents, and a prompt from your manager.

Before we start, please take a look at sps/WEAR_OS_DATA_FLOW.md

You are converting a **generic plan of action** from your manager into a **production-ready engineering design document and step-by-step plan that is directly executable by a junior engineer or capable coding agent. Your output must be a single Markdown document. Do **not** include any extra commentary, explanations, or chat—**output only the document**.

### Transformation requirements

* Be **specific** and **operational**. Replace vague goals with concrete steps, commands, checklists, acceptance gates, and explicit assumptions.
* Prefer **compact, high-signal prose**. No filler. Use short paragraphs and terse bullets.
* If information is missing, make **minimal, clearly labeled assumptions** (e.g., “Assumption: …”). Feel free to ask clarifying questions before you write the doc.
* Compile the spec into **executable oracles**: pre/postconditions, invariants, consumer/provider API contracts, and **metamorphic properties**. Generate both tests and **runtime guards**.
* Beyond unit/integration/e2e: include **property-based testing**, **metamorphic testing**, **mutation testing** (score target), **grammar/coverage-guided fuzzing**, **concolic/symbolic execution** for critical paths, **differential tests** vs last known-good, **contract tests** for external deps, and **runtime invariant checks** with shadow traffic or replay.
* Keep the plan **tool-agnostic** but actionable (shell/Python placeholders OK).

### Required document structure (exact section order)

1. **Title** — `# {{PROJECT_NAME}} — \`todo.md\`\`
2. **TL;DR** — one line.
3. **Invariants (do not change)** — non-negotiable constraints.
4. **Assumptions & Scope** — what you’re assuming (label uncertain items).
5. **Objectives** — 3–5 measurable goals.
6. **Risks & Mitigations** — top risks with a single mitigation each.
7. **Method Outline (idea → mechanism → trade-offs → go/no-go)** — turn high-level ideas into actionable variants/workstreams.
9. **Implementation Notes** — terse details a coder needs (APIs, attach points, precision, cache policies, etc.).
10. **Acceptance Gates** — pass/fail thresholds tied to Objectives.
11. **“Make-sure-you” Checklist** — must-do guardrails for the agent.
12. **Project hygiene prep** - following instructions in DEVELOPMENT_PROCESS.md, instruct on git branch setup, github issue creation, and of course a test-driven approach.
13. **In-depth test plan**
14. **In-depth engineering plan**

### Language & style constraints

* Crisp, technical, neutral tone.
* No self-references (“As an AI…”).
* Use code fences for XML and pseudocode.
* Use placeholders like `{{MODEL_NAME}}`, `{{DATASET}}`, `{{RANK_SCHEDULE}}` when the plan lacks specifics; **label them** in Assumptions.

Here is the request from your manager:

I want to create an engineering plan for creating a separate, independent, parallel AndroidAPS plugin to communicate to Pebble smartwatches. Read WEAR_OS_DATA_FLOW.md, and come up with a plan to do the same, but to Pebble. It should also include a plan for adding the Plugin to the Config Builder in the AAPS UI, adding it as a tab when enabled, etc. 

I've also included the Pebble Android SDK documentation here:

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

