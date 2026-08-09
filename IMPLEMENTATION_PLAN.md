# Implementation Plan: Pebble Watchface Protocol Expansion

## Stage 1: Core Definitions and Models
**Goal**: Expand PebbleKeys definitions and update the EnrichedData data class.
**Success Criteria**: All new keys (5-12) are represented, and the data structures compile.
**Tests**:
* `testEnrichedDataCompilation` verifies that the updated `EnrichedData` object can be constructed with the new target, history, units, and basal properties.
**Status**: Complete

---

## Stage 2: PebbleDataMapper Protocol Expansion (TDD)
**Goal**: Implement the updated mapping logic inside `PebbleDataMapper` using a strict TDD flow.
**Success Criteria**: `PebbleDataMapper` maps all 12 key-value pairs correctly, handles null values gracefully, and encodes byte array payloads accurately.
**Tests**:
* `testMap_populatesAllExpandedKeys_withCorrectFormats` verifies that targets, units, history byte array, deltas, detail IOB strings, and basal rates are correctly added to the `PebbleDictionary`.
* `testMap_handlesNullFields_gracefully` verifies that optional null fields are omitted without raising exceptions.
**Status**: Complete

---

## Stage 3: PebblePlugin Data Gathering & DI Injection (TDD)
**Goal**: Update `PebblePlugin` to inject required AAPS services, perform telemetry extraction (mirroring Wear OS), and broadcast to the transport layer.
**Success Criteria**: The plugin injects `ProfileFunction`, `ProfileUtil`, `Preferences`, and `ProcessedTbrEbData`, retrieves and formats the telemetry values, scales the glucose history array, and triggers data transmission.
**Tests**:
* `testSendData_extractsAndSendsAllEnrichedMetrics` verifies that when an GUI update event fires, the plugin gathers matching values and delegates compilation to the mapper.
* `testGlucoseHistorySerialization_correctlyAlignsAndScales` verifies that history values are scaled (`/ 2`) and right-aligned.
**Status**: Complete
