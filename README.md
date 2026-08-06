# GATHRA

GATHRA is a native Indonesian Android pilot for route preview and foreground
turn-by-turn navigation. The app uses MapLibre and calls one provider-neutral
NestJS API for routing, place search, reverse geocoding, health, and flood
hazard snapshots.

The deployed API is [https://api.gathra.my.id/](https://api.gathra.my.id/).
GraphHopper and Photon are private provider services behind that API; Android
never connects to either provider directly.

## Capabilities

- CAR and MOTORCYCLE route previews with one optional alternative.
- Map-selected origin and destination coordinates, with reverse geocoding used
  only to improve display text.
- Foreground navigation with route progress, rerouting, Indonesian voice
  prompts, and a location foreground service.
- Photon-backed autocomplete, search, place lookup, and reverse geocoding for
  the configured Jakarta–Tangerang pilot region.
- Simulated flood polygons, route-risk metadata, snapshot synchronization, and
  blocked-route rejection.

Flood information is simulated and stored only in backend memory. It is not
live sensor data, does not survive a backend restart, and must not be treated
as proof that a route is safe.

## Architecture

```text
Android (MapLibre, Compose, MVVM)
  -> HTTPS NestJS API
     -> private GraphHopper 11 routing
     -> private Photon 0.5.0 geocoding
     -> in-memory simulated FloodHazardProvider
```

The Android app is one Gradle module with immutable UI state, StateFlow, typed
actions/effects, provider-neutral domain repositories, and a manual
application-scoped `AppContainer`.

See [docs/architecture.md](docs/architecture.md) for component and lifecycle
boundaries.

## Android builds

The only application build variants are `debug` and `release`. Both default to:

```text
https://api.gathra.my.id/
```

Build a debug APK with:

```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleDebug
```

Local backend overrides, emulator/device setup, and release differences are
documented in [docs/development.md](docs/development.md).

## Backend development

The local stack requires Docker Compose and a deliberately installed Photon
database volume. Setup, routing data, geocoding data, API contracts, flood
simulation, and backend quality checks are owned by
[backend/README.md](backend/README.md).

## Documentation

- [AGENTS.md](AGENTS.md): repository rules and verification for coding agents.
- [backend/README.md](backend/README.md): backend operation and provider data.
- [docs/architecture.md](docs/architecture.md): stable system architecture.
- [docs/development.md](docs/development.md): Android and end-to-end workflows.

OpenStreetMap-derived routing and geocoding data remains subject to
OpenStreetMap attribution and the ODbL.
