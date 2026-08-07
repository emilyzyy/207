# Autoschedule UI polish — design directions

Written 2026-08-06 on `feature/autoschedule-ui-polish`, from `main` @ `8542835`.

Scope: visual and interaction polish only. The scheduling algorithm, scoring, constraints,
gateways, use-case boundaries, package structure and persistence are finished and are not
touched by any option below.

## What is actually there today

Read from source, not from memory. The Day Plan tab is `DayPlanPanel`, a `JPanel` with
`BorderLayout`:

```
DayPlanPanel (BorderLayout, white, 16px inset)
├─ NORTH  header()      JPanel BorderLayout: "Day Plan" (HEADING) | contract line (SMALL, MUTED)
├─ CENTER JScrollPane
│         └─ centre     JPanel BoxLayout Y
│            ├─ eventList    JPanel BoxLayout Y   ← current plan
│            │   ├─ JLabel "Your Day Plan" / "Your Day Plan now"
│            │   └─ eventCard(...) per ScheduledEvent
│            │        JPanel BorderLayout(12,5), SwingTheme.styleCard
│            │        ├─ WEST   JLabel "09:00 – 10:00"   (BODY bold, BLUE)
│            │        ├─ CENTER JLabel <html> name + notes + hourly weather lines
│            │        └─ EAST   JPanel FlowLayout: JCheckBox "Lock", Edit, Remove
│            └─ previewArea  JPanel BoxLayout Y  ← proposal
│                ├─ JLabel "Proposed schedule (not applied yet)" (BODY bold, BLUE)
│                ├─ JLabel metrics, one dense line
│                ├─ noticeLabel(...) per warning, SMALL/MUTED
│                ├─ previewCard(row) per PreviewRowView
│                │    JPanel BorderLayout(12,4), same styleCard as an activity
│                │    ├─ WEST   JLabel row.getTimeLabel()
│                │    └─ CENTER JLabel <html> title + " [locked]" / " [moved]" + reason
│                └─ whySection(state) when whButton.isSelected()
└─ SOUTH  actions()     status JLabel, objective JLabel,
                        FlowLayout: Autoschedule | Apply | Cancel | Why these times? | Calendar View
```

State comes from `DayPlanState` (immutable, 15 fields) via `DayPlanViewModel`
(`PropertyChangeSupport`). Relevant fields: `events`, `previewRows`, `metrics`, `warnings`,
`objectiveSummary`, `lockedEventIds`, `hourlyWeather`, `status`, `travelQualityNote`,
`searchCompletedWithinLimit`. `PreviewRowView.getTimeLabel()` returns `start + " - " + end`.

`SwingTheme` already supplies the palette: `NAVY #0D2340`, `BLUE #1F68E1`,
`BLUE_SOFT #EEF5FF`, `BACKGROUND #F4F7FA`, `PANEL` white, `LINE #D8E0E8`, `MUTED #5B6A7B`,
`SUCCESS`, `ERROR`, and fonts `TITLE/HEADING/BODY/SMALL`. It has `cardBorder()`,
`styleCard()`, `primaryButton()`, `secondaryButton()`, `placeholderButton()`.

**There is no icon system and no `src/main/resources`.** Nothing in `adapters/views`
implements `Icon`, loads an `ImageIcon`, or calls `setIcon`. Any icon has to be drawn with
Java2D or added as a new resource pipeline.

### Concrete problems, from the captured renders

`docs/autoschedule/screenshots/` before this pass:

1. **24-hour times everywhere** — `09:00 – 10:00`, `15:30 – 16:30`.
2. **Lock is a bare `JCheckBox` labelled "Lock"** — no lock affordance, reads as a form field.
3. **Travel rows are visually identical to activities** — same `styleCard`, same border, same
   size. Only the time colour differs (`MUTED` vs `BLUE`), which is colour-only signalling.
4. **`[locked]` and `[moved]` are bracketed text** inside an HTML label.
5. **Metrics are one dense line**: `Travel 0 min to 132 min · waiting 270 min to 60 min · 2 of
   3 moved`.
6. **Warnings sit immediately under the metrics as identical SMALL/MUTED labels**, so a
   routing caveat looks like part of the figures.
7. **Current and proposed plan are separated only by a heading** — the two card stacks run
   together in one scroll column.
8. **Five buttons of three different sizes compete** in the action row: `Autoschedule`
   (primary, blue), `Apply` (primary, blue), `Cancel` (default), `Why these times?`
   (`JToggleButton`, SMALL), `Calendar View` (default).
9. **Settings dialog is default Swing grey** — `GridLayout(3,2)` for the hour fields, no
   section grouping, no white surface, and dead grey space to the right of the fields.

---

## Direction 1 — Side-by-side comparison

**Concept.** Put the current plan and the proposal in two columns so the before/after is a
literal side-by-side, using a `JSplitPane` or a two-column `GridLayout(1,2)` inside the
existing scroll pane.

```
┌ Day Plan ─────────────────────────────────────────────┐
│ ┌ Now ──────────────┐ ┌ Proposed ───────────────────┐ │
│ │ 9:00 AM  High Park│ │ 11:00 AM ROM      [locked]  │ │
│ │ 11:00 AM ROM  🔒  │ │ 12:00 PM ↳ travel           │ │
│ │ 3:30 PM  Market   │ │ 12:37 PM Market   [moved]   │ │
│ └───────────────────┘ └─────────────────────────────┘ │
│ Travel 0→132  Waiting 270→60  Moved 2/3               │
└───────────────────────────────────────────────────────┘
```

- **Separation:** strongest possible — two panes, each with its own header.
- **Metrics:** a strip beneath both columns, spanning the width.
- **Activity vs travel:** travel rows indented inside the proposed column.
- **Moved/locked:** badges in the right column only.
- **Reasoning/warnings:** below the metrics strip, full width.
- **Settings dialog:** unchanged by this direction.
- **Accessibility:** two scroll regions to tab through; screen-reader order becomes
  column-major, which reads oddly for a schedule that is inherently one timeline.
- **Complexity:** high. `eventCard` and `previewCard` currently share the panel's single
  `BoxLayout` column; splitting means restructuring `centre`, and every card must survive at
  roughly half its present width.
- **Risk to shared files:** high. `DayPlanPanel` is Shiyuan's file, already carrying Alex's
  selection and Shiyuan's weather. A layout rewrite touches all of it.
- **Verdict:** the comparison is genuinely clearer, but the Day Plan lives in a tab beside
  Search/Bookmarks/Trip Options and is not guaranteed width. Weather lines already wrap; at
  half width they would dominate. Rejected as too risky for a polish pass.

## Direction 2 — Sectioned card stack *(selected)*

**Concept.** Keep the single vertical timeline the panel already is, and spend the effort on
hierarchy: real section headers, a metric strip, differentiated row treatments, badges, a
warning band, and a disclosure for reasoning.

```
┌ Day Plan ────────────────────── Autoschedule reorders… ┐
│ YOUR DAY PLAN ─────────────────────────────────────────│  section rule
│ ┌────────────────────────────────────────────────────┐ │
│ │ 9:00 AM      High Park                    🔓 ✎ 🗑  │ │  white card, BLUE time
│ │ – 10:00 AM   Sunny intervals · 24°C                │ │  weather, MUTED, truncated
│ └────────────────────────────────────────────────────┘ │
│ ┌────────────────────────────────────────────────────┐ │
│ │ 11:00 AM     Royal Ontario Museum         🔒 ✎ 🗑  │ │  locked: BLUE_SOFT tint
│ └────────────────────────────────────────────────────┘ │
│                                                        │
│ PROPOSED SCHEDULE ─────────────────  not applied yet ──│  BLUE rule
│ ┌ Travel ──┐ ┌ Waiting ─┐ ┌ Moved ───┐                 │  three metric cards
│ │ 0 → 12   │ │ 135 → 0  │ │ 2 of 2   │                 │
│ │ minutes  │ │ minutes  │ │ activities│                │
│ └──────────┘ └──────────┘ └──────────┘                 │
│ ⚠ Travel times may include estimates.                  │  warning band, amber-tinted
│ ┌────────────────────────────────────────────────────┐ │
│ │ 11:00 AM – 12:00 PM  Royal Ontario Museum  [Locked]│ │  activity: full card
│ └────────────────────────────────────────────────────┘ │
│   ↳ 12:00 PM – 12:37 PM  Travel to St Lawrence Market  │  travel: inset, tinted, no border
│ ┌────────────────────────────────────────────────────┐ │
│ │ 12:37 PM – 1:37 PM   St Lawrence Market    [Moved] │ │
│ └────────────────────────────────────────────────────┘ │
│ ▸ Why this schedule?                                   │  disclosure, collapsed
├────────────────────────────────────────────────────────┤
│ status line                                            │
│ [Apply]  [Cancel]              [Autoschedule] [Calendar]│  primary left, secondary right
└────────────────────────────────────────────────────────┘
```

- **Separation:** two labelled sections with a horizontal rule and different accent colours —
  `NAVY` for the current plan, `BLUE` for the proposal. The proposal's section header also
  carries "not applied yet" on the right.
- **Metrics:** three small cards in a `FlowLayout` row, each showing `before → after` and a
  unit caption. Replaces the dense sentence.
- **Activity vs travel:** activities keep the white `styleCard`. Travel rows lose the border,
  gain a `BLUE_SOFT`/`BACKGROUND` tint, sit indented with a `↳` glyph, use `SMALL` font, and
  are captioned `Travel to <destination>` — three independent signals, not colour alone.
- **Moved/locked:** a small rounded badge component (`JLabel` with a painted background),
  `[Locked]` in `BLUE_SOFT`/`BLUE`, `[Moved]` in a neutral tint, with an accessible name.
- **Reasoning:** the existing `whyButton` becomes a `▸/▾` disclosure titled "Why this
  schedule?", and reasons are grouped by category rather than listed per row.
- **Warnings:** a dedicated band above the rows with a `⚠` glyph and a tinted background,
  distinct from both metrics and reasons. Conflicts and errors stay in the always-visible
  status line, never inside the disclosure.
- **Settings dialog:** white surface, three labelled groups (When you are free / Times you are
  not available / Preferences), right-aligned labels, consistent field widths, primary and
  secondary buttons separated.
- **Accessibility:** reading order stays a single top-to-bottom timeline, matching the domain.
  Every new control gets an accessible name; the lock is a `JToggleButton`, focusable and
  space-activated. Badges are text, not colour.
- **Complexity:** moderate. `renderItinerary`, `renderPreview`, `previewCard`, `eventCard` and
  `whySection` are rewritten; the panel's outer structure, listeners and controller calls are
  untouched.
- **Risk to shared files:** contained. Changes are inside `DayPlanPanel`'s private render
  methods and additive helpers in `SwingTheme`. No change to `DayPlanState`'s meaning, the
  controller, or the presenter's data.

## Direction 3 — Toggle between Now and Proposed

**Concept.** One card stack, with a `JTabbedPane` or segmented toggle switching between
"Now" and "Proposed". Metrics stay pinned above.

- **Separation:** total, but by hiding one side.
- **Metrics:** pinned strip, always visible.
- **Complexity:** low — the smallest diff of the four.
- **Accessibility:** fewer components on screen, less scrolling.
- **Verdict:** rejected. The before/after comparison is the individual rubric's Required
  Element and the core of the demo; a design where the two are never visible together
  actively works against the thing this feature has to show. Cheap is not the criterion here.

## Direction 4 — Timeline rail

**Concept.** A custom-painted vertical rail down the left with dots at each activity and a
thin connector through travel, times set against the rail.

- **Separation:** two rails, or one rail with a branch.
- **Complexity:** high — a custom `JComponent` with `paintComponent`, hit-testing for the lock
  control, and manual layout maths for row heights that already vary with weather lines.
- **Accessibility:** a painted rail conveys structure only visually; every relationship it
  shows would need a text equivalent anyway.
- **Verdict:** the most attractive of the four and the least appropriate. It is a redesign,
  not a polish pass, and it puts custom painting in a shared file days before submission.

---

## Selected direction: 2 — Sectioned card stack

**Why.**

1. **It fixes the real problems.** Every item in the list above — 24-hour times, the invisible
   lock, travel indistinguishable from activities, bracketed status text, the dense metric
   line, warnings mixed into figures, weak section separation, competing buttons, the grey
   dialog — is addressed directly. Directions 1 and 4 fix some of them while introducing
   layout risk; Direction 3 fixes the fewest and damages the demo.

2. **It keeps the before/after visible together**, which the Individual Presentation Rubric
   requires and the demo depends on.

3. **It matches CloseAI rather than replacing it.** Everything is drawn from the existing
   `SwingTheme` palette and fonts. The only new visual primitives are a badge, a metric card,
   a warning band and a lock icon — all small, all reusable, all in the established style.

4. **It is the lowest-risk change to shared files.** `DayPlanPanel` is Shiyuan's file and now
   also carries Alex's selection wiring and Shiyuan's weather rendering. Direction 2 rewrites
   its private render methods and leaves its structure, listeners, controller calls and both
   teammates' features intact. Directions 1 and 4 would restructure the file around them.

5. **The reading order stays a timeline**, which is what a day is. That is better for screen
   readers than a two-column layout, not merely easier.

**What it deliberately does not do.** No new preference, no algorithm change, no new
ViewModel semantics. The only state addition considered is presentation-only formatting;
`DayPlanState`'s fields keep their exact current meaning, and business logic stays out of
Swing.

**Known trade-off.** A vertical stack still grows long with many activities, and the Preview
roughly doubles the row count. Direction 2 mitigates this with lighter travel rows and a
collapsed reasoning section, but does not eliminate it. If the day-length problem becomes
real, Direction 1 is the natural follow-up — and this pass leaves the card components in a
shape that a two-column layout could reuse.
