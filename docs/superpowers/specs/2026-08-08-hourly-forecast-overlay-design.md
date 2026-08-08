# Inline hourly forecast overlay + switch controls — design

Approved 2026-08-08 (Emily). Replaces the separate hourly-weather window with an
in-dashboard overlay and swaps the settings checkboxes for switch controls.

## Decisions
- Click WEATHER PREVIEW toggles the overlay; Esc closes. No hover trigger.
- `HourlyWeatherDialog` + `HourlyWeatherPanel` are deleted; their behavioural tests are
  retargeted at the new strip. Dennis to be told his popup became the inline strip.

## Components
1. **`HourlyForecastStrip`** (adapters/views): flat horizontal strip of hour cards
   (time, glyph, temp, precip), horizontal-only scroll, ~110px tall, reads the same
   `DayPlanViewModel` as before so it live-updates; "Weather is updating…" placeholder
   when no forecast is loaded.
2. **`OverviewPanel`**: map area becomes a `JLayeredPane`; strip floats bottom-right
   above the weather bar, hidden by default, bounds pinned on resize.
3. **`ToggleSwitch`** (adapters/views): JToggleButton with custom pill/knob painting
   (~40×22), blue on / grey off / washed-out disabled, Space toggles, focus ring,
   accessible name preserved. Used by both settings rows (weather + keep order).
4. Settings dialog gains a muted read-only line: "Always considered: less travel ·
   fewer gaps · sensible mealtimes · daylight." Disabled-with-reason weather behaviour
   unchanged.

## Tests
Retarget `HourlyWeatherPanelTest` → strip; update `HourlyWeatherAndAutoscheduleTest`;
settings-dialog tests search for the switch instead of `JCheckBox`. Full suite green.
