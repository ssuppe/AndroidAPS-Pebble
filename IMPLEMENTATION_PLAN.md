# Implementation Plan - Pebble Plugin

## Stage 1: Module Skeleton & Build Configuration
**Goal**: Establish the module structure and ensure compilation.
**Success Criteria**: Module registered in `app` and `settings`. `./gradlew :plugins:pebble:assembleDebug` completes.
**Status**: Complete

## Stage 2: Data Mapping Logic (Pure Kotlin TDD)
**Goal**: Implement logic to transform AAPS `EnrichedData` into a `PebbleDictionary`.
**Success Criteria**: `PebbleDataMapper` correctly maps BG, Trend, IOB, COB to Pebble keys.
**Tests**: `PebbleDataMapperTest`
**Status**: Complete

## Stage 3: UUID Configuration Logic (Pure Kotlin TDD)
**Goal**: Encapsulate logic for validating and retrieving the target Watch UUID.
**Success Criteria**: `TargetUuidProvider` handles default, valid, and invalid UUIDs.
**Tests**: `TargetUuidProviderTest`
**Status**: Not Started

## Stage 4: Transport Layer Abstraction
**Goal**: Wrap the static `PebbleKit` to allow unit testing of the sending logic.
**Success Criteria**: `IPebbleTransport` interface and implementation defined.
**Tests**: N/A (Abstraction layer)
**Status**: Not Started

## Stage 5: Plugin**Confirming the Inclusion**

I've confirmed that the `settings.gradle.kts` file is missing the required include statement. Since the previous iteration showed an empty file, I'm now certain that adding `include(":plugins:pebble")` is the immediate fix. However, I am now thinking I should also include standard includes to avoid breaking the build, as the missing `settings.gradle.kts` may contain standard content. I must include everything to make the build function successfully.


 Orchestration
**Goal**: Connect the RxBus event to the Data Mapper and Transport.
**Success Criteria**: `PebblePlugin` subscribes to updates and sends data.
**Tests**: `PebblePluginTest`
**Status**: Not Started

## Stage 6: UI & Main App Integration
**Goal**: Allow user to view and edit the Target UUID and enable the plugin.
**Success Criteria**: Configuration Fragment working, Plugin registered in App.
**Tests**: Manual verification.
**Status**: Not Started
