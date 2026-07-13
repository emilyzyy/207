# CloseAI Team Handoff

This document separates the shared project foundation and Shiyuan's completed scope from the CSC207 work that remains owned by other team members.

## 1. Completed shared foundation

- Java 11 compilation is configured in `pom.xml`.
- Maven Wrapper scripts pin Maven 3.9.16, so contributors can run `./mvnw` without a system Maven installation.
- The Clean Architecture foundation provides domain entities, application use cases, and repository/service ports.
- `closeai.AppBuilder` is the outer composition root; `application.AppContainer` receives abstractions and does not instantiate HTTP, JSON, or persistence adapters.
- JUnit 5 and Maven Surefire provide the test foundation.
- `.github/workflows/java-ci.yml` runs the ordinary test suite with Java 11 for pushes and pull requests to `main`.
- Offline mode uses `MockWeatherService` by default. `-Dcloseai.weather.mode=open-meteo` selects the real `WeatherService` adapter.
- `.gitignore` excludes `target/`, `out/`, and `.DS_Store`.

## 2. Completed by Shiyuan (Dennis) Lyu

- `AutoScheduleTripUseCase` with first-leg and inter-activity transportation time.
- Injectable `ActivityScoringPolicy` and the documented rating/travel/weather/exposure formula.
- Walking, driving, and transit mode propagation through `DistanceService`.
- Waiting when an activity has not opened yet.
- Opening/closing time and trip-window validation.
- Sorted, non-overlapping travel and activity events with deterministic tie-breaking and event IDs.
- Failure atomicity: scheduling builds and validates a separate aggregate before repository save.
- Weather severity weighting for indoor, mixed, and outdoor activities.
- Open-Meteo Geocoding and Forecast integration through the existing `WeatherService` port.
- Java `HttpClient` timeouts and handling for non-2xx, empty, network, interruption, and malformed/misaligned JSON responses.
- Fake-based scheduler tests, offline adapter tests, an explicit live smoke test, and related architecture/algorithm documentation.

## 3. Work remaining for other members

The following items are intentionally not implemented by this branch:

- Java Swing `JFrame` and the main application window.
- Swing Search, Bookmarks, Day Plan, and Options panels.
- Swing `CalendarDialog`.
- UI integration for Search, Open Now, and place filtering.
- Manual Edit validation and event-conflict handling.
- Share and Calendar PNG export.
- Map or place visualization in the final Swing experience.
- JUnit tests for each member's remaining modules.
- Each member's own Git branch, commits, and pull request.

The existing web frontend may remain as a prototype/reference, but it does not satisfy the final Swing GUI requirement.

## 4. CSC207 requirement status

| Requirement | Status |
| --- | --- |
| Java implementation | Complete for the checked-in foundation |
| Clean Architecture foundation | Complete |
| External API | Complete: Open-Meteo Geocoding + Forecast |
| Auto Schedule | Complete for Shiyuan's scope |
| Java Swing GUI | **Not complete; required from subsequent team work** |
| Team Git contribution | Every member must contribute independently |

This branch provides a complete shared foundation and Shiyuan-owned module, not a claim that the whole team project is finished.

## 5. Starting development

Clone the team repository and branch from the latest `main`:

```bash
git clone https://github.com/emilyzyy/207.git
cd 207
git switch main
git pull --ff-only
git switch -c feature/<member>-<scope>
```

Run the normal offline suite:

```bash
./mvnw clean test
```

Run the application with mock weather (default):

```bash
./mvnw compile exec:java -Dexec.mainClass=closeai.Main
```

Run with the real Open-Meteo adapter:

```bash
./mvnw compile exec:java -Dexec.mainClass=closeai.Main \
  -Dcloseai.weather.mode=open-meteo
```

Run the opt-in live weather smoke test separately:

```bash
RUN_LIVE_OPEN_METEO_TEST=true \
  ./mvnw -Dtest=OpenMeteoWeatherServiceLiveTest test
```

Before opening a pull request:

1. Rebase or merge the latest `main` without rewriting shared history.
2. Run the relevant tests and `git diff --check`.
3. Stage only files owned by the branch; do not commit build output, secrets, IDE files, or another member's work.
4. Push the feature branch and open a pull request to `main`.
5. Describe scope, tests, architecture impact, and remaining work.
6. Wait for CI and review. Add a normal follow-up commit for fixes; do not force push unless the team explicitly agrees.
7. Do not merge until the designated reviewer approves.
