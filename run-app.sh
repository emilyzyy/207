#!/bin/sh
# Launches Trippy with live places (Nominatim/Overpass) and live weather (Open-Meteo).
# Exists because the main class and the -D property names have both been renamed by
# repackaging, so a remembered command line silently starts the app on mock data instead
# of failing loudly: places.mode defaults to "mock", which returns 7 stub places.
set -e
cd "$(dirname "$0")"
exec ./mvnw compile exec:java -Dexec.mainClass=app.Main \
  -Dtrippy.places.mode=nominatim \
  -Dtrippy.weather.mode=open-meteo
