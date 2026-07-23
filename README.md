# CloseAI

CloseAI is a Java 11 CSC207 prototype for planning a one-day trip. The backend follows Clean Architecture: domain and application code have no HTTP, JSON, Swing, or infrastructure dependencies, while concrete services are assembled at the outer `AppBuilder` composition root.

The checked-in frontend is a retained web prototype, not the course's final GUI. The required Java Swing `JFrame`, panels, dialogs, and Swing feature integration are still team work and are explicitly listed in [`docs/TEAM_HANDOFF.md`](docs/TEAM_HANDOFF.md). This repository does not claim that the Swing requirement is complete.

## Build and test

Requirement: JDK 11. The checked-in Maven Wrapper downloads the pinned Maven runtime.

```bash
./mvnw clean test
```

The normal suite is deterministic and does not call the public internet. It uses JUnit 5 fakes for `TripRepository`, `DistanceService`, and `WeatherService`, plus a loopback HTTP server for Open-Meteo response/error tests.

Run the app in offline mode (the default):

```bash
./mvnw compile exec:java -Dexec.mainClass=closeai.Main
```

Select real Open-Meteo weather at runtime:

```bash
./mvnw compile exec:java -Dexec.mainClass=closeai.Main -Dcloseai.weather.mode=open-meteo
```

Open [http://localhost:8080](http://localhost:8080). No API key or secret is used.

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
- `EditItineraryInteractor` depends on `ItineraryDataAccessInterface`, not concrete persistence.
- `ActivityScoringPolicy` is injectable; `DefaultActivityScoringPolicy` owns the default rule.
- `OpenMeteoWeatherService` implements the existing `WeatherService` port. Its API DTOs and Jackson mapping remain in `infrastructure.weather`.
- `InMemoryItineraryDataAccessObject` implements both `ItineraryDataAccessInterface` and `TripRepository` so create and edit share one in-memory store.
- `application.AppContainer` receives abstractions and constructs use cases; it does not instantiate infrastructure.
- `MockWeatherService` remains the default for offline development and deterministic tests.

## Edit Itinerary

After a trip/itinerary exists, `EditItineraryInteractor` updates its destination, date, trip window, and transportation mode through `ItineraryDataAccessInterface`.

- Input is carried by immutable `EditItineraryInputData`; callers depend on `EditItineraryInputBoundary`.
- Changes that would push scheduled events outside the new trip window are rejected before save.
- `PUT /api/trips/{tripId}` and the Options tab “Save trip options” action use this interactor so an existing itinerary is updated in place instead of replaced by a new trip.

## Auto Schedule

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
- Open-Meteo success mapping, nearest-hour selection, non-2xx, empty results, malformed/misaligned JSON, and connection failure
- separate opt-in live Open-Meteo request

## Known limitations

- The current `Trip` model has a destination but no separate hotel/home origin. The geocoded destination centre is therefore the initial scheduling location.
- The model uses same-day `LocalTime`; overnight trips and overnight opening hours are not supported.
- Greedy scoring is deterministic but does not guarantee a globally optimal itinerary.
- Places, distance estimates, and persistence are still mock/in-memory implementations.

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

Shiyuan (Dennis) Lyu: Auto Schedule use case, scoring policy, schedule invariants, weather weighting, Open-Meteo adapter, Maven/JUnit 5 configuration, related tests, and documentation.

Bianca: Edit Itinerary interactor (`EditItineraryInteractor`), `ItineraryDataAccessInterface`, `InMemoryItineraryDataAccessObject`, Options/API wiring for in-place itinerary updates, and related unit test.
