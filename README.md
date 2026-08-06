# CloseAI

CloseAI is a Java 11 CSC207 prototype for planning a one-day trip. The backend follows Clean Architecture: domain and application code have no HTTP, JSON, Swing, or infrastructure dependencies, while concrete services are assembled at the outer `AppBuilder` composition root.

The default application is a Java Swing dashboard. It starts without a seeded trip: the Trip Setup tab creates the active trip, and the same form edits that trip later. The retained web frontend is available as a secondary prototype through `Main --web`.

## Build and test

Requirement: JDK 11. The checked-in Maven Wrapper downloads the pinned Maven runtime.

```bash
./mvnw clean test
```

The normal suite is deterministic and does not call the public internet. It uses JUnit 5 fakes plus loopback-only HTTP servers for Open-Meteo and Nominatim/Overpass response and error tests.

Run the app in offline mode (the default):

```bash
./mvnw compile exec:java -Dexec.mainClass=closeai.Main
```

Select real Open-Meteo weather at runtime:

```bash
./mvnw compile exec:java -Dexec.mainClass=closeai.Main -Dcloseai.weather.mode=open-meteo
```

Real places and OpenStreetMap tiles are separate, explicit opt-ins:

```bash
./mvnw compile exec:java -Dexec.mainClass=closeai.Main \
  -Dcloseai.places.mode=nominatim \
  -Dcloseai.map.tiles.mode=osm
```

The places and weather refresh runs in a `SwingWorker`, not on the Swing event-dispatch thread. If either service fails, the created trip remains valid and the UI retains its cached mock places.

Run the web prototype and open [http://localhost:8080](http://localhost:8080):

```bash
./mvnw compile exec:java -Dexec.mainClass=closeai.Main -Dexec.args=--web
```

No API key or secret is used.

## Architecture

```text
domain
  Trip, Activity, ScheduledEvent, WeatherWarning, value objects
        ↑
application
  use cases, ports, ActivityScoringPolicy
        ↑
adapters / infrastructure
  HTTP controller, persistence, mocks, Open-Meteo HTTP + DTO/JSON mapping
        ↑
closeai.AppBuilder / Main
  concrete dependency assembly and live/offline selection
```

- `AutoScheduleTripUseCase` depends only on application ports and domain objects.
- `CreateTripUseCase` implements `CreateTripInputBoundary`, validates immutable `CreateTripInputData`, and saves through `TripRepository`.
- `EditItineraryInteractor` depends on `ItineraryDataAccessInterface`, not concrete persistence.
- `ActivityScoringPolicy` is injectable; `DefaultActivityScoringPolicy` owns the default rule.
- `OpenMeteoWeatherService` implements the existing `WeatherService` port. Its API DTOs and Jackson mapping remain in `infrastructure.weather`.
- `InMemoryItineraryDataAccessObject` implements both `ItineraryDataAccessInterface` and `TripRepository` so create and edit share one in-memory store.
- `application.AppContainer` receives abstractions and constructs use cases; it does not instantiate infrastructure.
- `MockWeatherService` remains the default for offline development and deterministic tests.
- `MockPlacesService` and the offline map remain the defaults. `NominatimPlacesService`, Overpass, and OpenStreetMap tiles require explicit runtime modes.

## Create Trip and Trip Setup

- Swing starts with no active trip and disables Optimize until creation succeeds.
- `TripSetupController` chooses Create Trip when there is no active trip and Edit Itinerary afterward.
- `TripSetupPresenter` updates Dashboard, Trip Options, Bookmarks, and the Day Plan with the same saved trip ID.
- Destination weather and place refresh happens asynchronously; failure never rolls back a successfully created trip.

## Edit Itinerary

After a trip/itinerary exists, `EditItineraryInteractor` updates its destination, date, trip window, and transportation mode through `ItineraryDataAccessInterface`.

- Input is carried by immutable `EditItineraryInputData`; callers depend on `EditItineraryInputBoundary`.
- Changes that would push scheduled events outside the new trip window are rejected before save.
- `PUT /api/trips/{tripId}` and the Options tab “Save trip options” action use this interactor so an existing itinerary is updated in place instead of replaced by a new trip.

## Share Trip

The header Share action is enabled only after an active trip exists. It runs through
`ShareTripController`, the `ShareTripInputBoundary`, and `ShareTripPresenter` before opening a
modeless preview. The user can copy a portable itinerary containing the destination, date, trip
window, transportation mode, and scheduled events to the system clipboard. Validation failures
are presented in the dialog instead of leaking Swing or clipboard classes into the application
layer. The existing `GET /api/trips/{tripId}/share` endpoint uses the same share use case.

## Interactive Calendar

The Calendar View is backed by a dedicated `CalendarViewModel` that observes the same immutable
Dashboard and Day Plan states used by the rest of Swing. It does not create a second trip or
schedule source.

- Day, Week, and Month views can be selected at runtime.
- Previous/next navigation advances by the selected time scale.
- Today and Trip date actions provide predictable navigation anchors.
- Month and Week dates are clickable, with the active trip and scheduled-item count highlighted.
- Trip edits and schedule changes immediately update the open calendar.

Because the current domain aggregate represents a one-day trip, events appear on the trip date;
the expanded calendar provides surrounding week/month context without pretending that events
have dates the domain model does not store.

## Auto Schedule (bookmark selection)

> This section describes the bookmark-selection scheduler, which chooses activities from
> the bookmark list. It is distinct from **Autoschedule (Day Plan)** below, which reorders
> and retimes activities the traveller has already added to the Day Plan.

For every scheduling step, each remaining feasible activity is scored using:

```text
score = 2.0 × rating − 0.05 × travelMinutes − severityPenalty × exposure

severityPenalty: LOW = 0.4, MEDIUM = 2.0, HIGH = 4.0
exposure:        INDOOR = 0.0, MIXED = 0.5, OUTDOOR = 1.0
```

The scheduler then:

1. Uses the destination coordinates resolved by `WeatherService` as the trip's initial location; there is no Toronto coordinate in the use case.
2. Calculates travel for the first activity and every later activity using the selected transportation mode.
3. Allows arrival before opening time by leaving a waiting gap, then starts at opening time.
4. Rejects candidates whose travel/activity interval crosses the trip window or whose activity crosses its opening/closing time.
5. Chooses the highest-scoring feasible candidate; equal scores use activity ID as a stable tie-break.
6. Inserts a travel event when travel time is positive and generates deterministic event IDs from the trip, sequence, type, activity, and times.
7. Validates that all events are sorted, inside the trip window, and non-overlapping before saving a separate scheduled trip copy.

An empty bookmark list raises a clear `IllegalArgumentException`. If none of the bookmarks is feasible, scheduling raises `IllegalStateException` and preserves the previous schedule. If at least one activity fits, the legal greedy subset is saved and infeasible bookmarks remain bookmarked. Any weather, distance, scoring, or validation failure occurs before the repository receives the new aggregate, so no partial schedule is left behind.

## Autoschedule (Day Plan)

Autoschedule rearranges the activities already in the Day Plan. It never adds an activity,
never drops one, and never changes anything until the traveller chooses **Apply**.

### Running it

Open the **Day Plan** tab, then choose **Autoschedule**. The settings dialog asks only for
what cannot be worked out automatically:

- **Available from / until** - prefilled from the trip's own hours. These may narrow the
  day but not widen it, because the `Trip` entity refuses to hold events outside its stored
  window.
- **Getting around by** - walking, driving or transit, prefilled from the trip.
- **Times I am not available** - optional. Nothing is scheduled in these, *including
  travel*: the traveller waits until the period ends before setting out.
- **Keep my current order where possible** - on by default.
- **Consider weather** - offered only when the forecast can tell one time of day from
  another. When it can, the box is enabled and ticked by default and may be unticked. When
  the forecast covers the whole day, is beyond the provider's hourly range, or cannot be
  obtained, the box is disabled and unticked and the reason is shown in words beneath it
  ("Hourly weather is not available for this trip date."). The state is never signalled by
  colour alone, and the checkbox stays keyboard-reachable.

Individual activities can be pinned with the **Lock** checkbox on their row. A pinned
activity keeps its exact time and everything else is arranged around it. Pins last as long
as the application is open and are never written to the trip.

### Preview and Apply

Generating a preview shows the proposal underneath the unchanged Day Plan, with before and
after figures for travel and waiting, how many activities moved, one short reason on the
rows that have one, and a keyboard-accessible **Why these times?** panel listing every
explanation. **Apply** saves it; **Cancel** discards it. If the Day Plan changed after the
preview was produced, Apply is refused rather than overwriting the newer version.

### What it optimises for

These are built in and always applied - they are what the feature is for, not options:

| Objective | Behaviour |
|---|---|
| Travel | Minimise total travel time for the chosen mode |
| Wasted waiting | Reduce idle time that is not caused by opening hours or an unavailable period |
| Mealtimes | Prefer customary lunch/dinner windows for `FOOD` activities |
| Daylight | Prefer daylight for `OUTDOOR` activities |

Weather is the one soft objective the traveller decides about, because it is the one that
cannot always be honoured: a whole-day forecast scores every candidate time alike and so
has nothing to say about *when*. When selected and usable it prefers better conditions for
exposed activities. It remains soft and capped like the rest — it can shift timing, but it
can never make a day unschedulable and never overrides a hard rule. If the forecast turns
out to be coarse or unavailable after all, it contributes zero, the preview says so, and
the schedule is still produced.

Valid schedules are ranked by a single practical cost in minutes: travel + avoidable idle +
capped meal/daylight/weather penalties, plus a small capped charge for disturbing the
traveller's order when they asked to keep it. Every soft penalty is capped, which is what
guarantees a minor improvement in one of them can never justify a large detour. Hard rules
- opening hours, availability, unavailable periods, pins, travel feasibility, no overlaps -
are never traded against anything.

### Configuration and secrets

No configuration is required; the defaults work offline.

| Variable / property | Effect |
|---|---|
| `TOMTOM_API_KEY` or `-Dtomtom.api.key=...` | Enables traffic-aware driving via TomTom. Without it, driving uses OSRM and is not traffic-aware. |
| `-Dcloseai.weather.mode=open-meteo` | Live forecast instead of the mock. |
| `-Dcloseai.places.mode=nominatim` | Live place discovery instead of the mock. |

**No API key is ever committed.** Keys are read from the environment or a system property.
`origin/main` also added a `.env` fallback, so `.env` and `.env.*` are in `.gitignore`;
never commit one. Keys are never logged or printed, and no authenticated URL is written to
output.

### Current limitations

- **The weather preference cannot be offered yet.** Both shipped forecast adapters report
  one severity for the whole trip, so `canDistinguishTimes()` is false and the "Consider
  weather" checkbox is disabled with the reason shown. This is the designed behaviour
  rather than a defect, but it does mean the enabled state is not reachable in production
  today. An hourly forecast would activate it with no change to the engine, the Interactor
  or the UI — verified by test, not assumed.
- **Travel confidence is reported as unknown.** The shared `DistanceService` returns a plain
  number and cannot distinguish a real route from its own distance-based fallback, so the
  preview says travel times may include estimates instead of claiming more.
- **Transit departure times use the JVM's default time zone**, which is correct for a local
  trip and wrong for a trip in another zone. `Trip` has no time-zone field.
- **Driving is not live-verified.** The TomTom request was corrected to send
  `latitude,longitude` and is unit-tested against a stubbed HTTP client, including a
  regression test that fails against the old order. No key has been available in any
  verification run, so no real TomTom route has ever been obtained and **no traffic-aware
  claim should be made**. `TomTomLiveVerificationTest` will settle it in one command once a
  credential is present — it calls the fallback-free TomTom path directly, so it cannot be
  satisfied by an OSRM fallback. Walking (OSRM) and transit (Transitous) are live-verified.
- Single day only, one transportation mode per run, and travel between activities only -
  there is no hotel or origin leg because `Trip` has no origin coordinate.

### Architecture

```text
DayPlanPanel  ->  AutoScheduleController  ->  AutoScheduleInputBoundary
                                                      |
                                          AutoScheduleInteractor
                                          |     |        |        |
                                    TripRepo  Travel  Weather  ScheduleEngine
                                              gateway gateway   (+ policies)
                                                      |
                                          AutoScheduleOutputBoundary
                                                      |
                        AutoSchedulePresenter -> DayPlanViewModel -> Day Plan + Calendar
```

The engine is a pure function: no repository, no network, no Swing. Travel estimates are
fetched before the search and refined afterwards, so the recursion never makes a network
call. Full diagram and data flow: [`docs/autoschedule/architecture.md`](docs/autoschedule/architecture.md).

### How add-to-plan connects later

Autoschedule reads `Trip.getScheduledEvents()` and writes through `Trip.copyWithSchedule`.
It does not care how activities arrived.

**This is now wired end to end.** Alex's discovery, bookmark and manual add/edit/remove
workflow (`ActivityDiscoveryController`, `BookmarkController`, `ManualPlanController`) writes
through the same `TripRepository`. An activity found in Search can be added to the Day Plan
and then autoscheduled, with no seeded demo trip involved and **no Autoschedule code change**
— the two use cases meet only at the Trip.

`AddToPlanAutoscheduleIntegrationTest` runs that whole path through
`AppBuilder.buildOffline()` and the real controllers, and one of its tests reads the
Autoschedule package and fails if any file there names an add-to-plan or discovery class.

### Commands

```bash
./mvnw clean test                 # all tests, plus the JaCoCo report
./mvnw checkstyle:check           # style report -> target/checkstyle-result.xml
open target/site/jacoco/index.html  # coverage report

# opt-in live checks (network required)
RUN_LIVE_AUTOSCHEDULE_TEST=true ./mvnw test -Dtest=AutoScheduleLiveVerificationTest

# conclusive live TomTom driving check (also needs a credential in the environment)
RUN_LIVE_TOMTOM_TEST=true ./mvnw test -Dtest=TomTomLiveVerificationTest
```

The class diagram for the full use case is in
[`docs/autoschedule/diagrams/`](docs/autoschedule/diagrams/) (PlantUML source plus rendered
SVG and PNG).

## Open-Meteo adapter

`OpenMeteoWeatherService` performs two key-free requests:

1. `https://geocoding-api.open-meteo.com/v1/search` resolves `Trip.destination` to latitude/longitude.
2. `https://api.open-meteo.com/v1/forecast` requests local hourly `weather_code`, `temperature_2m`, `precipitation_probability`, and `wind_speed_10m` for the trip date.

It uses Java `HttpClient` with a 5-second connect timeout and an 8-second request timeout. It converts WMO weather codes, precipitation probability, and wind speed into `LOW`, `MEDIUM`, or `HIGH` severity. Non-2xx responses, no geocoding result, missing/misaligned hourly data, malformed JSON, interruption, timeout, and network failure become `WeatherServiceException`; interrupted threads retain their interrupt flag.

The public forecast API normally covers only a limited future horizon. Trips outside the provider's supported range will produce a handled service error rather than mock data.

### Explicit live smoke test

The live test is opt-in and is skipped by ordinary `./mvnw clean test`:

```bash
RUN_LIVE_OPEN_METEO_TEST=true ./mvnw -Dtest=OpenMeteoWeatherServiceLiveTest test
```

This makes a real geocoding request for Toronto and a real forecast request for tomorrow.

## Test coverage

- empty bookmarks and no feasible activity
- first-leg travel and walking/driving/transit timing
- waiting for opening time
- trip window and opening/closing constraints
- severe-weather outdoor penalty and injectable scoring
- event ordering, non-overlap, deterministic output, and failure atomicity
- edit itinerary options update and persistence through `InMemoryItineraryDataAccessObject`
- Create Trip validation, persistence, controller parsing, presenter state propagation, and the create/edit/optimize Swing path
- Nominatim/Overpass success mapping, empty results, non-2xx, malformed JSON, caching, and map ViewModel updates
- Open-Meteo success mapping, nearest-hour selection, non-2xx, empty results, malformed/misaligned JSON, and connection failure
- separate opt-in live Open-Meteo request
- Share Trip input validation, summary formatting, controller/output behavior, and copyable state
- Calendar trip/schedule synchronization, Day/Week/Month navigation, date selection, and Swing controls
- Autoschedule: hard constraints, pins, unavailable periods blocking activities and travel, the built-in objectives and their caps, preview side-effect freedom, stale-Apply protection, exact-time travel refinement, presenter wording, Calendar compatibility, background execution, and a brute-force cross-check that the search's pruning never changes the answer

Coverage and style are measured, not asserted:

```bash
./mvnw clean test        # JaCoCo report at target/site/jacoco/index.html
./mvnw checkstyle:check  # report at target/checkstyle-result.xml
```

Both are configured as reports rather than build gates, so a threshold or a legacy style
violation cannot block a teammate's commit.

**Checkstyle uses the official CSC207 configuration.** `config/mystyle.xml` is byte-identical
to the file distributed with the course starter code and named in the regex lecture. The
course ships it for the IntelliJ Checkstyle plugin rather than as a build gate — its own
starter code produces 62 warnings under it — so this project runs it through Maven for
reproducible evidence with `failOnViolation=false`. `pom.xml` pins Checkstyle 10.21.4
because the plugin's bundled version predates two modules the course file uses.

**JaCoCo is 0.8.13 or newer.** 0.8.12 cannot instrument Java 24 class files (major version
68) and floods the build with `IllegalClassFormatException`. Only JDK classes were affected,
so coverage numbers were never wrong, but the noise made the output unreadable.

## Known limitations

- The current `Trip` model has a destination but no separate hotel/home origin. The geocoded destination centre is therefore the initial scheduling location.
- The model uses same-day `LocalTime`; overnight trips and overnight opening hours are not supported.
- Greedy scoring is deterministic but does not guarantee a globally optimal itinerary.
- Distance estimates and persistence remain mock/in-memory implementations.
- Live place discovery is optional and uses public Nominatim/Overpass services; offline mode remains the supported default.
- Autoschedule's own limitations are listed under [Autoschedule (Day Plan)](#autoschedule-day-plan): day-wide weather, unknown travel provenance, JVM-default transit time zone, and driving not yet live-verified.

## REST API

- `POST /api/trips`
- `GET /api/trips/{tripId}`
- `PUT /api/trips/{tripId}` — edit itinerary options (destination, date, window, transportation)
- `GET /api/activities`
- `POST|DELETE /api/trips/{tripId}/bookmarks/{activityId}`
- `POST /api/trips/{tripId}/plan/manual`
- `POST /api/trips/{tripId}/plan/autoschedule`
- `PUT|DELETE /api/trips/{tripId}/plan/{eventId}`
- `GET /api/trips/{tripId}/summary`
- `GET /api/trips/{tripId}/share`
- `GET /api/trips/{tripId}/weather`

## Contribution

Shiyuan (Dennis) Lyu: Share Trip Clean Architecture flow and clipboard-ready Swing preview; interactive Day/Week/Month calendar expansion and synchronized navigation; related unit, Swing structure, and application integration tests; Create Trip input boundary/interactor validation and tests; Trip Setup create/edit Swing workflow; active-trip composition; offline-by-default service selection; reviewed integration and tests for Raashid's map/place branch; Auto Schedule, scoring policy, schedule invariants, weather weighting, Open-Meteo adapter, Maven/JUnit 5 configuration, and documentation.

Bianca: Edit Itinerary interactor (`EditItineraryInteractor`), `ItineraryDataAccessInterface`, `InMemoryItineraryDataAccessObject`, Options/API wiring for in-place itinerary updates, and related unit test.

Raashid: interactive Swing map, Nominatim/Overpass place discovery, cached-place repository, and web map integration.
