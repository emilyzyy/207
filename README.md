# CloseAI

CloseAI is a dependency-free CSC207 prototype for planning a one-day Toronto trip. It uses a Java 11 backend organized around Clean Architecture and a simple responsive web frontend. All places, weather, travel times, and persistence are mocked so no API keys are required.

## Run it

```bash
mkdir -p out
javac -d out $(find src/main/java -name '*.java')
java -cp out closeai.Main
```

Open [http://localhost:8080](http://localhost:8080). The backend serves both the frontend and the REST API.

To run the lightweight backend tests:

```bash
mkdir -p out
javac -d out $(find src/main/java src/test/java -name '*.java')
java -cp out closeai.TestRunner
```

## Project structure

```text
src/main/java/closeai/
  domain/          Entities, value objects, and enums
  application/     Use cases and repository/service ports
  adapters/        HTTP controller and JSON presenter
  infrastructure/  Mock services, in-memory persistence, and web server
frontend/           Code-native HTML, CSS, and JavaScript UI
docs/design/        Generated visual specifications used for implementation
```

Dependencies point inward: infrastructure and adapters depend on application ports and domain objects; domain code does not know about HTTP, JSON, or storage.

## Main user flow

1. Create or edit a trip with destination, date, hours, and transportation mode.
2. Browse mock Toronto activities on the map-first dashboard.
3. Filter, bookmark, or add activities to the compact Day Plan.
4. Auto Schedule scores bookmarked activities and inserts travel blocks.
5. Open Calendar View from Day Plan to inspect or remove scheduled blocks.
6. Generate a shareable trip summary.

The calendar is deliberately a secondary modal layer. It is never part of the main map/sidebar split.

## Auto-schedule algorithm

`AutoScheduleTripUseCase` filters activities that do not fit the trip window or opening hours, then greedily chooses the best remaining activity using:

```text
score = rating * 2 - travel-time penalty - weather penalty
```

Outdoor activities receive a larger penalty in severe weather. Walking travel times are longest, driving is shortest, and transit sits between them. Travel events are inserted between activity events until no candidate fits.

## REST API

- `POST /api/trips`
- `GET /api/trips/{tripId}`
- `GET /api/activities`
- `POST|DELETE /api/trips/{tripId}/bookmarks/{activityId}`
- `POST /api/trips/{tripId}/plan/manual`
- `POST /api/trips/{tripId}/plan/autoschedule`
- `PUT|DELETE /api/trips/{tripId}/plan/{eventId}`
- `GET /api/trips/{tripId}/summary`
- `GET /api/trips/{tripId}/share`
- `GET /api/trips/{tripId}/weather`

