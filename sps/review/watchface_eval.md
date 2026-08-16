# Watchface Evaluation: PebbleAAPS vs AndroidAPS Protocol

## Summary

**Overall verdict: ✅ Strong alignment — 2 real issues found, 1 behavioral gap to be aware of.**

---

## 1. Message Key Numbering — ✅ Perfect Match

`package.json` `messageKeys` vs `PebbleKeys.kt`:

| Key | `package.json` | `PebbleKeys.kt` | Match? |
|-----|---------------|-----------------|--------|
| BG | 0 | 0 | ✅ |
| TREND | 1 | 1 | ✅ |
| IOB | 2 | 2 | ✅ |
| COB | 3 | 3 | ✅ |
| TIME | 4 | 4 | ✅ |
| BASAL | 5 | 5 | ✅ |
| IOB_DETAIL | 6 | 6 | ✅ |
| DELTA | 7 | 7 | ✅ |
| AVG_DELTA | 8 | 8 | ✅ |
| GLUCOSE_HISTORY | 9 | 9 | ✅ |
| LOW_TARGET | 10 | 10 | ✅ |
| HIGH_TARGET | 11 | 11 | ✅ |
| UNITS | 12 | 12 | ✅ |

All 13 key IDs are in exact agreement on both sides.

---

## 2. Trend Arrow Resource Array — ✅ Correct Ordinals

`ARROW_RESOURCE_IDS[]` in `pebble_watchface.c`:
```c
// index 0 = NONE, 1 = TRIPLE_UP, ..., 9 = TRIPLE_DOWN
```

AndroidAPS `TrendArrow` enum ordinals (from `core/data`):
- `NONE=0, TRIPLE_UP=1, DOUBLE_UP=2, SINGLE_UP=3, FORTY_FIVE_UP=4, FLAT=5, FORTY_FIVE_DOWN=6, SINGLE_DOWN=7, DOUBLE_DOWN=8, TRIPLE_DOWN=9`

The resource array order matches the enum ordinals exactly. `get_safe_trend_index()` clamps out-of-range values to 0 (NONE). ✅

---

## 3. History Decoding — ✅ Correct Inverse of Encoding

**Sender (Kotlin)**:
```kotlin
val scaledValue = (reading.value / 2).toInt().coerceIn(0, 255)
historyBytes[targetIndex] = scaledValue.toByte()
```

**Receiver (C)**:
```c
uint8_t encoded = s_state.bg_history[i];
int32_t bg_val = (int32_t)encoded * 2;
```

`encoded * 2` correctly inverts `value / 2`. The C side reads `encoded` as `uint8_t` (unsigned), so even values > 127 (i.e., BG > 254 mg/dL after halving) are decoded correctly. ✅

**Fallback path** (when no `GLUCOSE_HISTORY` key received):
```c
} else {
    add_to_history(&s_state, bg);
}
```
The local `add_to_history` also uses `/2` encoding:
```c
uint8_t encoded = (uint8_t)((bg_mgdl / 2) & 0xFF);
```
Consistent with the Kotlin sender. ✅

---

## 4. 

do you agree with this? I didn't want to over allocate but we need to make sure there is enough ram.  

AppMessage Buffer Size — ⚠️ Issue: Buffer May Be Too Small

```c
app_message_open(256, 256);
```

The inbox buffer is **256 bytes**. With all 13 keys populated, the message size is roughly:
- BG (int32): 8 bytes (key=1 + type=1 + len=2 + data=4)
- TREND (int32): 8 bytes
- TIME (int32): 8 bytes
- IOB (string ~9 bytes): ~13 bytes
- COB (string ~4 bytes): ~8 bytes
- BASAL (string ~5 bytes): ~9 bytes
- IOB_DETAIL (string ~11 bytes): ~15 bytes
- DELTA (string ~3 bytes): ~7 bytes
- AVG_DELTA (string ~3 bytes): ~7 bytes
- GLUCOSE_HISTORY (36 bytes): ~40 bytes
- LOW_TARGET (int32): 8 bytes
- HIGH_TARGET (int32): 8 bytes
- UNITS (int32): 8 bytes

**Estimated total: ~147 bytes payload + overhead ≈ 160–180 bytes.**

256 bytes is likely sufficient in practice, but PebbleKit AppMessage has overhead per key (~4 bytes/key header). Total with 13 keys ≈ ~200 bytes — cutting it close. If the strings are long (e.g. `iob_detail = "(10.50|5.32)"`), this could approach the limit.

**Recommendation**: Increase to `app_message_open(512, 256)` for safety margin.

---

## 5. String Buffer Sizes — ⚠️ Issue: `iob_detail` May Truncate

In `AAPSState`:
```c
char iob_detail[20];  // e.g. "(0.02|0.31)"
```

AndroidAPS formats it as:
```kotlin
"(${decimalFormatter.to2Decimal(bolusIob.iob)}|${decimalFormatter.to2Decimal(basalIob.basaliob)})"
```

Max plausible value: `"(15.75|12.88)"` = 14 chars + null = 15 bytes. This fits in 20. ✅

But with extreme insulin values (e.g. dual-wave extended bolus): `"(150.00|99.99)"` = 15 chars + null = 16 bytes. Still fits. ✅

Other buffers:
- `iob[12]`: `"150.00 U"` = 9 chars → fits ✅
- `cob[10]`: `"999g"` = 4 chars → fits ✅  
- `basal[12]`: `"150%+0.90"` style strings from `toStringShort` could be 9–10 chars → fits ✅
- `delta[16]` / `avg_delta[16]`: max `"+999"` in mg/dL = 4 chars → fits ✅

No truncation risk with realistic clinical values. ✅

---

## 6. BG Display & mmol/L Conversion — ✅ Correct

```c
int32_t val_x10 = (bg_value * 100000 + 90091) / 180182;
```
This uses integer arithmetic to compute `bg_mgdl / 18.0182` with rounding, to 1 decimal place.  
`180182 = 18.0182 * 10000` and `90091 = 180182/2` (for rounding).

The `UNITS` key sets `is_mmol`:
```c
s_state.is_mmol = (units_t->value->int32 == 1);
```
`0 = mg/dL`, `1 = mmol/L` — matches AndroidAPS `unitsValue` exactly. ✅

**Note**: The `LOW_TARGET` and `HIGH_TARGET` are always stored in mg/dL by AndroidAPS (confirmed in protocol spec). The watchface uses them directly for graph scaling without any unit conversion. This is correct — the graph always operates in mg/dL internally. ✅

---

## 7. History Fallback Path Stale-Data Handling — ✅ Correct but Limited

When no `GLUCOSE_HISTORY` array arrives, the watchface locally tracks history:
```c
int elapsed_sec = (int)(now - s_state.last_reading_time);
int missed = (elapsed_sec - 30) / 300;
if (missed > 1) shift_history_left(&s_state, missed - 1);
add_to_history(&s_state, bg);
```
This shifts left by `missed - 1` slots (for ~5-minute gaps), then appends the new BG. Reasonable gap-filling heuristic. ✅

However, since AndroidAPS now **always sends** the full 36-byte `GLUCOSE_HISTORY` array, this fallback path should rarely fire. The fallback is triggered only when the array is missing or the wrong length:
```c
if (hist_t && hist_t->type == TUPLE_BYTE_ARRAY && hist_t->length == BG_HISTORY_COUNT) {
```
`BG_HISTORY_COUNT = 36`, and AndroidAPS always sends exactly 36 bytes. ✅

---

## 8. `is_mmol` Field — 🔵 Behavioral Gap (Not a Bug)

The `is_mmol` flag in `AAPSState` is used in `format_bg_string()` to display BG in mmol/L.  
However, the **delta and avg_delta strings** are already pre-formatted by AndroidAPS with the correct unit (e.g. `"+0.2"` for mmol or `"+3"` for mg/dL) and simply `strcpy`'d into the state. The watchface displays them verbatim — no re-conversion. ✅

The `is_mmol` flag is **not used for graph scaling** — the graph always uses mg/dL values (history bytes × 2) and the mg/dL targets. This is intentional and correct per the spec. ✅

---

## Summary Table

| Check | Result | Notes |
|-------|--------|-------|
| Key numbering (0–12) | ✅ | Exact match |
| Trend ordinal mapping | ✅ | Array order matches enum |
| History encode/decode symmetry | ✅ | `/ 2` → `* 2`, uint8_t correct |
| Buffer sizes (string fields) | ✅ | No truncation for realistic values |
| mmol/L BG display | ✅ | Correct integer math |
| Units key semantics (0/1) | ✅ | Matches protocol |
| Targets in mg/dL | ✅ | Protocol correctly always sends mg/dL |
| **AppMessage buffer (256 bytes)** | ⚠️ | May be tight; recommend 512 |
| History fallback path | ✅ | Consistent encoding, rarely triggered |
| `is_mmol` scope (BG only, not graph) | 🔵 | Intentional design — not a bug |
