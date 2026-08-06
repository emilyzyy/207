# Autoschedule screenshot checklist

The screenshot list comes from `docs/autoschedule/verification-evidence.md` §7 — six states,
not guessed. This batch added a seventh (2b) because the weather preference now has two
distinct appearances and only one of them occurs today.

Captured 2026-08-06. **Seven of seven captured automatically** and committed under
`docs/autoschedule/screenshots/`.

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
| **Expected visible result** | Three activities in their original, deliberately poor order: High Park 09:00–10:00, Royal Ontario Museum 11:00–12:00 (after it closes is the point of the demo narrative), St Lawrence Market 15:30–16:30. Status reads "Add activities, then choose Autoschedule." |
| **Must appear in the crop** | All three time ranges and names; the Lock checkbox on every row; the Autoschedule button. |
| **Must not appear** | Any proposed schedule; Apply or Cancel (both hidden outside a preview); any terminal window, environment variable, URL or personal information. |

### 2. Settings — weather withheld (the state that occurs today)

| | |
|---|---|
| **File** | `screenshots/02-settings-weather-withheld.png` |
| **Purpose** | Shows both user preferences and the capability gate. Supports Use-case Explanation and the accessibility descriptor. *(engineering judgment, not individually rubric-named)* |
| **Starting state** | From state 1, choose **Autoschedule**. |
| **Actions and settings** | Let the weather capability lookup finish (it is immediate offline). Optionally add one unavailable period. |
| **Expected visible result** | Available from 09:00, until 21:00, mode WALKING. **"Keep my current order where possible" ticked.** **"Consider weather" unticked and greyed**, with the sentence *"Hourly weather is not available for this trip date."* beneath it. |
| **Must appear in the crop** | Both checkboxes, the weather explanation sentence, and the Generate Preview button. |
| **Must not appear** | Any API key, authenticated URL, or the "Checking hourly weather…" transitional text. |

### 2b. Settings — weather available

| | |
|---|---|
| **File** | `screenshots/02b-settings-weather-available.png` |
| **Purpose** | Shows the other half of the gate: when a forecast can distinguish hours, the box is enabled and **on by default**. Needed because the shipped adapters cannot produce this state, so without it the design looks like a permanently dead control. |
| **Starting state** | Requires a gateway returning an hourly `WeatherContext`. Not reachable through the shipped weather adapters today. |
| **Actions and settings** | Captured through the same dialog with an hourly forecast supplied. |
| **Expected visible result** | "Consider weather" **enabled and ticked**, no explanation sentence. |
| **Must appear in the crop** | The enabled, ticked checkbox and the absence of the withheld-reason line. |
| **Must not appear** | Anything implying this is the current production behaviour — say plainly that it is the state an hourly gateway would produce. |

### 3. Preview — the proposal, with the plan still untouched

| | |
|---|---|
| **File** | `screenshots/03-preview.png` |
| **Purpose** | Use-case Explanation and functionality: preview-and-confirm, before/after metrics, per-row reasons. *(not individually rubric-named — see above)* |
| **Starting state** | From state 1, tick **Lock** on Royal Ontario Museum. |
| **Actions and settings** | Autoschedule → keep the defaults → **Generate Preview**. |
| **Expected visible result** | "Your Day Plan now" unchanged at the top with the museum's Lock still ticked; "Proposed schedule (not applied yet)" beneath it with a metrics line (travel, waiting, "2 of 3 moved"), travel rows, `[locked]` and `[moved]` tags, and per-row reasons ("you locked this time", "a usual mealtime", "in daylight"); the objective summary line; Apply and Cancel now visible. |
| **Must appear in the crop** | The unchanged original plan **and** the proposal together — that juxtaposition is the whole point. The metrics line and at least one reason. |
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
never rendered a shell. Verified by inspecting all seven images.
