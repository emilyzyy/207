# Autoschedule screenshot checklist

The screenshot list comes from `docs/autoschedule/verification-evidence.md` §7 — six states,
not guessed. This batch added a seventh (2b) because the weather preference now has two
distinct appearances and only one of them occurs today.

Captured 2026-08-06, re-captured after the UI polish pass, and **re-captured again
2026-08-07** on `feature/autoschedule-ui-polish` after merging `origin/main`, Shiyuan
(Dennis) Lyu's hourly forecast popup and real opening hours. Eight of eight captured
automatically and committed under `docs/autoschedule/screenshots/`.

Three things changed visibly in that pass and are why 1, 3, 5 and 6 were re-taken:

- **Per-row weather.** Merging `origin/main` added an hourly condition line under each Day
  Plan activity. Not an Autoschedule change, but it is in every Day Plan shot.
- **The opening-hours warning.** Shot 3's warning band now carries a second line naming the
  venues scheduled without hours data. This is the visible half of the honesty rule and is
  the reason shot 3 matters more than it did.
- **Dennis's forecast popup**, added as shot 7.

Every shot shows the polished interface: a 12-hour clock throughout, visible padlock
controls, section headers, the `SCHEDULE IMPROVEMENTS` stack, `Locked`/`Moved` badges, a
distinct warning band, and travel rows that no longer look like activities.

The shots use the deterministic five-activity `AutoscheduleDemoTrip` with its fixed hourly
forecast, so they can be reproduced exactly on any machine without a network. The six
improvement cards visible in shot 3 are produced by the real Interactor and asserted by
`AutoscheduleDemoImprovementsTest`.

## Which captures are automated, and which need a person

| # | Capture | How |
|---|---|---|
| 1, 2, 2b, 3, 5, 6 | Day Plan panel and settings dialog | **Automated component render** — the real production components painted offscreen at a fixed 1180×732 (dialogs at their packed size). |
| 7 | Dennis's hourly forecast popup | **Automated component render** of `HourlyWeatherPanel` at the dialog's own 640×520, scrolled to the afternoon. See the note under shot 7. |
| 4 | Expanded "Why this schedule?" | **Automated, but of the scroll pane's content rather than the window.** The expanded reasoning falls below a 692px viewport, so the capture is of the scrollable view at its full height. It is the real component, not a mock-up, but it is not what a 900×692 window shows without scrolling. |
| — | Whole application window, Calendar View after Apply | **Manual only.** The harness renders panels, not the frame, tab strip or native chrome. |

## How these were captured, and what that means

Java's own `Component.printAll(Graphics)` renders the real Swing components offscreen into
a `BufferedImage`. No extra software was installed, and no screen-recording permission was
needed. The harness builds the components through `AppBuilder.buildOffline()` and the same
production wiring the application uses, drives them with the real Controller, and paints
what results.

**These are genuine renders of the production components, not mock-ups**, and every state
below was reached by the real use case rather than staged. Two honest limitations:

- They show the Day Plan panel and the settings dialog, **not** the surrounding application
  window — no title bar, no tab strip, no native window chrome.
- Fonts and control styling are the platform defaults as rendered offscreen, which can
  differ very slightly from an on-screen window.

For a presentation slide these are sufficient and accurate. If a shot of the whole
application window is wanted, capture 1, 3, 5 and 6 by hand using the steps below; the
states are identical.

## Is the "four screenshots are individually rubric-relevant" claim supported?

**Partly. The earlier claim is corrected here.**

`AUTOSCHEDULE_IMPLEMENTATION_STATE.md` previously said "Steps 1, 3, 5 and 6 of that list
are the before/after/failure evidence the individual rubric asks for." Checking that against
the local course audit:

| Claim | Status | Source |
|---|---|---|
| The Individual Presentation Rubric requires **before and after Views** | **Verified** | S-017 (`source-ledger.md`); `course-source-verification.md` §26, §64; `project-requirements.md` §22 |
| Screenshot **1 (Before)** satisfies "before View" | **Verified** | same |
| Screenshot **5 (After Apply)** satisfies "after View" | **Verified** | same |
| Screenshot **3 (Preview)** is individually rubric-required | **Unverified** | The rubric names before/after Views, Interactor code and the full use-case class diagram. It does not name an intermediate preview state. |
| Screenshot **6 (Conflict)** is individually rubric-required | **Unverified** | The individual rubric names no failure-state screenshot. |

The rubric's three Required Elements are: **before and after Views, Use Case Interactor
code, and a class diagram for the full use case.** Only two of the six screenshots map
directly onto that list.

All three Required Elements now exist in the repository:

| Required Element | Where |
|---|---|
| Before and after Views | `screenshots/01-before-day-plan.png` and `05-after-apply.png` |
| Use Case Interactor code | `src/main/java/closeai/application/autoschedule/AutoScheduleInteractor.java` |
| Full use-case class diagram | [`../diagrams/`](../diagrams/) — PlantUML source, SVG and PNG |

Screenshots 3 and 6 are still worth capturing, but for reasons that should be stated
accurately rather than attributed to the individual rubric:

- **3 (Preview)** shows the preview-and-confirm design and the before/after metrics, which
  supports the *Use-case Explanation* category and the group rubric's functionality
  descriptor.
- **6 (Conflict)** shows structured failure and tolerance for error, which supports the
  group rubric's **accessibility** category. The team's own
  `autoschedule-presentation-plan.md` §271 asks for "before/after result metrics and
  structured failure" — a team plan, not a course rubric.

**Also unverified, and worth noting:** no course source found in the audit requires
screenshots at all as a submitted artefact. The rubric asks that before/after Views be
*shown* in the presentation, which a live demo also satisfies. These files are the
fallback, and the fallback is legitimate (Piazza @280).

## The checklist

Filenames below are the ones committed.

---

### 1. Before — the inefficient seeded day

| | |
|---|---|
| **File** | `screenshots/01-before-day-plan.png` |
| **Purpose** | Individual rubric Required Elements: the **"before" View**. *(verified)* |
| **Starting state** | App launched offline; seeded `demo-trip` (Toronto, 2026-08-12, 09:00–21:00, walking) loaded; Day Plan tab open; no preview run. |
| **Actions and settings** | None. Capture as opened. |
| **Expected visible result** | A `YOUR DAY PLAN` section header, then the five activity cards of the seeded day on a 12-hour clock: Royal Ontario Museum 11:00 AM – 12:00 PM, Distillery District 1:00 PM – 2:00 PM, St Lawrence Market 3:30 PM – 4:30 PM, Casa Loma 5:00 PM – 6:00 PM, High Park 7:30 PM – 8:30 PM. Each carries an **open padlock**, Edit and Remove, and an hourly weather line. Status reads "Add activities, then choose Autoschedule." |
| **Must appear in the crop** | All five time ranges in AM/PM; the padlock control on every row; the Autoschedule button. A locked row is tinted blue and shows a **closed** padlock. |
| **Must not appear** | Any proposed schedule; Apply or Cancel (both hidden outside a preview); any terminal window, environment variable, URL or personal information. |

### 2. Settings — weather offered (the state that occurs today)

| | |
|---|---|
| **File** | `screenshots/02-settings-weather-available.png` |
| **Purpose** | Shows both user preferences and the capability gate. Supports Use-case Explanation and the accessibility descriptor. *(engineering judgment, not individually rubric-named)* |
| **Starting state** | From state 1, choose **Autoschedule**. |
| **Actions and settings** | Let the weather capability lookup finish (it is immediate offline). Optionally add one unavailable period. |
| **Expected visible result** | Available from 09:00, until 21:00, mode WALKING. **Both checkboxes ticked and enabled** — "Keep my current order where possible" and "Consider weather". Since Shiyuan's hourly forecast landed, this is the production state. |
| **Must appear in the crop** | Both ticked checkboxes and the Generate Preview button. |
| **Must not appear** | Any API key, authenticated URL, or the "Checking hourly weather…" transitional text. |

### 2b. Settings — weather withheld (the degraded path)

| | |
|---|---|
| **File** | `screenshots/02b-settings-weather-withheld.png` |
| **Purpose** | Shows the other half of the gate: when no usable forecast exists the box is disabled, unticked and explained. Worth keeping because a provider can always fail, and it is the behaviour that makes the preference honest. |
| **Starting state** | Requires a gateway returning no usable forecast. Not the normal production state since the hourly adapter landed. |
| **Actions and settings** | Captured through the same dialog with an hourly forecast supplied. |
| **Expected visible result** | "Consider weather" **disabled and unticked**, with *"Hourly weather is not available for this trip date."* beneath it. |
| **Must appear in the crop** | The disabled checkbox and the explanation sentence. |
| **Must not appear** | Anything implying this is the current production behaviour — it is the fallback. |

### 3. Preview — the proposal, with the plan still untouched

| | |
|---|---|
| **File** | `screenshots/03-preview.png` |
| **Purpose** | Use-case Explanation and functionality: preview-and-confirm, before/after metrics, per-row reasons. *(not individually rubric-named — see above)* |
| **Starting state** | From state 1, tick **Lock** on Royal Ontario Museum. |
| **Actions and settings** | Autoschedule → keep the defaults → **Generate Preview**. |
| **Expected visible result** | `YOUR DAY PLAN` unchanged above with the museum still locked; a `PROPOSED SCHEDULE` header marked "not applied yet"; a **`SCHEDULE IMPROVEMENTS` stack on the right** with six cards (waiting removed, less travel, pinned kept, meal moved, into daylight, better weather); an amber warning band carrying **two** lines — *"Opening hours unavailable for Royal Ontario Museum, Distillery District and 3 more, so they were scheduled without that limit."* and the travel-estimate caveat; activity rows with `Locked` / `Moved` badges; travel rows tinted, indented and prefixed `↳`. Apply and Cancel visible; Autoschedule hidden. |
| **Must appear in the crop** | The unchanged original plan **and** the proposal together — that juxtaposition is the whole point. The improvements stack, **both lines of the warning band**, and at least one badge. Capture at **1180px or wider** so the stack sits beside the schedule; below that it moves underneath, which is correct but is not the shot to present. The panel must be sized *before* the preview runs, because the layout decision is made when the state is rendered. |
| **Must not appear** | Any suggestion the plan has already changed. |

### 4. Why these times — expanded

| | |
|---|---|
| **File** | `screenshots/04-why-these-times.png` |
| **Purpose** | Accessibility: explanations are an expandable focusable control, not a hover tooltip. |
| **Starting state** | State 3. |
| **Actions and settings** | Press **Why these times?**. |
| **Expected visible result** | The reason text expanded on the proposal rows. |
| **Must appear in the crop** | The toggle button and the expanded reasons. |
| **Must not appear** | — |

### 5. After Apply — the updated Day Plan

| | |
|---|---|
| **File** | `screenshots/05-after-apply.png` |
| **Purpose** | Individual rubric Required Elements: the **"after" View**. *(verified)* |
| **Starting state** | State 3. |
| **Actions and settings** | Press **Apply**. |
| **Expected visible result** | The Day Plan now holds the applied schedule — museum 11:00–12:00 (its locked time, honoured), travel rows, market 12:37–13:37, park 15:12–16:12. Status: "Autoschedule applied. Your Day Plan has been updated." |
| **Must appear in the crop** | The new times, the travel rows, and the confirmation message. Pair it with shot 1 on the slide. |
| **Must not appear** | Apply or Cancel (both hidden once applied). |
| **Also worth capturing by hand** | **Calendar View** showing the same times, which the automated capture does not include. |

### 6. Conflict — a pin inside an unavailable period

| | |
|---|---|
| **File** | `screenshots/06-conflict.png` |
| **Purpose** | Group rubric accessibility / tolerance for error: named, actionable failure. *(not individually rubric-named — see above)* |
| **Starting state** | State 1. |
| **Actions and settings** | Lock Royal Ontario Museum, then Autoschedule → add unavailable period **10:30–12:30** → Generate Preview. |
| **Expected visible result** | No proposal. Message: *"Royal Ontario Museum is locked to a time you marked as unavailable. Your Day Plan was not changed."* The original plan is intact above it. |
| **Must appear in the crop** | The message **naming the museum** and stating the plan was not changed, plus the untouched plan. |
| **Must not appear** | Any partial or greyed-out proposal. |

### 7. Hourly forecast popup — Dennis's, on the same forecast Autoschedule used

| | |
|---|---|
| **File** | `screenshots/07-hourly-weather-popup.png` |
| **Purpose** | Shows Shiyuan (Dennis) Lyu's live hourly forecast window, and that it and Autoschedule read one shared `DayPlanViewModel`. It is his feature, captured here because it is the other half of the weather story the Preview depends on. |
| **Starting state** | Overview tab, a trip with a forecast loaded. |
| **Actions and settings** | Press **WEATHER PREVIEW** on the Overview weather card. Scroll to the afternoon. |
| **Expected visible result** | "Hourly forecast", "Toronto · Wednesday, August 12, 2026", "24 HOURLY FORECASTS", then one card per hour on a 12-hour clock with condition, temperature, precipitation and a severity chip. The seeded forecast turns at 6 PM: LOW through the afternoon, **MEDIUM at 6:00 PM (Showers)**, **HIGH from 7:00 PM (Heavy rain)**. |
| **Must appear in the crop** | The 6 PM and 7 PM rows — that turn is exactly why Autoschedule moves High Park out of the evening, so it is what ties this shot to shot 3. |
| **Must not appear** | Any API key or authenticated URL; the forecast is the deterministic seeded one, not a live call. |
| **Honest note** | The dialog **opens at midnight**, since it lists all 24 hours from the top. This capture is scrolled to the afternoon, which is what a viewer does within a second of opening it — the alternative would have been six identical dark-hours rows that show nothing about the feature. Nothing else is staged. |

## Can the seeded demo produce every required state?

**Yes — verified, not assumed.** Every state above was reached through
`AppBuilder.buildOffline()` and the seeded `demo-trip` with no hand-assembled fixtures, and
each is also asserted by `AutoScheduleWalkthroughTest`. The one exception is **2b**, which
no shipped weather adapter can produce because none returns an hourly forecast; it is
covered by `AutoScheduleWalkthroughTest.anHourlyGatewayWouldOfferThePreferenceWithNoOtherChange`
and captured with an hourly gateway supplied. That limitation is stated wherever 2b appears.

## Safety check

No screenshot contains an API key, an authenticated URL, a terminal window, an environment
variable, or personal information. The captures are of application panels only; the harness
never rendered a shell. Verified by inspecting all eight images.
