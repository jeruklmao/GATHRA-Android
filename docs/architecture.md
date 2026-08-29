# GATHRA architecture

## System boundary

```text
Android app
  |
  | normalized HTTP/JSON
  v
NestJS
  |-- Route service -------> GraphHopper 11.0
  |      `---------------> independent flood geometry evaluation
  |-- Geocoding provider --> Photon 0.5.0
  |-- Flood provider ------> PostgreSQL sensor state (production)
  |                          or explicit in-memory simulation (development)
  `-- Health -------------> selected routing + geocoding readiness
```

The deployed client path adds HTTPS and Cloudflare Tunnel before NestJS.
GraphHopper and Photon remain private. Local Compose publishes only NestJS port
3000; its geocoding provider network is internal.

## Android application

The native app is a single Gradle application module under package
`opsi.sman35jkt.gathra`. `GathraApplication` owns one application-scoped manual
`AppContainer`; no dependency-injection framework is used.

The only application variants are `debug` and `release`. Both construct remote
routing, geocoding, and flood repositories against one shared NestJS base URL,
plus the fused foreground navigation location source. Deterministic fakes are
test-source fixtures and are not packaged into either variant.

### Layers

- `core/model`: framework-independent coordinates, routes, manoeuvres, places,
  selection metadata, and flood models.
- `core/location`, `core/map`, `core/navigation`: stable platform/map
  abstractions and shared navigation helpers.
- `domain/route`: provider-neutral `RouteRepository`.
- `domain/geocoding`: provider-neutral `GeocodingRepository`.
- `domain/flood`: provider-neutral `FloodHazardRepository`.
- `domain/sensor`: sanitized current sensor/Gateway models and repository; no
  history or persistent cache.
- `domain/navigation`: navigation repository, session/progress/status models,
  and explicit state transitions.
- `data/route/remote`: Retrofit API, strict DTO mapping, and remote repository.
- `data/geocoding/remote`: Retrofit API, normalized DTO mapping, and remote
  repository.
- `data/flood/remote`: Retrofit/GeoJSON API, strict snapshot mapping, and remote
  repository.
- `data/sensor/remote`: strict Retrofit mapping for public
  `GET /api/v1/sensors/:nodeId`.
- `data/location`: one-shot foreground location and fused active-navigation
  updates.
- `data/navigation`: geometry projection, progress, deviation, filtering,
  reroute coordination, voice policy, and the application-scoped session
  engine.
- `feature/map`, `feature/geocoding`, `feature/navigation`: immutable UI state,
  typed actions/effects, ViewModels, and Compose surfaces.
- `service/navigation`: foreground service, notification, controller, and TTS
  lifecycle.

Retrofit DTOs, Android Location, and MapLibre objects never enter Android
domain or UI state.

### Preview, search, and coordinate authority

`MapRouteViewModel` owns route-preview state. It cancels stale requests,
supports permission-denied fallback behavior, and reverse-geocodes selected
map points asynchronously. A map-selected `GeoPoint` is authoritative;
reverse results may change labels only.

`PlaceSearchViewModel` retains its query across Activity recreation, applies a
minimum query length and debounce, and uses cancellation plus generation checks
so stale responses cannot replace newer results. Suggestions outside configured
coverage are visible but not selectable. Manual map selection remains available
when geocoding fails.

### Navigation ownership

`NavigationSessionRepository` retains the active session beyond one Activity
instance. `NavigationForegroundService` owns high-accuracy location
collection, rerouting, notification updates, and TTS while navigation is
active. `NavigationSessionEngine` performs route projection, progress,
off-route detection, guarded reroutes, and cleanup.

The app requests foreground location only. Location updates, reroute jobs, and
voice work stop on navigation stop or arrival. Process-death recovery is
limited because the session is not stored in a durable database.

MapLibre Android views are retained across Compose recomposition. Route,
marker, and flood geometry use owned map sources and layers rather than large
sets of Android view markers.

## NestJS application

NestJS exposes four provider-neutral surfaces:

- `routes`: strict request validation, normalized response mapping,
  GraphHopper client, flood-aware filtering, and error contracts.
- `geocoding`: autocomplete/search/lookup/reverse, provider adapter, bounded
  caches, concurrency/rate guards, regional policy, and opaque tokens.
- `flood`: read-only active GeoJSON snapshots, optional local development
  mutations, and an independently enabled bearer-authenticated administration
  controller.
- `health`: readiness for both selected providers.

URI versioning creates `/api/v1`. Global DTO validation rejects unknown input.
Request IDs and a sanitized common error envelope are applied across APIs.
OpenAPI is configured at `/api/docs` and `/api/docs-json`.

## GraphHopper boundary

GraphHopper 11.0 reads the OSM file mounted as `/data/region.osm` and maintains
its generated graph in a named cache. The checked-in fixture is intentionally
small; useful geographic routing coverage depends on the configured PBF.

The provider defines `car` and `motorcycle` profiles. The NestJS client asks
for GeoJSON geometry and instructions, validates snapped endpoints and
geometry, maps provider signs into GATHRA manoeuvre/modifier enums, and
constructs ordered step intervals ending in `ARRIVE`.

GraphHopper response types never leave the backend. Route IDs are normalized
opaque fingerprints rather than provider identifiers.

## Photon boundary

`GeocodingProvider` defines autocomplete, search, lookup, reverse, and health.
Photon is the normal Compose implementation; a fake provider remains available
inside the backend for deterministic backend tests and local development.

NestJS constrains Photon queries to the versioned buffered bounds over the
pinned Indonesia database. Direct provider IDs are not returned. NestJS signs
opaque place tokens and stores normalized details in a bounded TTL cache
because Photon has no lookup-by-OSM-ID endpoint. Reverse responses preserve the
requested coordinate.

Normal logs contain request IDs, durations, counts, and query lengths, not full
address-like queries or result text.

## Flood snapshot flow

Production uses GATHRA Node firmware 2.1.1 and LoRa Protocol 3 through GATHRA
Gateway firmware 2.2.0. Authenticated ingestion stores immutable telemetry in
PostgreSQL. The Backend sensor classifier combines telemetry with durable,
runtime-configurable deployment thresholds, hysteresis, and routing
multipliers. Every enabled deployment contributes its coverage polygon to the
public, unauthenticated `GET /api/v1/flood-hazards` snapshot, including LOW and
UNKNOWN states and stale/no-telemetry lifecycle states.

The Android flood domain preserves each hazard's `riskLevel`,
`routingMultiplier`, `freshness`, `reasonCodes`, nullable `observedAt` and
`validUntil`, `source`, and `sourceNodeIds`. It never reruns the Backend
classifier or infers routing policy from a risk label. Explicit in-memory
simulation remains a Backend development mode; Android presentation is
source-aware so simulation wording cannot leak into SENSOR data.

For each preview request:

1. NestJS captures one immutable active-hazard snapshot.
2. Flood polygons become request-scoped GraphHopper custom-model areas.
3. GraphHopper returns route candidates.
4. NestJS independently intersects every LineString with the same snapshot.
5. Routes touching a polygon whose runtime multiplier is zero are rejected;
   usable routes are ranked and exactly one is recommended.
6. Route-risk metadata records the snapshot used for evaluation.

Android polls the read-only snapshot only while its UI lifecycle is started.
A selected route is current only when its risk snapshot matches the visible
snapshot:

```text
SYNCHRONIZED
  -> OUTDATED_BY_FLOOD_UPDATE
  -> UPDATING
  -> SYNCHRONIZED (replacement matches target)
  -> STALE       (refresh/recalculation fails or mismatches)
```

Preview and navigation retain old geometry during validation but do not present
its old risk as current. Generation and target-snapshot checks prevent late
responses from replacing newer guidance. Active navigation reuses its guarded
foreground-service reroute flow.

Sensor freshness and snapshot retrieval are separate dimensions. HTTP 200
means Android received a valid API snapshot; it does not mean every sensor
measurement is `FRESH`:

- `FRESH` can carry LOW, MEDIUM, HIGH, BLOCKED, or UNKNOWN when a recent
  measurement is unusable.
- `STALE` carries UNKNOWN after a prior measurement passes `validUntil`.
- `NO_TELEMETRY` carries UNKNOWN with nullable observation/validity times
  before a deployment has usable telemetry.

UNKNOWN always means the current condition cannot be determined. It is not a
synonym for LOW, no flood, or safe. Stale/no-telemetry polygons remain visible
as neutral dashed coverage. Reason codes explain unavailable sensor states;
Android maps the bounded current codes into user-facing Indonesian and uses a
generic phrase for future codes.

The current bounded sensor reasons are `NO_TELEMETRY`, `STALE`,
`REFERENCE_DISTANCE_MISSING`, `ACCEPTED_DISTANCE_MISSING`, `FILTER_INVALID`,
`SENSOR_UNHEALTHY`, and `DEPLOYMENT_DISABLED`. Raw codes are retained in the
domain for contract fidelity but never shown directly in normal UI. For
`source=SENSOR`, `sourceNodeIds` provides compact Node provenance; simulated
hazards do not invent a sensor identity.

`routingMultiplier` is authoritative and independent of `riskLevel`: 1 means
no route penalty, a value strictly between 0 and 1 means a penalty, and 0 means
hard exclusion. Android explains this actual effect without hard-coding the
current Backend defaults.

For a SENSOR polygon with exactly one `sourceNodeId`, Android loads the public
current detail and renders a small marker at the Backend deployment coordinate.
Polygon and marker taps converge on the existing scrollable detail sheet. While
that sheet is open and lifecycle-active, detail refreshes every 30 seconds and
supports pull-to-refresh. Application refresh time is separate from the
Backend-authoritative measurement `observedAt` and freshness; failures preserve
only in-memory data with its original measurement time.

The first usable SENSOR coverage polygon is fit once from geometry bounds with
padding. Later flood/sensor/Gateway refreshes never recenter it, and user or
route/navigation camera ownership cancels the initial fit. A broad neutral
technical camera is used only while no backend geometry is available; it is not
presented as a real location or marker.

Android displays authoritative water height/effective risk, accepted distance,
temperature/humidity, Backend-derived heartbeat state, recent radio reception,
raw RSSI/SNR measurements, and sanitized delivery status. It does not calculate
water height, heartbeat status, radio quality, or delivery health. It does not
persist flood/sensor state or expose raw distance, battery, filter/health
diagnostics, ACK data, Gateway network/runtime internals, sensor history, or
charts. Polling is latest/current state, not continuous streaming.

## Deployment boundary

The repository defines the development Compose topology. The deployed service
uses the same provider boundaries behind `https://api.gathra.my.id/`, but
server paths, Cloudflare credentials, backups, and update/rollback scripts are
external operational state.

Committing to `main` does not deploy automatically. Android release signing and
distribution are also outside the repository's current build quality gate.

## Architectural constraints

- Android calls NestJS only; provider SDKs and hostnames remain server-side.
- Domain models remain provider- and framework-neutral.
- GeoJSON is `[longitude, latitude]`; Android `GeoPoint` is latitude then
  longitude.
- Map selection coordinates remain authoritative.
- No background-location permission or raw location-history persistence.
- No network/geocoding calls from Composables.
- No production secrets in source, Gradle properties, or BuildConfig.
- No automatic provider-data download, import, replacement, or deletion.
- No hosted geocoder fallback.
- Missing, stale, or simulated flood data is never a safety guarantee.
