# GATHRA Android architecture

## System boundary

```text
Android (Compose + MapLibre)
  -> HTTPS NestJS
     |-- GraphHopper routing
     |-- Photon geocoding
     `-- PostgreSQL sensor telemetry, classification, and Gateway status
```

GraphHopper, Photon, PostgreSQL, and device credentials remain private Backend
concerns. Android uses one normalized API origin.

## Application structure

The app is one Gradle application module under
`opsi.sman35jkt.gathra`. `GathraApplication` owns an application-scoped manual
`AppContainer`. UI state is immutable and exposed through StateFlow with typed
actions and effects.

Main boundaries:

- `core/model`, `core/map`, `core/location`, and `core/navigation` contain
  shared models and platform abstractions.
- `domain/route`, `domain/geocoding`, `domain/flood`, `domain/sensor`, and
  `domain/navigation` define provider-neutral repositories and state.
- `data/*/remote` maps Retrofit DTOs into domain models.
- `data/location` and `data/navigation` own fused location, geometry
  projection, progress, deviation, reroute, and voice policy.
- `feature/map`, `feature/geocoding`, and `feature/navigation` own ViewModels
  and Compose surfaces.
- `service/navigation` owns foreground service, notification, and TTS lifetime.

Retrofit DTOs, Android Location, MapLibre objects, GraphHopper shapes, and Photon
shapes do not enter UI state.

## Coordinates, search, and map ownership

A coordinate selected on the map remains authoritative for routing. Reverse
geocoding may improve its display label but cannot replace the coordinate.
Suggestions outside configured coverage may be shown but cannot be selected.
Manual map selection remains available when search or location fails.

The initial camera policy collects finite points from Backend SENSOR polygons
and fits their geometry once with padding. It yields permanently when the user,
a route, or navigation takes camera ownership. The technical map fallback is
not shown as a location marker.

MapLibre views survive Compose recomposition. Flood polygons, sensor markers,
routes, and navigation geometry use owned GeoJSON sources and layers.

## Flood and sensor flow

The Backend is authoritative for:

- coverage geometry and snapshot identity;
- effective risk level, freshness, reason codes, and routing multiplier;
- water height and accepted-distance interpretation;
- sensor position and sanitized Gateway status.

Android preserves these values without rerunning the classifier. Every enabled
deployment may appear as LOW, MEDIUM, HIGH, BLOCKED, or UNKNOWN. STALE and
NO_TELEMETRY coverage remains visible and UNKNOWN.

For a SENSOR polygon with one source Node ID, Android loads
`GET /api/v1/sensors/:nodeId`, places the marker at the Backend position, and
shows the same detail sheet from polygon or marker selection. While the sheet
is visible and lifecycle-active, detail refreshes every 30 seconds and supports
pull-to-refresh. Data is in-memory only.

Flood snapshot polling is lifecycle-bound. A selected route is current only
when its risk snapshot matches the visible hazard snapshot:

```text
SYNCHRONIZED
  -> OUTDATED_BY_FLOOD_UPDATE
  -> UPDATING
  -> SYNCHRONIZED or STALE
```

Generation and target-snapshot checks prevent a late response from replacing a
newer state. Active navigation uses the same guarded foreground-service
revalidation and reroute path.

## Navigation ownership

`NavigationSessionRepository` retains an active session beyond one Activity
instance. `NavigationForegroundService` owns high-accuracy foreground location,
rerouting, notification updates, and TTS. Location, reroute jobs, and voice work
stop on navigation stop or arrival.

Only foreground location permission is requested. The session is not stored in
a durable database, so process-death recovery is limited.

## Security and safety constraints

- Debug and release use remote repositories and fused foreground location.
- Release requires HTTPS and disables cleartext; debug enables cleartext only
  for an explicit HTTP override.
- Android calls NestJS only and contains no production secrets.
- GeoJSON is `[longitude, latitude]`; Android `GeoPoint` is latitude then
  longitude.
- `UNKNOWN`, `STALE`, and `NO_TELEMETRY` never imply safe conditions.
- Route guidance remains modeled assistance, not a public-safety guarantee.
