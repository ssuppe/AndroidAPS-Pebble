# AndroidAPS (Pebble Edition Fork)

This repository is a fork of [AndroidAPS](https://github.com/nightscout/AndroidAPS) designed to provide extended telemetry support for Pebble smartwatches.

### Purpose & High-Level Design
* **Pebble Plugin Expansion**: Expands the `plugins:pebble` module to pack and transmit rich status dictionary payloads via PebbleKit (`AppMessage`).
* **Enriched Data**: Transmits blood glucose, trend arrows, IOB, COB, basal rates, deltas, target ranges, and historical BG data for graph rendering on the watch.
* **Mirrored Design**: The data extraction and mapping logic intentionally mirrors the existing `plugins:wear` (Wear OS) module design to maintain consistency and minimize complexity.
* **Target Watchface**: Designed to work in tandem with the **PebbleAAPS** watchface on the Pebble App Store (currently unpublished / in development).
* **Minimal Scope**: No changes have been made to core looping, safety checks, or pump driver logic outside of the Pebble companion plugin.

---

# AAPS
* Check the wiki: https://wiki.aaps.app
*  Everyone who’s been looping with AAPS needs to fill out the form after 3 days of looping  https://docs.google.com/forms/d/14KcMjlINPMJHVt28MDRupa4sz4DDIooI4SrW0P3HSN8/viewform?c=0&w=1

[![Support Server](https://img.shields.io/discord/629952586895851530.svg?label=Discord&logo=Discord&colorB=7289da&style=for-the-badge)](https://discord.gg/4fQUWHZ4Mw)

[![CircleCI](https://circleci.com/gh/nightscout/AndroidAPS/tree/master.svg?style=svg)](https://circleci.com/gh/nightscout/AndroidAPS/tree/master)
[![Crowdin](https://d322cqt584bo4o.cloudfront.net/androidaps/localized.svg)](https://translations.aaps.app/project/androidaps)
[![Documentation Status](https://readthedocs.org/projects/androidaps/badge/?version=latest)](https://wiki.aaps.app/en/latest/?badge=latest)
[![codecov](https://codecov.io/gh/nightscout/AndroidAPS/branch/master/graph/badge.svg?token=EmklfIV6bH)](https://codecov.io/gh/nightscout/AndroidAPS)

DEV: 
[![CircleCI](https://circleci.com/gh/nightscout/AndroidAPS/tree/dev.svg?style=svg)](https://circleci.com/gh/nightscout/AndroidAPS/tree/dev)
[![codecov](https://codecov.io/gh/nightscout/AndroidAPS/branch/dev/graph/badge.svg?token=EmklfIV6bH)](https://codecov.io/gh/nightscout/AndroidAPS/tree/dev)

<img src="https://cdn.iconscout.com/icon/free/png-256/bitcoin-384-920569.png" srcset="https://cdn.iconscout.com/icon/free/png-512/bitcoin-384-920569.png 2x" alt="Bitcoin Icon" width="100">

3KawK8aQe48478s6fxJ8Ms6VTWkwjgr9f2
