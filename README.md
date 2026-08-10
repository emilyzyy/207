# Trippy

Trippy is a Java 11 desktop trip planner built for CSC207. Travellers can create an
itinerary, discover nearby activities, bookmark places, build a Day Plan, check weather,
and automatically improve the order and timing of scheduled activities.

The project follows Clean Architecture. Entities and use cases do not depend on Swing,
HTTP, JSON, Supabase, or other infrastructure details; concrete implementations are wired
together in the outer `app.AppBuilder` composition root.

## Table of contents

- [Features](#features)
- [Requirements](#requirements)
- [Run the application](#run-the-application)
- [Configuration](#configuration)
- [Activity discovery](#activity-discovery)
- [Day Plan and autoschedule](#day-plan-and-autoschedule)
- [George trip assistant](#george-trip-assistant)
- [Architecture](#architecture)
- [Testing and reports](#testing-and-reports)
- [External services and limitations](#external-services-and-limitations)
- [Web prototype](#web-prototype)

## Features

- Create, open, and delete single- or multi-day itineraries.
- Discover activities through OpenStreetMap using Nominatim named-place search and
  Overpass nearby discovery.
- Filter activities by category and indoor/outdoor setting.
- View activities on an interactive OpenStreetMap map and select matching cards and pins.
- Bookmark activities and manually add them to the Day Plan.
- Edit, remove, and drag scheduled activities in 15-minute increments.
- Change a day's date and available hours from the Day Plan **Options** dialog.
- Preview and apply an autoscheduled plan using travel, opening hours, availability,
  mealtimes, daylight, weather, and locked activities.
- Review the plan in Day, Week, and Month calendar views.
- View current and hourly weather from Open-Meteo.
- Ask George, the grounded trip assistant, about the open itinerary.
- Use light or dark mode, category colours and icons, keyboard-accessible controls, and
  confirmation prompts for destructive actions.
- Optionally sign in with Supabase, manage friends, share itineraries, and assign View,
  Edit, or Admin access.

## Requirements

- JDK 11 or newer
- Internet access for live places, maps, weather, and George
- Optional Supabase credentials for accounts and cloud persistence
- Optional TomTom API key for traffic-aware driving estimates

Maven does not need to be installed separately; the repository includes the Maven Wrapper.

## Run the application

From the repository root:

### Windows PowerShell

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd compile exec:java
```

### macOS or Linux

```bash
./mvnw clean test
./mvnw compile exec:java
```

The Maven configuration already points to `app.Main`, so no `-Dexec.mainClass` argument is
required. The application opens on **My Trips**. Without Supabase configuration, trips use
in-memory persistence for the current run.

## Configuration

Trippy reads configuration in this order:

1. Java system properties passed with `-D...`
2. Operating-system environment variables
3. A gitignored `.env` file in the repository root

Create `.env` manually only for the services you want to enable:

```dotenv
# Optional account, friend, sharing, and cloud-persistence support
TRIPPY_SUPABASE_URL=https://your-project.supabase.co
TRIPPY_SUPABASE_ANON_KEY=your-anon-key

# Optional traffic-aware driving routes
TOMTOM_API_KEY=your-tomtom-key

# Optional custom George proxy
TRIPPY_AI_PROXY_URL=https://your-worker.example/v1/responses

# Optional direct OpenAI development mode only
OPENAI_API_KEY=your-project-key
OPENAI_MODEL=gpt-5.4-mini
```

Do not add quotation marks or placeholder asterisks around values. Never commit `.env` or
API keys.

### Supabase setup

1. Create a Supabase project.
2. Run [`docs/supabase/schema.sql`](docs/supabase/schema.sql) in its SQL editor.
3. Add `TRIPPY_SUPABASE_URL` and `TRIPPY_SUPABASE_ANON_KEY` to `.env`.
4. Run Trippy from the repository root so `.env` can be found.

When both credentials are present, Supabase mode is enabled automatically. For a classroom
demo, disable email confirmation under **Authentication > Providers > Email** so newly
created accounts can sign in immediately.

### Runtime modes

The desktop entry point defaults to live Nominatim/Overpass places, OpenStreetMap tiles,
Open-Meteo weather, and the configured George proxy. These modes can be overridden:

| Property | Example value | Purpose |
|---|---|---|
| `trippy.places.mode` | `mock` or `nominatim` | Offline fixtures or live place discovery |
| `trippy.weather.mode` | `mock` or `open-meteo` | Offline fixtures or live weather |
| `trippy.map.tiles.mode` | `offline` or `osm` | Offline map or OpenStreetMap tiles |
| `trippy.persistence.mode` | `memory` or `supabase` | Session-only or cloud persistence |
| `trippy.chatbot.mode` | `offline`, `proxy`, or `openai` | George implementation |
| `trippy.nominatim.endpoint` | URL | Override the named-search endpoint |
| `trippy.overpass.endpoint` | URL | Override the nearby-discovery endpoint |

For example, a deterministic offline run in PowerShell is:

```powershell
.\mvnw.cmd compile exec:java `
  "-Dtrippy.places.mode=mock" `
  "-Dtrippy.weather.mode=mock" `
  "-Dtrippy.map.tiles.mode=offline" `
  "-Dtrippy.persistence.mode=memory" `
  "-Dtrippy.chatbot.mode=offline"
```

## Activity discovery

Activity Discovery separates two operations:

- An empty query discovers nearby activities through Overpass.
- A text query finds named places through Nominatim and merges them with indexed local
  results.

`SearchActivitiesUseCase` coordinates the search through application-facing gateways.
Infrastructure adapters handle remote APIs, mapping, caching, stable ordering, partial
results, rate limits, and service failures. Nodes, ways, and relations use type-aware OSM
identities so unrelated objects with the same numeric ID are not merged.

OpenStreetMap does not provide a standardized review rating, so Trippy does not invent or
display ratings. Category and indoor/outdoor values are inferred from OSM tags; incomplete
source data can therefore produce unknown or imperfect classifications.

## Day Plan and autoschedule

Activities can be added manually from Search or Bookmarks. The add dialog proposes the
earliest available interval and also shows the existing plan for drag-and-drop placement.
The Day Plan supports editing, removal, locking, and 15-minute drag rescheduling while
preventing overlaps and keeping events inside the day's available hours.

Autoschedule operates only on activities already in the Day Plan. It produces a preview
before changing saved data and observes these hard constraints:

- day boundaries and user-unavailable periods;
- no overlapping activities or travel;
- locked activities remain at their exact times;
- parsed venue opening hours when available; and
- feasible travel between activities.

It then compares valid schedules using travel time, avoidable waiting, mealtime placement,
daylight, weather when usable, and the user's preference to preserve order. **Apply** saves
the preview; **Cancel** leaves the original plan unchanged.

More detail and the complete autoschedule diagram are in
[`docs/autoschedule/architecture.md`](docs/autoschedule/architecture.md) and
[`docs/autoschedule/diagrams/`](docs/autoschedule/diagrams/).

## George trip assistant

Select George's circular avatar after opening a trip. George receives grounded context from
the current itinerary: destination, date, hours, activities, bookmarks, Day Plan, travel
mode, and weather. The default proxy keeps the OpenAI key outside the desktop application.
If the live assistant is unavailable, Trippy falls back to deterministic offline guidance.

The Cloudflare Worker source is in [`george-proxy`](george-proxy). Direct OpenAI mode is
intended only for local development and must be explicitly enabled with
`-Dtrippy.chatbot.mode=openai`.

## Architecture

```text
entity
  Trip, Activity, ScheduledEvent, WeatherWarning, value objects
      ^
use_case
  interactors, input/output boundaries, repository and service interfaces
      ^
interface_adapter
  controllers, presenters, view models, API and persistence adapters
      ^
views / app / database
  Swing UI, composition root, concrete external services
```

Representative flows include:

```text
SearchPanel
  -> ActivityDiscoveryController
  -> SearchActivitiesUseCase
  -> ActivitySearchGateway
  -> ActivityDiscoveryPresenter
  -> SearchViewModel
```

```text
DayPlanPanel
  -> AutoScheduleController
  -> AutoScheduleInteractor
  -> trip, routing, weather, and scheduling boundaries
  -> AutoSchedulePresenter
  -> DayPlanViewModel
```

The dependency rule points inward: use cases depend on abstractions, while HTTP clients,
JSON mapping, Swing, Supabase, and API-specific behavior remain outside the core.

## Testing and reports

The normal test suite is deterministic and does not require public internet services.
External API behavior is tested with fakes and loopback HTTP servers; explicitly named live
smoke tests are skipped by default.

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd checkstyle:check
```

Generated reports:

- JaCoCo coverage: `target/site/jacoco/index.html`
- Checkstyle: `target/checkstyle-result.xml`
- Surefire test results: `target/surefire-reports/`

## External services and limitations

- Nominatim performs geocoding and named-place search.
- Overpass discovers nearby OpenStreetMap activities.
- OpenStreetMap supplies map tiles.
- Open-Meteo supplies geocoding and hourly weather.
- OSRM supplies key-free route estimates; TomTom can provide traffic-aware driving when a
  key is configured.
- Supabase supplies optional authentication, friends, sharing, and persistence.
- Public services can time out, reject traffic, or rate-limit requests. Trippy reports this
  separately from a genuine zero-result search and may show cached partial results.
- Search results depend on the quality and completeness of OpenStreetMap data.
- Venue hours are only as reliable as their OSM `opening_hours` tags.
- The domain uses local dates and times and does not yet model destination time zones.
- Trips are planned one day at a time; a multi-day itinerary contains separate Day Plans.
- There is no hotel/home origin, so routing starts from the destination centre or the prior
  scheduled activity.

## Web prototype

A retained secondary web prototype can be started with:

```powershell
.\mvnw.cmd compile exec:java "-Dexec.args=--web"
```

Then open [http://localhost:8080](http://localhost:8080). The Swing application is the main
and actively developed interface.
