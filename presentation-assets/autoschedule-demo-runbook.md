# Autoschedule demo — certified runbook

**This file supersedes every earlier version of the hero setup.** If a time or a venue
anywhere else disagrees with this page, this page is right.

Certified 2026-08-09 against `main @ 558ef7a` (level with `origin/main`) plus the uncommitted
Autoschedule working tree, using live Nominatim / OSRM / Open-Meteo.
Three consecutive Preview→Apply runs of each scenario; all six produced the identical
proposed schedule.

---

## Fixed for both scenarios

| Field | Value |
|---|---|
| Destination | `Toronto` |
| Trip date | **Wednesday 5 August 2026** |
| Available from / until | **9:00 AM** / **9:00 PM** |
| Getting around by | **Walking** |
| Unavailable time | **9:00 AM → 10:30 AM** (one window) |
| Locked activity | **Royal Ontario Museum** |
| Minimize travel time | ON |
| Minimize gaps | ON |
| Preserve mealtimes | ON |
| Prefer daylight outdoors | ON |
| Avoid bad weather | ON |
| **Preserve plan order** | **OFF** |

The date must stay a **Wednesday**: ROM parses `Sa-Th 10:00-17:30` and Four Brothers
`Mo-We 11:30-24:00`. Thursday or Friday shifts the pizza's window and breaks the meal story.

### The four venues, exactly as production resolves them

| Venue | Search term | Category / setting | Duration | Hours on 5 Aug |
|---|---|---|---|---|
| Four Brothers Pizza | `Four Brothers Pizza` | FOOD / indoor | 60 min | 11:30 AM–11:59 PM |
| High Park | `High Park` | PARKS_NATURE / **outdoor** | 90 min | unknown (unrestricted) |
| Royal Ontario Museum | `Royal Ontario Museum` | MUSEUM / indoor | 120 min | 10:00 AM–5:30 PM |
| Trinity Bellwoods Park | `Trinity Bellwoods Park` | PARKS_NATURE / **outdoor** | 90 min | unknown (unrestricted) |

**You do not enter durations.** They come from the venue records. If your app shows different
durations than these, stop and reconcile before presenting — see *Open risk*.

---

## PRIMARY scenario

Enter these four start times. Nothing else differs from the table above.

| Order added | Venue | Start |
|---|---|---|
| 1 | Four Brothers Pizza | **10:00 AM** |
| 2 | High Park | **12:00 PM** |
| 3 | Royal Ontario Museum | **3:30 PM** ← lock this one |
| 4 | Trinity Bellwoods Park | **7:30 PM** |

Original plan as it will appear:

```
10:00 AM – 11:00 AM   Four Brothers Pizza      central        lng -79.3981
12:00 PM –  1:30 PM   High Park                FAR WEST       lng -79.4638
 3:30 PM –  5:30 PM   Royal Ontario Museum     north-central  lng -79.3947   [LOCKED]
 7:30 PM –  9:00 PM   Trinity Bellwoods Park   mid-west       lng -79.4139
```

### Expected output — identical on all 3 runs

```
10:30 AM – 12:00 PM   High Park                moved clear of your unavailable time
12:00 PM –  1:05 PM   Travel to Trinity Bellwoods Park
 1:05 PM –  2:35 PM   Trinity Bellwoods Park   in daylight
 2:41 PM –  3:30 PM   Travel to Royal Ontario Museum
 3:30 PM –  5:30 PM   Royal Ontario Museum     you locked this time
 5:30 PM –  6:13 PM   Travel to Four Brothers Pizza
 6:13 PM –  7:13 PM   Four Brothers Pizza      a usual mealtime
```

7 rows, **3 travel rows**. Travel **220 → 157 (−63)**. Waiting **100 → 6 (−94)**. Moved 3 of 4.

Tiles, in this order, as a 2×2 block:

1. `◴ 94 MIN / waiting removed`
2. `→ 63 MIN / less travel`
3. `☀ DAYLIGHT / Trinity Bellwoods Park`
4. `◕ BETTER MEAL TIME / Four Brothers Pizza`

Chips: `◷ Your unavailable time kept free` · `⚿ Royal Ontario Museum kept at your time` ·
`✓ All 4 activities kept`

Trade-off strip: **empty**. Footer:

> Arranged for less travel, fewer wasted gaps, daylight for outdoor activities and sensible mealtimes.

Apply saves **7 events** matching the proposal exactly.

---

## BACKUP scenario

Same date, venues, lock, unavailable window, and toggles. **Only the start times differ**, and
only for two venues.

| Order added | Venue | Start |
|---|---|---|
| 1 | High Park | **9:30 AM** |
| 2 | Four Brothers Pizza | **2:15 PM** |
| 3 | Royal Ontario Museum | **3:30 PM** ← lock this one |
| 4 | Trinity Bellwoods Park | **7:30 PM** |

**It produces the exact same proposed schedule as the primary** — same order, same seven rows,
same times. Only the "before" figures change:

Travel **172 → 157 (−15)**. Waiting **186 → 6 (−180)**.

Tiles: `◴ 180 MIN / waiting removed`, `→ 15 MIN / less travel`, `☀ DAYLIGHT`, `◕ BETTER MEAL TIME`.
Same three chips, same empty trade-off, same footer.

Use it if the primary's entry goes wrong. Note the story flips: waiting becomes the headline
and the travel saving is only 15 minutes, so lead the narration with the wasted afternoon
rather than the route.

---

## Why the daylight claim is truthful

Production's `DaylightPolicy` uses a fixed 8:00 AM–7:00 PM window, which is wrong for Toronto
in August — real sunset on 5 Aug 2026 is about **8:37 PM**.

Both scenarios are built so the claim survives that correction: **Trinity Bellwoods Park at
7:30–9:00 PM genuinely runs 23 minutes past the real sunset**, and the proposal moves it to
1:05–2:35 PM, fully in daylight. Verified against both the current 7:00 PM constant and the
real 8:37 PM sunset — all four tiles appear either way.

The earlier setup (Bellwoods at 6:30 PM) earned its DAYLIGHT tile purely from the artificial
cutoff and would have been an untrue claim. That version is retired; do not use it.

---

## Deterministic vs variable

**Deterministic — identical on all six certified runs:**

- proposed order and all seven row times
- which four tiles appear, and their order
- all three chips
- empty trade-off strip; footer wording
- three travel rows; Apply succeeds and saves seven events
- no weather tile (5 Aug's recorded weather earns none, so there is no hidden fifth
  improvement and no extra tile row)

**Can still vary — live external APIs:**

| Source | What could move | Mitigation |
|---|---|---|
| **Nominatim** | which venue a search term resolves to | all four resolved first-hit every run; add venues before the audience is watching |
| **OSRM** | the minute figures (220/157/100/6) | identities of the tiles are stable; a 63-minute gap has wide margin. Read numbers off the screen, not this page |
| **OSM opening hours** | ROM 10:00–5:30, Pizza opens 11:30 | if either changes, the meal or lock story shifts |
| **Open-Meteo** | recorded weather for a past date | fixed now; Open-Meteo only serves ~92 days back, so 5 Aug stops working around November 2026 |

A past trip date is deliberate: recorded weather cannot move between rehearsal and
presentation. Narrate it as "a day I planned", not "a day I'm planning".

---

## Click sequence

1. `./run-app.sh`. The gallery also holds a seeded Toronto demo trip — ignore or delete it.
2. **New Itinerary** → `Toronto`, `5 August 2026`.
3. **Before adding anything**: open **Trip Options** and set the end to **9:00 PM**
   (New Itinerary hardcodes 9:00 AM–6:00 PM). This is required — the proposal ends at
   7:13 PM, and after Apply the timeline window is pinned to the trip's hours.
4. Search each venue, **Add to plan** at its time from the scenario table.
5. On **Royal Ontario Museum**, click **Lock**. It should read **Locked**.
6. **Autoschedule** → *Available from* `9:00 AM`, *until* `9:00 PM`.
7. **Add unavailable time** → From `9:00 AM`, To `10:30 AM`.
8. Confirm **Preserve plan order is OFF**; leave the other four preferences ON.
9. **Generate Preview**.
10. Map: **Before** (dashed coral) vs **Proposed** (solid green).
11. Scroll to the 6:13 PM Four Brothers Pizza row.
12. **Apply**.

---

## Narration (~40 seconds)

> Here's a day someone actually threw together: pizza at ten in the morning, High Park way out
> west at noon, the museum back downtown at half three, and Trinity Bellwoods at half seven —
> running until nine, well past sunset. The red line crosses the city three times.
>
> I've told Trippy two things: the museum is booked, so it's locked; and I'm not free before
> half ten.
>
> All four activities are kept — it never drops anything. Green route: straight west to east,
> no backtracking. Ninety-four minutes of waiting gone, sixty-three minutes less travel.
>
> Pizza moves to quarter past six, an actual dinner time. Bellwoods moves to the early
> afternoon, in daylight. The museum hasn't moved a minute, my unavailable morning is
> untouched, and everything sits inside its opening hours.
>
> Apply — and that's exactly what gets saved.

---

## Screenshot guidance

- Window **1600 × 1000**. Below ~1400 wide the tiles stop reading as a 2×2 block.
- Split map ~55% / timeline ~45%.
- Whole-city zoom so both west parks stay visible — that's where the backtrack shows.
- Capture **Before** after step 5, **Preview** right after step 9, **Applied** after step 12.
- Keep the four tiles, three chips, and footer in frame for every Preview shot.

---

## Open risk

One unreconciled observation: a live session reported **High Park at 6:01–7:01 PM** (a
60-minute duration) and figures of 112 min waiting / 57 min less travel. Production resolves
High Park as **90 minutes**, and neither figure matches any certified run.

That means the live session used different venue data than this certification. **Do one dry
run and check the four durations against the venue table above** before presenting. If they
differ, the times in this document will not reproduce, and the discrepancy needs chasing
before the demo rather than during it.

Everything else here is measured, not predicted.
