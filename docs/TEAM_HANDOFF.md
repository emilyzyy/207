# CloseAI Team Handoff

This document records the integrated foundation, completed Shiyuan scope, and remaining team-owned work.

## 1. Completed shared foundation

- Java 11, Maven Wrapper 3.9.16, JUnit 5, Surefire, and Java CI are configured.
- Domain and application code remain independent of HTTP, JSON, Swing, and concrete infrastructure.
- `AppBuilder` is the composition root and `AppContainer` receives abstractions.
- `InMemoryItineraryDataAccessObject` is the one runtime store implementing both `ItineraryDataAccessInterface` and `TripRepository`.
- Mock weather, mock places, and a marker-only offline map are the defaults.
- Open-Meteo, Nominatim/Overpass, and OpenStreetMap tiles are explicit runtime opt-ins.

## 2. Completed by Shiyuan (Dennis) Lyu

- `CreateTripUseCase` implements `CreateTripInputBoundary` and consumes immutable `CreateTripInputData`.
- Create Trip validates every required field and the trip window before repository save.
- Direct Create Trip tests cover success, persistence, missing fields, blank destination, and invalid time windows.
- `TripSetupController` and `TripSetupPresenter` wire Create Trip and Edit Itinerary to the editable Swing Trip Setup form.
- Dashboard, Trip Options, Bookmarks, Day Plan, Calendar, and Optimize share the created trip ID; no demo trip is seeded.
- Offline services remain the default and destination network enrichment runs outside the Swing EDT.
- Raashid's map/place branch is integrated with offline tile control, cached-place composition, Nominatim/Overpass error handling, and adapter/ViewModel tests.
- Auto Schedule, scoring policy, schedule validation, weather weighting, Open-Meteo integration, and their tests remain in place.

## 3. Integrated Swing status

The Java Swing application includes:

- `CloseAIFrame`, header, overview/weather, interactive marker map, and four planner tabs;
- Search and Bookmarks display panels;
- editable Trip Setup with create and save behavior;
- Day Plan, Calendar, and Optimize Current Itinerary;
- shared ViewModel updates and EDT launch through `Main`.

The retained web frontend is a secondary prototype and is launched with `Main --web`.

## 4. Remaining team work

- Wire Search text/category actions and saved-activity actions to their use cases.
- Wire add-to-plan, edit-event, remove-event, Share, and Calendar export controls.
- Decide whether live public place/weather modes should be demonstrated during grading; offline mode requires no network.
- Add direct unit-test classes for remaining thin use cases listed in the Swing milestone report.
- Complete the human pair walkthroughs and record actual Alex/Raashid/Emily/Bianca confirmations. Implementation status must not be treated as another person's sign-off.

## 5. Verification and contribution workflow

Run the deterministic suite:

```bash
./mvnw clean test
```

Run Swing offline:

```bash
./mvnw compile exec:java -Dexec.mainClass=closeai.Main
```

Opt into live weather, live places, and online map tiles only when required:

```bash
./mvnw compile exec:java -Dexec.mainClass=closeai.Main \
  -Dcloseai.weather.mode=open-meteo \
  -Dcloseai.places.mode=nominatim \
  -Dcloseai.map.tiles.mode=osm
```

Before merging:

1. update from `main` without rewriting shared history;
2. run `./mvnw clean test` and `git diff --check`;
3. commit only the intended scope;
4. open a pull request describing architecture, behavior, tests, and remaining pair confirmations;
5. merge only after the designated human reviewers approve.
