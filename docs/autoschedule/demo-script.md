# Autoschedule demo script

Roughly three minutes, compressible to two by dropping step 6. Every step below is also
executed as an automated test (`AutoScheduleWalkthroughTest`), so the demo is not relying on
anything assembled specially for it.

**Before starting:** launch with the seeded demo trip. If the live forecast is wanted, run
with `-Dcloseai.weather.mode=open-meteo` — note that the live forecast is still whole-day,
so the weather checkbox stays disabled either way. Do **not** claim traffic-aware driving:
no TomTom key has ever reached a verification run, so no real TomTom route has been
obtained. Driving falls back to OSRM and is not traffic-aware.

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

Point at the greyed-out **Consider weather** box and the sentence under it.

> "This is the interesting one. Weather is a preference rather than a built-in, and it is
> only offered when the forecast can actually tell one hour from another. For this trip it
> cannot — the provider returns one outlook for the whole day, which scores every possible
> time identically. So instead of a checkbox that looks like a choice and changes nothing,
> the box is off and it tells you why. With an hourly forecast it enables and defaults on,
> and you can still turn it off."

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

On the objectives line:

> "Notice weather is not listed. It scored nothing, so claiming it as an applied objective
> would be telling you the day was arranged around something it wasn't. And that decision is
> made by the use case, not the dialog — if I had ticked the box anyway, the Interactor
> would still find the forecast too coarse, contribute zero, and say so in a warning. The
> schedule comes out either way; weather can shift timing but it can never make a day
> unschedulable."

### 5. Why these times (15s)

Click **Why these times?**

> "Every explanation comes from the policy that produced it as a code. The presenter turns
> codes into sentences, so none of this wording lives in the scheduling logic."

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

> "265 tests. The one I would point at is a brute-force cross-check: it enumerates every
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
