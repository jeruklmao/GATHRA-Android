# GATHRA Android agent guide

Read [README.md](README.md), [docs/architecture.md](docs/architecture.md), and
[docs/development.md](docs/development.md) before changing code. Backend code,
provider data, and backend operations belong to the independent
[GATHRA-Backend repository](https://github.com/JerukLMAO/GATHRA-Backend).

## Purpose and verified baseline

GATHRA is a native Indonesian Android route-preview and foreground-navigation
pilot. Its stable boundary is:

```text
Android -> NestJS -> GraphHopper
                 -> Photon
                 -> PostgreSQL sensor-backed flood provider
```

- The live NestJS API base URL is `https://api.gathra.my.id/`.
- Health, route preview, autocomplete, reverse geocoding, and the read-only
  flood endpoint have been verified through public HTTPS.
- The public development flood endpoint returns HTTP 404.
- GraphHopper and Photon have no public provider port.
- Swagger is currently public at `https://api.gathra.my.id/api/docs` because
  the backend enables it unconditionally.
- Repository changes are not automatically deployed. Server update, rollback,
  credentials, and Cloudflare configuration are separate operational work.

The geocoding pilot covers Jakarta Pusat, Jakarta Selatan, Kota Tangerang, Kota
Tangerang Selatan, and the backend's versioned buffered envelope. Do not spell
Tangerang as “Tanggerang” except in explicit typo-quality fixtures.

## Android rules

- The supported application variants are only `debug` and `release`.
- Both variants default to `https://api.gathra.my.id/` through
  `BuildConfig.API_BASE_URL`.
- `GATHRA_API_BASE_URL` is the canonical debug override;
  `GATHRA_RELEASE_API_BASE_URL` is the canonical release override.
- The older route-prefixed property names are compatibility aliases only.
- Release overrides must use HTTPS, and release cleartext traffic stays
  disabled. Debug enables cleartext only when its resolved override uses HTTP.
- Debug and release always use `RemoteRouteRepository`,
  `RemoteGeocodingRepository`, `RemoteFloodHazardRepository`, and
  `FusedNavigationLocationSource`.
- Deterministic Android fake repositories and the location simulator live in
  `app/src/test`; do not wire them into an application variant.
- Keep the app as one module with MVVM/UDF, immutable state, StateFlow, typed
  actions/effects, and the manual `AppContainer`. Do not introduce Hilt or
  split modules without discussion.
- Keep domain models independent of Retrofit DTOs, Android Location, MapLibre,
  GraphHopper, and Photon types.
- Android must call NestJS only. Never expose or call GraphHopper or Photon
  directly from a device.
- A coordinate selected on the map is authoritative for routing. Reverse
  geocoding may replace display text only, never the coordinate.
- Keep manual map selection available when geocoding or location fails.
- Request foreground location only. Do not add `ACCESS_BACKGROUND_LOCATION`.
- Keep active navigation execution in the foreground service/repository layer,
  not in Composables or an Activity.
- Keep all Android user-facing text in `strings.xml`, in Indonesian, and use
  Material theme tokens rather than screen-local colors.

## Backend integration constraints

- Keep `RouteRepository`, `GeocodingRepository`, and
  `NavigationRepository` provider-neutral; DTOs never enter UI state.
- GeoJSON positions are `[longitude, latitude]`; Android `GeoPoint` constructor
  order remains `latitude`, then `longitude`.
- GraphHopper signs are normalized by the backend into GATHRA manoeuvre enums.
- Outside-coverage geocoding suggestions may be shown but cannot be selected.
- Never commit API keys, tokens, `.env`, signing material, address-like logs,
  or deployment artifacts.

## Flood-safety invariants

- Production flood hazards are sensor-backed modeled observations and are not
  a public-safety guarantee. In-memory hazards are explicit local simulation.
- Development mutation endpoints are unauthenticated and disabled by default.
  Enable them only for an isolated local test stack.
- A route intersecting a polygon whose runtime multiplier is zero cannot be
  selectable or recommended. Exclusion-only provider results return
  `NO_ROUTE_DUE_TO_FLOOD`.
- `UNKNOWN` and `NOT_EVALUATED` are never represented as LOW.
- A route-risk snapshot must match the visible hazard snapshot. Mismatch
  triggers guarded recalculation; stale guidance must not imply current safety.
- Preserve generation and target-snapshot checks in preview and navigation so
  late responses cannot replace newer guidance.
- Do not describe missing, stale, or simulated flood data as evidence that a
  route is safe.
- Do not change flood-risk multipliers, evaluation, ranking, or snapshot
  behavior as part of unrelated work.

## High-risk files

- `app/build.gradle.kts`: API URL validation, variants, BuildConfig, cleartext.
- `app/src/main/AndroidManifest.xml`: foreground permissions and cleartext.
- `app/src/main/java/opsi/sman35jkt/gathra/AppContainer.kt`: runtime wiring.
- `app/src/main/java/opsi/sman35jkt/gathra/GathraApp.kt`: screen ownership and
  lifecycle dispatch.
- `feature/map/MapRouteViewModel.kt`: cancellation, permissions, selection,
  reverse-coordinate authority, and flood snapshots.
- `feature/geocoding/PlaceSearchViewModel.kt`: debounce and stale responses.
- `core/map/MapLibreRouteMap.kt` and `MapLibreNavigationMap.kt`: Android view
  lifecycle and map-source/layer ownership.
- `data/navigation/NavigationSessionEngine.kt` and
  `service/navigation/NavigationForegroundService.kt`: location, reroute, TTS,
  and cleanup lifecycle.

## Verification

Use the Android Studio JBR on Fedora:

```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew clean
JAVA_HOME=/opt/android-studio/jbr ./gradlew testDebugUnitTest
JAVA_HOME=/opt/android-studio/jbr ./gradlew lintDebug
JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleDebug
JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleRelease
JAVA_HOME=/opt/android-studio/jbr ./gradlew compileDebugAndroidTestKotlin
```

Run connected tests when a compatible emulator/device is available:

```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew connectedDebugAndroidTest
```

Run focused tests first, then the full relevant matrix. Never run a full Photon
import merely to validate source changes.

## Current limitations and narrow priorities

- Sensor deployment policy and telemetry are PostgreSQL-backed. Android has no
  persistent offline flood cache or real-time push invalidation.
- Navigation survives Activity recreation through application-scoped state but
  has limited process-death recovery.

## Onboarding checklist

1. Run `git status --short --branch`, inspect the current diff, and identify the
   actual default branch before editing.
2. Read the three retained documentation files relevant to the change.
3. Confirm whether Android should use the public default or an explicit local
   debug override.
4. Inspect source, tests, workflows, and provider configuration rather than
   trusting historical PR text or generated reports.
5. Preserve coordinate authority, provider privacy, foreground-only location,
   and flood-safety invariants.
6. Run focused checks, then the full relevant verification matrix, and report
   only results actually observed.
