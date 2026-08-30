# GATHRA Android

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
- Sensor-backed flood coverage polygons, route-risk metadata, snapshot
  synchronization, and runtime-multiplier route exclusion.
- A Backend-positioned current sensor marker and shared scrollable detail sheet
  with authoritative water state plus sanitized Gateway connectivity.

Production flood observations come from Protocol 3 Nodes through the Gateway
and PostgreSQL-backed Backend classifier. The public hazard API remains a
modeled observation, not proof that an area or route is safe. Explicit local
simulation remains supported for deterministic development.

## Architecture

```text
Android (MapLibre, Compose, MVVM)
  -> HTTPS NestJS API
     -> private GraphHopper 11 routing
     -> private Photon 0.5.0 geocoding
     -> PostgreSQL sensor telemetry + flood classification
```

The production flood path is:

```text
GATHRA Node 2.1.1 (LoRa Protocol 3)
  -> GATHRA Gateway 2.2.0
  -> Backend PostgreSQL telemetry
  -> sensor classification
  -> public /api/v1/flood-hazards
  -> GraphHopper routing + Android
```

Android preserves the Backend-provided risk, sensor freshness, reason codes,
nullable observation/validity timestamps, provenance Node IDs, and runtime
routing multiplier. `UNKNOWN` means the current condition cannot be
determined; it never means LOW or safe. A successful HTTP refresh is separate
from sensor freshness, so a valid response can contain `STALE` or
`NO_TELEMETRY` polygons.

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

## Backend integration

The API is maintained independently in
[JerukLMAO/GATHRA-Backend](https://github.com/JerukLMAO/GATHRA-Backend).
Its README owns local stack setup, routing and geocoding data, sensor flood
contracts, explicit flood simulation, and backend quality checks.

## Documentation

- [AGENTS.md](AGENTS.md): repository rules and verification for coding agents.
- [docs/architecture.md](docs/architecture.md): stable system architecture.
- [docs/development.md](docs/development.md): Android and end-to-end workflows.
- [GATHRA-Backend](https://github.com/JerukLMAO/GATHRA-Backend): backend
  operation and provider data.

OpenStreetMap-derived routing and geocoding data remains subject to
OpenStreetMap attribution and the ODbL.

---

Copyright © 2026 GATHRA Project. All rights reserved.

Source code and documentation in this repository are publicly viewable for inspection, academic review, and evaluation. No permission is granted to reproduce, redistribute, modify, commercialize, or create derivative works except where explicitly permitted by the repository's license or by written permission from the copyright holder.

If you use GATHRA in academic or research work, please provide appropriate attribution to the GATHRA Project and its associated publications.
