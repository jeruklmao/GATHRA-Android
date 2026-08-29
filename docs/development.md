# GATHRA development

This guide owns Android, emulator/device, and end-to-end development workflows.
Backend provider data and local Compose operation are documented in the
[GATHRA-Backend README](https://github.com/JerukLMAO/GATHRA-Backend#readme).

## Public API mode

Both Android variants use the deployed API by default:

```text
https://api.gathra.my.id/
```

No Gradle property, API key, ADB reverse rule, or local backend is required for
the default mode. The app sends routing, geocoding, flood-read, and health
traffic to the same NestJS origin.

Smoke the public deployment before diagnosing Android networking:

```bash
curl --fail --silent \
  https://api.gathra.my.id/api/v1/health

curl --fail --silent \
  "https://api.gathra.my.id/api/v1/geocoding/autocomplete?q=Monas&limit=6"

curl --fail --silent \
  "https://api.gathra.my.id/api/v1/geocoding/reverse?lat=-6.1754&lon=106.8272"

curl --fail --silent \
  https://api.gathra.my.id/api/v1/flood-hazards

curl --fail --silent \
  --request POST \
  --header "Content-Type: application/json" \
  --data '{
    "origin": {"latitude": -6.1939, "longitude": 106.8250},
    "destination": {"latitude": -6.2124, "longitude": 106.8094},
    "travelMode": "CAR",
    "alternatives": 1
  }' \
  https://api.gathra.my.id/api/v1/routes/preview
```

The public development flood surface must remain unavailable:

```bash
curl --silent --output /dev/null --write-out '%{http_code}\n' \
  https://api.gathra.my.id/api/v1/dev/flood-hazards
# Expected: 404
```

## API base URL overrides

Use an override only when Android must reach an explicitly started local or
alternate NestJS instance.

Canonical properties:

- `GATHRA_API_BASE_URL`: debug.
- `GATHRA_RELEASE_API_BASE_URL`: release.

The deprecated `GATHRA_ROUTE_API_BASE_URL` and
`GATHRA_RELEASE_ROUTE_API_BASE_URL` names remain lower-precedence compatibility
aliases. New scripts and developer configuration should use the canonical
names.

Set a persistent personal override in `~/.gradle/gradle.properties`, or pass it
for one invocation:

```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew installDebug \
  -PGATHRA_API_BASE_URL=http://10.0.2.2:3000/
```

Gradle validates the resolved value during configuration without making a
network request. It must be non-empty, an absolute HTTP(S) URL with a host,
contain no credentials/query/fragment, and end with `/`. Release additionally
requires HTTPS.

Debug cleartext is disabled for the default HTTPS URL and enabled only when the
resolved debug override explicitly uses HTTP. Release always disables
cleartext.

## Build variants

| Variant | API default | Runtime repositories/location | Cleartext |
| --- | --- | --- | --- |
| `debug` | public HTTPS | remote route/geocoding/flood + fused location | only for explicit HTTP override |
| `release` | public HTTPS | remote route/geocoding/flood + fused location | disabled; override must be HTTPS |

There is no demo, mock, staging, or fake application variant. Deterministic
Android fakes are unit-test fixtures only.

Release assembly is an unsigned compilation quality gate; signing and
distribution require a separate release process.

## Android emulator workflow

List and start an available AVD:

```bash
emulator -list-avds
emulator -avd Pixel_8
adb wait-for-device
adb shell getprop sys.boot_completed
```

Build, install, and launch the public-API debug app:

```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew installDebug
adb shell am force-stop opsi.sman35jkt.gathra
adb shell monkey -p opsi.sman35jkt.gathra -c android.intent.category.LAUNCHER 1
```

Confirm package/process/service state and inspect failures:

```bash
adb shell pm path opsi.sman35jkt.gathra
adb shell dumpsys package opsi.sman35jkt.gathra
adb shell dumpsys activity services opsi.sman35jkt.gathra
adb logcat -d | rg -i \
  'FATAL EXCEPTION|ANR in|AndroidRuntime|UnknownHostException|SSLHandshakeException'
```

For a local backend, the emulator host alias is `10.0.2.2`, so use the debug
override shown above. Never point Android at provider ports 8989 or 2322.

## Physical-device workflow

The public API default works directly when the device has internet access.

For a USB-connected device talking to local NestJS:

```bash
adb reverse tcp:3000 tcp:3000
JAVA_HOME=/opt/android-studio/jbr ./gradlew installDebug \
  -PGATHRA_API_BASE_URL=http://127.0.0.1:3000/
adb reverse --list
```

Recreate the reverse rule after reconnecting the device. For a same-LAN
device, use the development host's LAN address and allow port 3000 only on the
trusted network:

```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew installDebug \
  -PGATHRA_API_BASE_URL=http://192.168.1.25:3000/
```

Do not publish GraphHopper or Photon for device access.

## Route and navigation checks

1. Use current location or select an origin manually.
2. Select a destination and request both CAR and MOTORCYCLE previews.
3. Confirm one route is recommended and an alternative is selectable when the
   backend returns one.
4. Confirm distance, ETA, route steps, and flood-risk wording are present.
5. Start navigation, grant foreground location, and confirm the ongoing
   notification appears.
6. Pan and recenter the map, mute/unmute voice prompts, rotate the Activity,
   and confirm the session remains active.
7. Exercise an off-route location when safely possible and confirm guarded
   rerouting.
8. Stop navigation and confirm the service, notification, high-accuracy
   location updates, and TTS work stop.

Android requests no background-location permission. Android 13+ may ask for
notification permission separately; denying it does not grant location access.

## Geocoding checks

1. Open origin or destination search and enter at least three characters.
2. Confirm debounce/loading behavior and that a newer query is not overwritten
   by stale results.
3. Select a supported suggestion and confirm its marker and readable label.
4. Repeat for the other endpoint and confirm route preview recalculates.
5. Select **Pilih titik di peta**. Confirm the marker/coordinate updates
   immediately and reverse geocoding changes display text only.
6. Try a result outside the supported buffer and confirm it cannot be selected.
7. Rotate/recreate the Activity while search is open and confirm the query is
   retained.
8. Stop the local geocoder/backend when using local mode. Search should show a
   retryable Indonesian error while manual map selection remains available.

The map-selected coordinate is always authoritative for routing.

## Flood contract and local simulation checks

Production uses the public sensor-backed endpoint. Its current data path is:

```text
Node 2.1.1 / Protocol 3 -> Gateway 2.2.0 -> PostgreSQL telemetry
  -> Backend sensor classification -> public flood hazards
  -> routing + Android
```

For a read-only production check, open a returned polygon and confirm sensor
source/provenance, Indonesian freshness, observation time, actual routing
effect, and sensor-coverage disclaimer. HTTP success is only retrieval status;
the payload may legitimately report `STALE` or `NO_TELEMETRY`. `UNKNOWN` never
means safe, and nullable `observedAt`/`validUntil` are valid for no telemetry.

Do not change production thresholds or disrupt a Node to force lifecycle
states. Use deterministic Android fixtures or an isolated local Backend for
STALE, NO_TELEMETRY, health-reason, and multiplier visual checks.

### Explicit local simulation

Public flood mutation endpoints are disabled. Start an isolated local stack
with the opt-in from the
[GATHRA-Backend README](https://github.com/JerukLMAO/GATHRA-Backend#local-flood-simulation),
then use:

```bash
curl --fail --request DELETE \
  http://127.0.0.1:3000/api/v1/dev/flood-hazards
curl --fail --request POST \
  http://127.0.0.1:3000/api/v1/dev/flood-hazards/presets/central-corridor-high
curl --fail --request POST \
  http://127.0.0.1:3000/api/v1/dev/flood-hazards/presets/central-corridor-blocked
```

With Android pointed at local NestJS, confirm:

- HIGH/BLOCKED polygons render below route and marker layers.
- Tapping a polygon never prevents deliberate manual point-selection mode.
- A newer snapshot triggers updating/recalculation rather than showing an old
  LOW assessment as current.
- Failure retains geometry only as stale guidance and exposes retry.
- A blocked-only route set is rejected rather than recommended.
- `UNKNOWN` and `NOT_EVALUATED` remain neutral/caution states.
- simulated wording appears only for `source=SIMULATED`.

Local simulation is in-memory and not evidence that a route is safe.

Never enable the unauthenticated `/dev` surface on the public deployment.
Android uses only the unauthenticated read-only flood endpoint; it must never
embed flood-administrator or Gateway credentials.

Also verify the public current `GET /api/v1/sensors/:nodeId`, Backend-coordinate
marker, shared polygon/marker detail sheet, 30-second open-sheet refresh,
pull-to-refresh, and one-shot initial polygon camera fit. Measurement age and
application refresh time are different. Use deterministic fixtures—not
production writes—for stale/unavailable states. The UI intentionally has no
history/chart, raw distance, battery, filter/health detail, ACK metrics, or
Gateway network/runtime internals.

## Android quality commands

```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew clean
JAVA_HOME=/opt/android-studio/jbr ./gradlew testDebugUnitTest
JAVA_HOME=/opt/android-studio/jbr ./gradlew lintDebug
JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleDebug
JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleRelease
JAVA_HOME=/opt/android-studio/jbr ./gradlew compileDebugAndroidTestKotlin
```

When a compatible emulator/device is available:

```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew connectedDebugAndroidTest
```

Inspect generated configuration rather than assuming the Gradle declaration
worked:

```bash
find app/build/generated -path '*BuildConfig*' -type f -print
find app/build/outputs/apk -type f -print
JAVA_HOME=/opt/android-studio/jbr ./gradlew tasks --all | rg -i 'assemble|install|unitTest'
```

The Android CI workflow runs debug unit tests, debug lint, and debug/release
assembly on pull requests and pushes to `main`.

## Backend validation

Backend quality commands and Compose/provider checks belong to the independent
[GATHRA-Backend repository](https://github.com/JerukLMAO/GATHRA-Backend#quality-checks)
and are not run from this Android checkout.
