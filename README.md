# GATHRA Android

GATHRA Android is the native Indonesian route-preview and foreground-navigation
application for the GATHRA flood-monitoring pilot. It uses the official GATHRA
identity, Jetpack Compose, and MapLibre, and calls the provider-neutral GATHRA
Backend at <https://api.gathra.my.id/>.

Android never connects directly to GraphHopper, Photon, PostgreSQL, a GATHRA
Gateway, or a GATHRA Node.

## Current experience

- CAR and MOTORCYCLE route preview with one optional alternative.
- Map-selected origin and destination coordinates, search, autocomplete,
  reverse geocoding, and manual selection when location or geocoding fails.
- Foreground turn-by-turn navigation with route progress, off-route rerouting,
  Indonesian voice prompts, and a location foreground service.
- Sensor-backed coverage polygons and Backend-derived flood risk.
- A sensor marker positioned from the Backend deployment.
- A shared detail sheet showing water height, effective level, freshness, Node
  ID, accepted distance, temperature, humidity, Gateway heartbeat state and
  relative time, radio recency, RSSI/SNR, and sanitized delivery status.
- Flood-aware route exclusion, alternative ranking, and guarded snapshot
  revalidation during preview and active navigation.

At startup, the map fits the geometry of the first usable Backend SENSOR
coverage snapshot. User interaction, route display, or navigation then owns the
camera; later refreshes do not recenter it. No fixed city marker or hard-coded
demo point is shown.

## Data flow

```text
GATHRA Node
  -> LoRa Protocol 3
GATHRA Gateway
  -> authenticated Internet ingestion
GATHRA Backend
  -> PostgreSQL telemetry and sensor classification
  -> routing and public APIs
GATHRA Android
```

Android reads flood polygons from `GET /api/v1/flood-hazards` and sanitized
current sensor detail from `GET /api/v1/sensors/:nodeId`. It does not expose
raw distance, battery, protocol flags, sensor history, or Gateway network and
runtime internals.

The Backend-provided `routingMultiplier` is authoritative: 1 has no local
penalty, values between 0 and 1 penalize, and 0 is a hard exclusion regardless
of the risk label. Android does not recalculate water height, classification,
heartbeat health, or routing policy.

## Flood-safety semantics

- `UNKNOWN` is not LOW or safe.
- `STALE` and `NO_TELEMETRY` are not LOW.
- A successful API response may contain stale or unavailable sensor state.
- An area outside monitored polygons is not known flood-free.
- Routing is based on modeled observations and cannot guarantee safety.

## Build configuration

Application version: **1.0** (`versionCode` 1). Minimum SDK is 24 and target SDK
is 36. The only application variants are `debug` and `release`; both default to:

```text
https://api.gathra.my.id/
```

Build and verify with the Android Studio JBR:

```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew testDebugUnitTest
JAVA_HOME=/opt/android-studio/jbr ./gradlew lintDebug
JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleDebug
JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleRelease
JAVA_HOME=/opt/android-studio/jbr ./gradlew compileDebugAndroidTestKotlin
```

Release assembly is an unsigned compilation gate. Signing and distribution are
managed outside this repository.

See [architecture](docs/architecture.md) and
[development](docs/development.md) for current boundaries and workflows. The
Backend contract is maintained in
[GATHRA-Backend](https://github.com/JerukLMAO/GATHRA-Backend).

OpenStreetMap-derived map, routing, and geocoding data remain subject to
OpenStreetMap attribution and the ODbL.

---

Copyright © 2026 GATHRA Project. All rights reserved.

Source code and documentation in this repository are publicly viewable for inspection, academic review, and evaluation. No permission is granted to reproduce, redistribute, modify, commercialize, or create derivative works except where explicitly permitted by the repository's license or by written permission from the copyright holder.

If you use GATHRA in academic or research work, please provide appropriate attribution to the GATHRA Project and its associated publications.
