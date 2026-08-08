# Autoschedule demo script

Roughly three minutes, compressible to two by dropping step 6. Every step below is also
executed as an automated test (`AutoScheduleWalkthroughTest`), so the demo is not relying on
anything assembled specially for it.

**Before starting:** launch with the seeded demo trip. Shiyuan (Dennis) Lyu's hourly
forecast has landed, so **"Consider weather" is enabled and ticked by default** — the
disabled state is now the fallback, not the norm. Do **not** claim traffic-aware driving:
no TomTom key has ever reached a verification run, so no real TomTom route has been
obtained. Driving falls back to OSRM and is not traffic-aware.

The seeded demo trip's venues publish **no** opening hours — they are deterministic offline
fixtures — so step 4b is talked through rather than shown. To show real hours, launch with
`-Dcloseai.places.mode=nominatim`, search a city and add a place that publishes them;
Raashid's adapter reads the tag on both search and map panning.

---

### 1. The problem (20s)

Open the **Day Plan**. Point out that this is a day someone actually put together and it is
a mess: the museum is scheduled for 11:00 but the day starts at the park across town, lunch
is at 15:30, and there is no travel time between anything.

> "These are the activities I want. The order and the times are the problem."

### 2. Pin something (15s)

Tick **Lock** on the museum.

> "I have a timed ticket for the museum, so that one cannot move. Everything else can."

### 3. Settings (20s)

Choose **Autoschedule**. Walk through the dialog quickly — it is deliberately short.

> "It only asks what it cannot work out: when I am free, how I am getting around, any time
> I am not available, and two preferences. Everything else — less travel, fewer gaps,
> sensible mealtimes, daylight outdoors — is always on, because that is what the feature is
> for."

Point at the **Consider weather** box, now ticked and enabled.

> "This is the interesting one. Weather is a preference rather than a built-in, and it is
> only offered when the forecast can actually tell one hour from another. Since Dennis's
> hourly forecast landed it can, so the box is enabled and ticked — and you can still turn
> it off. If the provider ever falls back to one outlook for the whole day, that scores
> every possible time identically, so rather than a checkbox that looks like a choice and
> changes nothing, the box goes off and tells you why. Turning it on took one adapter
> change and no edit to the engine, Interactor, Controller or dialog, which is what the
> inward-facing contract was for."

Add an unavailable period (13:00–14:00).

> "Nothing gets scheduled here, and that includes travel. If I say I am busy, I am busy."

### 4. Preview (30s)

Click **Generate Preview**. The proposal appears *below* the unchanged Day Plan.

Point at, in order:

- the metrics line — travel and waiting, before and after;
- the museum, still at 11:00, marked locked;
- one reason on a row — "closes at 17:00" or "a usual mealtime";
- the objectives line, and what is *not* on it.

> "Nothing has changed yet. My Day Plan is still up there, untouched — this is a proposal."

On the improvement cards:

> "Each of these is a before-and-after comparison, not a description of the result. High
> Park was at half past seven in heavy rain and after dark; it earns a daylight card and a
> weather card because moving it lowered the penalty from the *same policy objects the
> search used*. An activity that was already in daylight earns nothing. Nothing that got
> worse is dressed up as an achievement — the trade-offs are under 'Why this schedule?'."

### 4b. Opening hours (25s)

> "Opening hours are a hard constraint — an activity has to sit entirely inside one opening
> interval, and a venue that shuts for lunch gets two intervals, not one long one. Travel is
> allowed outside them, because walking to a museum before it opens is how you get there.
>
> They come from OpenStreetMap's `opening_hours` tag, which Raashid's places adapter reads.
> When a venue publishes them they are obeyed strictly: shut on your date and it cannot be
> scheduled at all, and you are told which one and why.
>
> Most places do not publish them, and those fall back to a general daily window, which is
> still obeyed. Treating no data as 'closed' would refuse to plan almost any real day."

If asked why the screen says nothing about that:

> "It used to. It named every venue with no published hours, which on a real day is most of
> them, so the caution appeared on nearly every schedule. A warning that always appears just
> teaches you to skip warnings, so it went. A venue that is genuinely shut still gets named,
> and that one stops the schedule rather than decorating it."

If asked where the parsing lives:

> "In the places adapter, next to the Overpass call. The syntax is one provider's quirk. The
> Interactor gets normalised windows for the trip's own weekday, and the search below it
> never needs a calendar."

If asked how it fits with Raashid's work:

> "He reads the tag and flattens it into one window per venue, so anything that only knows
> about a single opening and closing always has something valid — and that is what the Trip
> entity enforces. I read the same text a second way, keeping the weekdays and the gaps,
> because that is what you need to actually place a visit. They disagree on purpose: for a
> place open nine-to-five on weekdays and eleven-to-eleven at weekends, his window says nine
> to eleven every day. The scheduler believes the weekday. When my parser cannot read a tag
> it says unknown and his window takes over, so adding the parse can only make a place more
> accurately scheduled, never less schedulable."

### 5. Why these times (15s)

Click **Why these times?**

> "Every explanation comes from the policy that produced it as a code. The presenter turns
> codes into sentences, so none of this wording lives in the scheduling logic."

### 5b. Where the weather came from (15s) — optional

Switch to **Overview** and press **WEATHER PREVIEW**.

> "This popup is Dennis's. It reads the same view model Autoschedule does, which is why the
> hours it lists are exactly the hours the scheduler reasoned about — the forecast turns at
> six, and that is the reason the park moved."

### 6. Cancel, then Apply (20s)

Click **Cancel**.

> "Nothing to undo — it was never applied."

Re-run and click **Apply**. Switch to **Calendar View**.

> "Now it is saved, and the calendar updates through the observer that was already there. I
> did not have to touch the calendar code."

### 7. A day that cannot work (20s)

Pin the museum, add an unavailable period covering 10:30–12:00, generate a preview.

> "It names the museum and the rule that blocked it, and it says the plan was not changed.
> A day that cannot be arranged is an ordinary outcome, not a crash — it comes back as data,
> not an exception."

### 8. Architecture and honesty (40s)

Show the diagram from `docs/autoschedule/architecture.md`.

> "The engine is a pure function — no repository, no network, no Swing — which is why it can
> be tested exhaustively. Travel is fetched before the search and refined afterwards, so the
> recursion never makes a network call. That is checked by a test that counts calls."

One tradeoff, said plainly:

> "Everything soft is capped. Perfect meal timing is worth at most 120 minutes, so it can
> never send you across the city to move lunch. I originally ranked these in strict tiers
> and that was wrong — a trivial gain in a high tier could outrank any amount of travel."

One limitation, said plainly:

> "Travel confidence shows as unknown, because the shared routing service returns a plain
> number and cannot tell me whether it routed or fell back to a distance estimate. I would
> rather say unknown than claim more than I know."

### 9. Testing (20s)

> "450 tests. The one I would point at is a brute-force cross-check: it enumerates every
> possible order on a hundred randomised days and requires the pruned search to return an
> equally good schedule. That is how I know the pruning is not quietly throwing away the
> right answer — and it caught a real bug in my lower bound when I wrote it."

---

## Questions to expect

**"Why not just sort by distance?"** Travel is only one of the constraints. Opening hours,
pins and unavailable periods interact, so a greedy pass produces feasible-but-poor days —
there is a fixture where the search finds 10 travel minutes against greedy's 65.

**"Is this optimal?"** Within the search limit, yes; beyond it the UI says "best found within
the search limit" rather than claiming optimality. At 12 activities the budget is reached.

**"What is your contribution versus your teammates'?"** The ports express what scheduling
needs, and the policy deciding what the numbers mean is mine. Raashid's adapter reaches
OSRM, Transitous and TomTom; Shiyuan's reaches Open-Meteo. My tests run on fakes I wrote.

**"What would you do next?"** An hourly forecast, which activates the weather preference
with no change to the engine, the Interactor or the UI — there is a test asserting exactly
that; and a quality signal on the routing port so travel confidence stops reading as
unknown.

**"Why is weather a checkbox when the others aren't?"** Because the others always work.
Travel, gaps, mealtimes and daylight can be computed for any day from data we already have.
Weather depends on a forecast whose resolution we do not control, so it is the one objective
that can be honestly unavailable — and when it is, the right answer is to say so, not to
apply it silently at zero effect.
