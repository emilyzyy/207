# Autoschedule presentation demo — design and evidence

**Audited commit:** `857bc91c4ddbc9883da8ed2d0b76db9ba560b84c` (`main`, "Merge pull request #24").
Working tree clean; `git pull --ff-only` was a no-op. Nothing was implemented in this phase —
analysis only, using a throwaway harness outside the repository.

> ⚠️ The working copy had drifted onto `feature/autoschedule-ui-polish` (a commit *before*
> the transport-mode work). It is now on `main`. Run the demo from `main`.

---

## 1. Sources and Venice data examined

| Source | What it gave | Trust |
|---|---|---|
| **Overpass API** (`overpass.private.coffee`, the app's own mirror #3), bbox `45.425,12.31,45.448,12.35`, read 2026-08-08 | 196 real Venice venues with `name`, coordinates, category and `opening_hours` | **Verified provider data** — the same endpoint and tag the app reads in production |
| `OpeningHoursParser` (production class) | Which of those tags the app can actually read, per weekday | **Verified app behaviour** |
| `MockDistanceService` (production class) | Deterministic haversine travel: walking ≈ 4.8 km/h + 2 min, floor 10 min | **Deterministic, offline** |
| Real Interactor, engine, policies, `ProblemValidator`, `ReasonCollector`, `ScheduleImprovementFinder`, `AutoSchedulePresenter` | Every message quoted below | **Production output, not authored copy** |

`overpass-api.de` (mirror #1) rate-limited during the probe; mirror #3 answered. Worth knowing
for demo day — the app already falls through the same list.

### Venues examined, and how the app reads them

Parsed for Mon 10 – Thu 13 Aug 2026:

| Venue | Real `opening_hours` tag | Parsed (Wed) | Verdict |
|---|---|---|---|
| **Bistrot de Venise** | `Mo-Su 12:00-15:00, 19:00-23:30` | `12:00-15:00, 19:00-23:30` | ✅ **chosen** — every weekday, unambiguous afternoon closure |
| **Gallerie dell'Accademia** | `Tu-Su 08:15-19:15; Mo 8:15-14:00` | `08:15-19:15` | ✅ chosen (note the app copes with the missing leading zero) |
| **Peggy Guggenheim** | `We-Mo 10:00-18:00` | `10:00-18:00` | ✅ usable; closed Tuesdays |
| **Scala Contarini del Bovolo** | `Tu-Su 10:00-13:30, 14:00-18:00` | two intervals | ✅ usable (split-hours candidate) |
| **Libreria Acqua Alta** | `Mo-Su 09:00-19:15` | `09:00-19:15` | ✅ chosen |
| **Scuola Grande di San Marco** | `Tu-Sa 09:30-17:30; PH closed` | `09:30-17:30` | ✅ usable (holiday rule skipped, as documented) |
| **Museo Correr** | `10:00-19:00; Nov-Mar 10:00-17:00` | **UNKNOWN** | ❌ **rejected for the demo** — month ranges are unsupported, so the app treats hours as unknown |

`Museo Correr` is the honest illustration of the permissive path and is worth *mentioning*, never
worth building a failure beat on.

### Data classification for the seed

| Class | Items |
|---|---|
| **Verified real venue data** | Names, coordinates and `opening_hours` of Bistrot de Venise, Gallerie dell'Accademia, Libreria Acqua Alta — copied verbatim from Overpass |
| **Provider data the app returns today** | The parsed intervals in the table above |
| **Deterministic values copied into the demo** | Those same coordinates/hours frozen into a local seed; travel from `MockDistanceService`; a fixed 24-hour forecast |
| **Representative / synthetic** | **Piazza San Marco** and **Giardini della Biennale** carry real coordinates but **no `opening_hours` tag** (OSM has none). They are modelled as unknown-hours outdoor spaces — which is exactly how the app treats them — and the forecast is invented for determinism |

---

## 2. Candidate scenarios tested

All run through the real production path. Fixed elements: Wed 12 Aug 2026, walking, `keepOrder` on,
weather worsening from 18:00.

### C1 — Business lunch pinned inside the afternoon closure
Accademia, Acqua Alta, **Bistrot (pinned 16:00)**, San Marco, Giardini.
Conflict ✅. Corrected 13:00 → travel **59→61 (worse)**, waiting 256→6, 5 cards.

### C2 — C1 plus a blocked 15:00–16:00 call
Conflict ✅. Travel still worsened; waiting 256→14; gained "moved clear of your unavailable time".

### C3 — Museum pinned before opening
Guggenheim pinned 09:00 (opens 10:00). Conflict ✅ — *"Peggy Guggenheim Collection is locked to a
time when it is closed."* Correct but the mistake is less vivid than a restaurant siesta, and the
rest of the day is unchanged from C1.

### C4 — Split-hours attraction pinned in its own lunch gap
Bovolo pinned 13:15 for 45 min, crossing its 13:30–14:00 closure. Conflict ✅. **The most
technically impressive** (proves the one-interval rule) and **the hardest to narrate** — the
audience must hold two intervals and a duration in mind at once.

### C5 — Four activities only
Conflict ✅. Travel **42→42 (no gain)**; produced a slightly odd extra card, *"Your original order
was kept"*, alongside "3 of 4 moved". Fewer activities left too little to optimise.

### C6/C7 — Deliberate geographic zig-zag
C7 (east → far-west → centre → far-east) finally produced a **travel saving: 73→64**. Waiting
239 min was implausibly large.

### C9 — Tightened day, Giardini late
travel **61→55**, waiting 199→0, six cards. Strongest so far.

### C12 — **C9 plus a blocked 15:00–16:00 business call** ← **SELECTED**
Keeps the travel saving *and* adds the unavailable-period proof.

---

## 3. Ranked comparison

| # | Candidate | Conflict beat | Hard constraints shown | Soft improvements | Live actions | Time | Realism | Reliability | Clarity | Verdict |
|---|---|---|---|---|---|---|---|---|---|---|
| **1** | **C12 lunch + call** | Bistrot named, closed 15:00–19:00 | pin honoured · opening hours · unavailable period · all 5 kept once · durations intact | **6** (waiting, travel, weather ×2, daylight, pin) | 4 | ~70 s | High | High | High | **SELECTED** |
| 2 | C9 lunch, no call | same | pin · opening hours · all kept | 6 | 3 | ~60 s | High | High | High | Runner-up; loses the unavailable-period proof |
| 3 | C7 zig-zag | same | pin · opening hours | 6 | 3 | ~65 s | Medium | High | Medium | 239 min waiting reads as an artefact |
| 4 | C4 split-hours | Bovolo named | one-interval rule | ~4 | 4 | ~85 s | High | High | **Low** | Best proof, worst explanation in 10 s |
| 5 | C3 museum-before-opening | Guggenheim named | pin · opening hours | ~5 | 3 | ~60 s | High | High | Medium | Correct but flat; "before it opens" is a duller mistake |
| 6 | C5 four activities | same | pin · opening hours | 5 + odd order card | 3 | ~55 s | Medium | High | Medium | Too little to optimise; no travel gain |

**Why C12 wins.** It is the only candidate that simultaneously produces a *named* opening-hours
conflict, a *positive travel* number, a believable waiting number, an unavailable-period proof
visible in a row's own reason text, and a weather/daylight move — while needing one edit to fix.
C4 proves more but cannot be explained in ten seconds, which is the actual constraint.

---

## 4. Selected seed — exact values

### Activities (5)

| Id | Name | Category | Indoor/Outdoor | Lat | Lon | Duration |
|---|---|---|---|---|---|---|
| `acquaalta` | Libreria Acqua Alta | ATTRACTION | INDOOR | 45.43797 | 12.34227 | 45 min |
| `accademia` | Gallerie dell'Accademia | MUSEUM | INDOOR | 45.43137 | 12.32809 | 90 min |
| `bistrot` | **Bistrot de Venise** | **FOOD** | INDOOR | 45.43555 | 12.33653 | 90 min |
| `sanmarco` | Piazza San Marco | OUTDOOR | OUTDOOR | 45.43395 | 12.33860 | 60 min |
| `giardini` | Giardini della Biennale | OUTDOOR | OUTDOOR | 45.42890 | 12.35520 | 60 min |

### Opening intervals (Wed 12 Aug 2026)

| Venue | Tag frozen into the seed | Parsed |
|---|---|---|
| Bistrot de Venise | `Mo-Su 12:00-15:00, 19:00-23:30` | 12:00–15:00 **and** 19:00–23:30 |
| Gallerie dell'Accademia | `Tu-Su 08:15-19:15; Mo 8:15-14:00` | 08:15–19:15 |
| Libreria Acqua Alta | `Mo-Su 09:00-19:15` | 09:00–19:15 |
| Piazza San Marco | *(none)* | unknown → unconstrained |
| Giardini della Biennale | *(none)* | unknown → unconstrained |

### Initial Day Plan (Bob's careless arrangement)

| Time | Activity |
|---|---|
| 10:00–10:45 | Libreria Acqua Alta *(east)* |
| 11:15–12:45 | Gallerie dell'Accademia *(far west — a long hop)* |
| **16:00–17:30** | **Bistrot de Venise — PINNED, and shut** |
| 17:45–18:45 | Piazza San Marco |
| 19:00–20:00 | Giardini della Biennale *(far east, dark and raining)* |

### Fixed commitment
Pinned **16:00** (invalid — inside the 15:00–19:00 closure) → corrected to **13:15**.

### Availability and unavailable period
Available **09:00–21:00**. Unavailable **15:00–16:00** (a call with head office).

### Deterministic travel
`MockDistanceService`, walking: `max(10, round(km / 4.8 × 60) + 2)` on the coordinates above.
No network. Representative legs: Giardini→San Marco 20 min, San Marco→Accademia 13 min,
Accademia→Bistrot 12 min, Bistrot→Acqua Alta 10 min.

### Deterministic weather and daylight
24 hourly entries: **LOW** ("Sunny intervals", 26 °C, 10 %) before 17:00, **MEDIUM** ("Showers")
at 17:00, **HIGH** ("Heavy rain", 21 °C, 85 %) from 18:00. Daylight is the production constant
**08:00–19:00** (`DaylightPolicy`), not a sunrise lookup — see limitations.

---

## 5. Expected results — production output, verbatim

### Beat 1 — the invalid pin (rejected *before* any search, by `ProblemValidator`)

> **Bistrot de Venise is locked to a time when it is closed. Your Day Plan was not changed.**

Status `CONFLICT`. No proposal is drawn. The Day Plan above it is untouched.

### Beat 2 — the corrected preview

> **Proposed schedule: 4 of 5 activities moved. Nothing changes until you choose Apply.**

**Travel 61 → 55 min · Waiting 199 → 5 min · 4 of 5 moved**

| Time | Row | Badge / reason |
|---|---|---|
| 09:00–10:00 | Giardini della Biennale | moved |
| 10:00–10:20 | ↳ Travel to Piazza San Marco | |
| 10:20–11:20 | Piazza San Marco | moved |
| 11:20–11:33 | ↳ Travel to Gallerie dell'Accademia | |
| 11:33–13:03 | Gallerie dell'Accademia | moved |
| 13:03–13:15 | ↳ Travel to Bistrot de Venise | |
| **13:15–14:45** | **Bistrot de Venise** | **🔒 Locked — "you locked this time"** |
| 14:45–14:55 | ↳ Travel to Libreria Acqua Alta | |
| **16:00–16:45** | Libreria Acqua Alta | moved — **"moved clear of your unavailable time"** |

**Improvement cards, in the order the Presenter emits them:**

1. ⏳ **194 min of waiting removed** — Less dead time between activities
2. → **6 min less travel** — Shorter journeys than your current order
3. ☂ **Moved to better weather** — Giardini della Biennale
4. ☀ **Moved into daylight** — Giardini della Biennale
5. ☂ **Moved to better weather** — Piazza San Marco
6. ⚿ **Pinned activity kept at its time** — Bistrot de Venise

**Warning band:** *"Travel times come from the routing service and may include estimates."*

---

## 6. What is visibly proven

**Hard constraints**
- Pinned activity outside real opening hours → rejected **by name**, before any search ✅
- Corrected pin held at **exactly 13:15** ✅
- All 5 activities present exactly once ✅
- Durations unchanged (45/90/90/60/60) ✅
- Bistrot placed wholly inside **one** interval (12:00–15:00), never across the closure ✅
- Availability 09:00–21:00 respected ✅
- Unavailable 15:00–16:00 avoided — **and the row says so** ✅
- Travel sits between activities; nothing overlaps ✅
- Nothing silently dropped ✅

**Soft preferences** — all six confirmed by production evidence (a penalty that actually fell):
waiting ✅ · travel ✅ · weather ×2 ✅ · daylight ✅ · pin honoured ✅

---

## 7. Storyboard (≈ 70 s)

| # | Action | Screen | Say | Proves | Time |
|---|---|---|---|---|---|
| 1 | — | Day Plan, 5 Venice activities | "Bob's Wednesday in Venice. He's jet-lagged, and his day is in the order he happened to add things — Accademia is right across the city from where he starts." | Before View *(Individual rubric)* | 10 s |
| 2 | Click padlock on Bistrot | Row tints, padlock shuts | "His business lunch is fixed, so he pins it." | Pin as user intent | 8 s |
| 3 | Autoschedule → Generate Preview | — | "When Bob clicks Preview, the View passes his selections to the Controller, which packages them for the Autoschedule Interactor." | CA in one sentence | 7 s |
| 4 | *(conflict appears)* | Red line; plan unchanged | "It stops before it even starts searching. *Bistrot de Venise is locked to a time when it is closed* — he pinned lunch at four, and it shuts between three and seven. And notice his Day Plan is untouched." | **Hard constraint, named failure, atomicity** | 14 s |
| 5 | Edit pin → 13:15 | Row moves | "One fix: lunch at quarter past one." | Single correction | 6 s |
| 6 | Generate Preview | Proposal + cards | "Now it works. Lunch is still exactly where he put it. The rest of the day is rebuilt around it — the garden moves into daylight and out of the evening rain, and the bookshop steps aside for his four o'clock call." | Lock honoured · weather · daylight · unavailable period | 16 s |
| 7 | Point at cards + metrics | Improvements stack | "Waiting drops from 199 minutes to 5, travel from 61 to 55 — and every card is a before-and-after the use case computed, not a label." | **Soft improvements, honesty** | 9 s |
| 8 | — | — | "Nothing has changed until he presses Apply." | Preview immutability | 4 s |

Hand-off: *"That's Bob's Wednesday sorted — over to \_\_\_ for how he shares it."*

**Live actions: 4** (lock, preview, edit pin, preview).

---

## 8. Slide and screenshot plan

**Slide — user story**
> As Bob, a traveller who has already chosen what he wants to do on a day of his trip, I want
> CloseAI to work out the order and the times for him — respecting how he's getting around, when
> places are actually open, when he's busy, and anything he's pinned — so that he gets a realistic
> day with far less backtracking, without losing any of his choices.

**Screenshots** (into `docs/autoschedule/screenshots/`, captured offscreen as the existing set is):

| # | File | State |
|---|---|---|
| V1 | `v1-before-venice.png` | Before View — the careless 5-activity Wednesday |
| V2 | `v2-conflict-closed.png` | The named opening-hours conflict, plan unchanged |
| V3 | `v3-settings.png` | Settings dialog: 09:00–21:00, unavailable 15:00–16:00, mode, six switches |
| V4 | `v4-preview.png` | After View — proposal, badges, metrics |
| V5 | `v5-improvements.png` | Improvements stack, close-cropped and legible on a projector |

**Backup sequence if the live app fails:** V1 → V2 → V3 → V4 → V5, narrated with the same script.
Every state is reachable offline from the seed, so the fallback is a slide advance, not an apology.

---

## 9. Minimal UI adjustment needed

Only one change is genuinely required, and it is a **messaging defect, not a layout tweak**:

> **`WeatherSuitabilityPolicy.reasonFor` labels sunny rows "poorer weather expected outdoors".**
> `LOW_PENALTY_PER_HOUR = 5` means *any* outdoor activity scores above zero, so the reason fires
> whatever the forecast. In this seed, Giardini at **09:00 in sunshine** shows *"poorer weather
> expected outdoors"* while simultaneously earning the card *"Moved to better weather"*. On a
> projector that reads as the feature contradicting itself.
>
> **Fix:** emit the reason only when severity is MEDIUM or HIGH. Scoring is untouched — the flat
> LOW charge is deliberate and affects no ranking, since it is constant across candidate times.
> One condition, plus a test.

Optional, only if the projector is small:
- The improvements stack shows six cards; the **three strongest are cards 1, 2 and 6**. Consider
  ordering `LOCK_PRESERVED` first so the pinned-commitment proof leads.
- No clipping was observed at 1180 px; the warning band holds one line comfortably.

**Not recommended:** any redesign of the strip, dialog or Day Plan. Nothing else clips.

---

## 10. Offline fallback

Nothing in the demo touches the network: coordinates, hours, travel and forecast are all frozen
locally, exactly as `AutoscheduleDemoTrip` does today. Overpass is used **only** in this analysis
phase. If the projector machine has no network the demo is identical.

---

## 11. Files that a later implementation phase would touch

| File | Change |
|---|---|
| `src/main/java/closeai/VeniceDemoTrip.java` | **New** — the frozen seed, alongside `AutoscheduleDemoTrip` rather than replacing it |
| `src/test/java/closeai/VeniceDemoTripTest.java` | **New** — pins the conflict message, the six cards and the metric figures, so the demo cannot rot silently |
| `src/main/java/closeai/application/autoschedule/policy/WeatherSuitabilityPolicy.java` | One condition in `reasonFor` (§9) |
| `src/test/java/closeai/application/autoschedule/BuiltInObjectivesTest.java` | A case asserting no weather reason in good conditions |
| `docs/autoschedule/screenshots/v1…v5-*.png` | New captures |
| `docs/autoschedule/demo-script.md` | Replace the Toronto walkthrough with this storyboard |
| `docs/autoschedule/screenshot-checklist.md` | Add V1–V5 |

`AutoscheduleDemoTrip` and its tests stay untouched — the Toronto seed remains the regression
fixture; Venice is the presentation fixture.

---

# Narrative-corrected alternatives

**The problem with the original selection.** It pinned a *business lunch* at 16:00 and corrected
it to 13:15. Nobody describes a 4:00–5:30 PM commitment as lunch, so the audience spends the
failure beat wondering about the setup instead of watching the constraint fire. The scheduling
evidence was strong; the story was not.

Five directions were re-run through the real Interactor, validator, engine, policies,
`ReasonCollector`, `ScheduleMetrics`, `ScheduleImprovementFinder` and `AutoSchedulePresenter`.

### N1 — Late lunch: Bistrot pinned **15:30**, corrected **13:15**

*"Bob booked a late lunch for half past three. In Venice the kitchen shuts at three."*

Conflict ✅ *"Bistrot de Venise is locked to a time when it is closed. Your Day Plan was not
changed."* · travel **61 → 55** · waiting **199 → 5** · unavailable-period row ✅ ·
**6 cards** (waiting, travel, weather ×2, **daylight**, pin).
Believable — but relies on the audience knowing Italian lunch service ends at 15:00, and
194 min of recovered waiting is a big number to defend. **Retains every original proof point.**

### N2 — Early dinner: Bistrot pinned **18:00**, corrected **19:15** ← **RECOMMENDED**

*"Bob booked dinner for six. Venetian kitchens don't open until seven."*

Conflict ✅ (same message) · travel **74 → 65** · waiting **145 → 37** · unavailable-period row ✅ ·
**5 cards** (waiting, travel, weather ×2, pin). **No daylight card.**
The most instantly believable mistake of any candidate, and the only one needing no cultural
footnote. Waiting of 108 min reads as a real jet-lagged afternoon rather than a data artefact.

### N3 — Leon d'Oro (real dinner service from 17:00), pinned **16:00** → **17:30**

Conflict ✅ *"Leon d'Oro is locked to a time when it is closed."* · travel **82 → 62** ·
waiting **206 → 30** · 5 cards including daylight.
Strong numbers, but the *mistake* is a 4:00 PM dinner — the identical believability flaw being
corrected here. **Rejected for the same reason as the original.**

### N4 — Museum before opening: Guggenheim pinned **09:15** → **10:15**, dinner unpinned

Conflict ✅ *"Peggy Guggenheim Collection is locked to a time when it is closed."* ·
travel **52 → 64 (worse)** · waiting 213 → 16 · 4 cards.
Believable, but **travel gets worse**, so the required travel reduction is lost and the panel is
the weakest of the set. **Rejected.**

### N5 — Split-hours attraction (Bovolo pinned inside its own 13:30–14:00 closure)

Conflict ✅ and the most technically impressive proof (the one-interval rule), but it cannot be
explained in ten seconds: the audience must hold two intervals and a duration simultaneously.
**Rejected on clarity**, retained as a Q&A answer.

## Ranking

| Rank | Candidate | Realism | Clear <10 s | Honest capabilities | Reliability | Panel strength |
|---|---|---|---|---|---|---|
| **1** | **N2 early dinner** | **Highest** — needs no footnote | **Highest** | 5 cards + unavailable row + lock + hours | Deterministic | Strong; believable numbers |
| 2 | N1 late lunch | High | High | **6 cards** (adds daylight) | Deterministic | Strong; 194 min is a lot to defend |
| 3 | N3 Leon d'Oro | Low — 4 PM dinner | Medium | 5 cards | Deterministic | Strong numbers |
| 4 | N4 museum | High | High | 4 cards, **travel worsens** | Deterministic | Weakest |
| 5 | N5 split hours | High | **Low** | 4 cards | Deterministic | Medium |

## Recommendation — replace with **N2, the early-dinner mistake**

Narrative realism was the reason for this rework and is the first ranking criterion, and N2 wins
it outright: *"I booked dinner for six; they don't open until seven"* is a mistake every traveller
to Italy has either made or heard about. It needs no setup, no cultural footnote, and no defending.

**The single sacrifice is the daylight card.** This is structural, not tuneable: a daylight
improvement requires an outdoor activity to start after 19:00 (the production `DaylightPolicy`
boundary) and move earlier — and with an evening dinner pinned, nothing else can occupy the
evening. Two genuine *weather* improvements survive, so the "moved outdoors away from bad
conditions" story is still told, just once rather than twice.

Everything else the brief asked to retain is intact: named pre-search conflict, one simple
correction, the pin held at exactly 19:15, all five activities once each, unchanged durations,
opening-hours enforcement, the 15:00–16:00 call avoided **with the row saying so**, a genuine
travel reduction and a large waiting reduction.

### N2 — exact seed

**Availability 11:00–21:00** (Bob sleeps off the flight) · **unavailable 15:00–16:00** (call with
head office) · Wed 12 Aug 2026 · walking · weather LOW until 15:00, MEDIUM 15:00–16:00, **HIGH from
16:00**.

| Time | Activity | Note |
|---|---|---|
| 11:30–12:15 | Libreria Acqua Alta | east |
| 12:30–14:00 | Gallerie dell'Accademia | far west — the long hop |
| 15:15–16:15 | Piazza San Marco | sits across the call |
| 16:30–17:30 | Giardini della Biennale | far east, heavy rain |
| **18:00–19:30** | **Bistrot de Venise — PINNED, and shut until 19:00** | the mistake |

Corrected pin: **19:15–20:45**.

### N2 — expected output, verbatim

**Beat 1:** *"Bistrot de Venise is locked to a time when it is closed. Your Day Plan was not
changed."* (status `CONFLICT`, rejected by `ProblemValidator` before any search)

**Beat 2:** *"Proposed schedule: 4 of 5 activities moved. Nothing changes until you choose Apply."*
**Travel 74 → 65 · Waiting 145 → 37 · 4 of 5 moved**

| Time | Row | Badge / reason |
|---|---|---|
| 11:00–11:45 | Libreria Acqua Alta | moved |
| 11:45–12:05 | ↳ Travel to Giardini della Biennale | |
| 12:05–13:05 | Giardini della Biennale | moved |
| 13:05–13:25 | ↳ Travel to Piazza San Marco | |
| 13:25–14:25 | Piazza San Marco | moved |
| 14:25–14:38 | ↳ Travel to Gallerie dell'Accademia | |
| **16:00–17:30** | Gallerie dell'Accademia | moved — **"moved clear of your unavailable time"** |
| 17:30–17:42 | ↳ Travel to Bistrot de Venise | |
| **19:15–20:45** | **Bistrot de Venise** | **🔒 Locked — "you locked this time"** |

Cards, in Presenter order:
1. ⏳ **108 min of waiting removed** — Less dead time between activities
2. → **9 min less travel** — Shorter journeys than your current order
3. ☂ **Moved to better weather** — Giardini della Biennale
4. ☂ **Moved to better weather** — Piazza San Marco
5. ⚿ **Pinned activity kept at its time** — Bistrot de Venise

### N2 — revised storyboard (≈ 68 s)

| # | Action | Say | Proves | Time |
|---|---|---|---|---|
| 1 | Show Day Plan | "Bob's Wednesday in Venice. He landed late, so he starts at eleven — and his day is in the order he happened to add things." | Before View | 9 s |
| 2 | Click padlock on Bistrot | "Dinner with a client is fixed, so he pins it. He's booked six o'clock." | Pin as intent | 8 s |
| 3 | Autoschedule → Preview | "When Bob clicks Preview, the View passes his selections to the Controller, which packages them for the Autoschedule Interactor." | CA, one sentence | 7 s |
| 4 | *(conflict)* | "It stops before it even searches. *Bistrot de Venise is locked to a time when it is closed* — Venetian kitchens don't open until seven. And his Day Plan is untouched." | **Named hard-constraint failure, atomicity** | 13 s |
| 5 | Edit pin → 19:15 | "One fix: quarter past seven." | Single correction | 6 s |
| 6 | Preview | "Now it works. Dinner is exactly where he put it, and the day is rebuilt around it — the gardens move out of the afternoon rain, and the Accademia steps aside for his three o'clock call." | Lock · weather · unavailable period | 15 s |
| 7 | Point at cards | "Waiting drops from 145 minutes to 37, travel from 74 to 65 — and every card is a before-and-after the use case computed, not a label." | Soft improvements, honesty | 8 s |
| 8 | — | "Nothing changes until he presses Apply." | Preview immutability | 4 s |

**Live actions: 4.** Screenshot plan, offline fallback, UI adjustment (§9) and file list (§11) are
unchanged, except that V1–V5 capture the N2 seed and the Venice seed class would encode
availability 11:00–21:00.

### If you want the daylight card back

Use **N1** instead: identical in every other respect, adds ☀ *"Moved into daylight"*, and costs
believability only to the extent that the audience must know Italian lunch service ends at 15:00.
It is a genuine second choice, not a fallback.

---

# N1 redesigned — the irrational-pin problem, resolved

## The flaw

The earlier N1 put the business call at **15:00–16:00** and the mistaken pin at **15:30**.
Even though the validator reports the opening-hours conflict first, the *story* had Bob
knowingly booking lunch during his own call. The audience notices that before they notice the
constraint firing.

**The rule that fixes it:** only the **pin** must be consistent with the call. An *unpinned*
activity overlapping the call is natural — Bob added activities before he knew about the call,
and resolving that clash is exactly what Autoschedule is for. It is also what produces the
"moved clear of your unavailable time" evidence, so it is worth keeping.

Constraint: the call must overlap neither the invalid pin (15:30–17:00) nor the corrected pin
(13:15–14:45). That leaves mornings, or anything from 17:00.

## Variants tested (all through the real production path)

| Var | Call | Travel | Waiting | Cards | Unavailable-row | Notes |
|---|---|---|---|---|---|---|
| V1 | 17:00–18:00 | 61→55 | 209→0 | 6 | ❌ **none** | Engine front-loads the day, so an evening call is never in the way. Respected but invisible |
| V2 | 10:00–11:00 | 61→61 **none** | 209→14 | 6 | ❌ | Adds an odd "original order was kept" card |
| V3 | 18:00–19:00 | 61→55 | 209→0 | 6 | ❌ | Same blind spot as V1 |
| V4/W4 | 09:30–10:30 | 61→59 | 209→31 | 6 | ✅ Accademia | Works, but "2 min less travel" is a weak card |
| W1 | 09:30–10:30 | 67→59 | 203→31 | 5 | ✅ Accademia | 8 min travel, but only one weather card |
| **X1** | **09:30–10:30** | **73→59** | **197→31** | **6** | ✅ **Accademia** | **SELECTED** |

The decisive finding: **only a morning call yields the "moved clear" row.** The search packs the
day forwards, so an evening block is satisfied without anything having to move — the constraint
is honoured but never *shown*. X1 then recovers a strong travel number by making the initial
ordering genuinely bad (north-east → south-west → centre → south-east → centre) rather than by
moving the call.

## X1 — selected replacement for N1

**Availability 09:00–21:00** · **call 09:30–10:30** ("catch head office before the day starts")
· Wed 12 Aug 2026 · walking · weather LOW until 17:00, MEDIUM 17:00, **HIGH from 18:00**.

| Time | Activity | Position |
|---|---|---|
| 10:00–10:45 | Libreria Acqua Alta | north-east |
| 11:15–12:45 | Gallerie dell'Accademia | **far south-west — the long hop** |
| **15:30–17:00** | **Bistrot de Venise — PINNED, lunch service ended at 15:00** | centre |
| 17:15–18:15 | Giardini della Biennale | far south-east, rain |
| 19:15–20:15 | Piazza San Marco | centre, dark and raining |

Corrected pin: **13:15–14:45**.

Bob is now fully consistent: his call is at half nine, and the lunch he books — mistakenly at
half three, correctly at quarter past one — never touches it.

### Beat 1 — the mistake

> **Bistrot de Venise is locked to a time when it is closed. Your Day Plan was not changed.**

*"He booked a late lunch for half past three. In Venice the kitchen stops serving at three."*

### Beat 2 — the corrected preview

**Travel 73 → 59 · Waiting 197 → 31 · 4 of 5 moved**

| Time | Row | Badge / reason |
|---|---|---|
| **10:30–12:00** | Gallerie dell'Accademia | moved — **"moved clear of your unavailable time"** |
| 12:00–12:19 | ↳ Travel to Libreria Acqua Alta | |
| 12:19–13:04 | Libreria Acqua Alta | moved |
| 13:04–13:14 | ↳ Travel to Bistrot de Venise | |
| **13:15–14:45** | **Bistrot de Venise** | **🔒 Locked — "you locked this time"** |
| 14:45–14:55 | ↳ Travel to Piazza San Marco | |
| 14:55–15:55 | Piazza San Marco | moved |
| 15:55–16:15 | ↳ Travel to Giardini della Biennale | |
| 16:15–17:15 | Giardini della Biennale | moved |

Cards, in Presenter order:

1. ⏳ **166 min of waiting removed** — Less dead time between activities
2. → **14 min less travel** — Shorter journeys than your current order
3. ⚿ **Pinned activity kept at its time** — Bistrot de Venise
4. ☂ **Moved to better weather** — Piazza San Marco
5. ☀ **Moved into daylight** — Piazza San Marco
6. ☂ **Moved to better weather** — Giardini della Biennale

### Everything the brief asked to retain

| Requirement | X1 |
|---|---|
| Believable late-lunch pin rejected because service ended | ✅ 15:30, kitchen closed at 15:00 |
| One simple correction | ✅ 15:30 → 13:15 |
| Corrected pin preserved exactly | ✅ 13:15–14:45 |
| Five activities, unchanged durations | ✅ 45/90/90/60/60 |
| Business call visibly avoided by another activity | ✅ Accademia's own row says so |
| Genuine travel reduction | ✅ **14 min** |
| Genuine waiting reduction | ✅ **166 min** |
| Two weather improvements | ✅ San Marco and Giardini |
| Daylight improvement card | ✅ San Marco |
| Clear pinned-activity card | ✅ |
| No irrational user behaviour | ✅ the call cannot clash with either pin |

**X1 replaces both the original selection and N2 as the recommended scenario.** It is the only
variant that satisfies every item on the list at once.

---

# Route line on the map — implemented

`MapPanel` now joins the Day Plan's stops in order with a dashed, muted polyline drawn beneath
the numbered pins (commit `39d1923`). The map and Day Plan sit side by side in a `JSplitPane`,
so this is visible during the whole demo without switching tabs.

**Why it matters for the Before View beat.** "The day backtracks" was previously a claim the
audience had to take on trust; the numbered pins carried the order but nobody reads five scattered
pins as a shape. As a line, the zig-zag is a picture — and after Apply the same line comes out as
a west-to-east sweep, which is a genuine before/after with no extra narration.

Tested by `MapRouteLineTest`: the line follows Day Plan order rather than load order, a single
stop draws nothing, and a scheduled stop whose place is not currently on the map is skipped
rather than drawn to nowhere.

**Suggested Before View narration (≈4 s):** *"One, two — that's right across the city — three,
four back east again."* Point at the line, not the list.
