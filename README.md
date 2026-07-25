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
- Create Trip validation, persistence, controller parsing, presenter state propagation, and the create/edit/optimize Swing path
- Nominatim/Overpass success mapping, empty results, non-2xx, malformed JSON, caching, and map ViewModel updates
- Open-Meteo success mapping, nearest-hour selection, non-2xx, empty results, malformed/misaligned JSON, and connection failure
- separate opt-in live Open-Meteo request

## Known limitations

- The current `Trip` model has a destination but no separate hotel/home origin. The geocoded destination centre is therefore the initial scheduling location.
- The model uses same-day `LocalTime`; overnight trips and overnight opening hours are not supported.
- Greedy scoring is deterministic but does not guarantee a globally optimal itinerary.
- Distance estimates and persistence remain mock/in-memory implementations.
- Live place discovery is optional and uses public Nominatim/Overpass services; offline mode remains the supported default.

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

Shiyuan (Dennis) Lyu: Create Trip input boundary/interactor validation and tests; Trip Setup create/edit Swing workflow; active-trip composition; offline-by-default service selection; reviewed integration and tests for Raashid's map/place branch; Auto Schedule, scoring policy, schedule invariants, weather weighting, Open-Meteo adapter, Maven/JUnit 5 configuration, and documentation.

Bianca: Edit Itinerary interactor (`EditItineraryInteractor`), `ItineraryDataAccessInterface`, `InMemoryItineraryDataAccessObject`, Options/API wiring for in-place itinerary updates, and related unit test.

Raashid: interactive Swing map, Nominatim/Overpass place discovery, cached-place repository, and web map integration.
