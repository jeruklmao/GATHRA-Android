# GATHRA Android development

## API mode

Debug and release default to:

```text
https://api.gathra.my.id/
```

No API key, Gradle override, local Backend, or ADB reverse rule is required for
that mode. A read-only smoke check is:

```bash
curl --fail --silent https://api.gathra.my.id/api/v1/health
curl --fail --silent https://api.gathra.my.id/api/v1/flood-hazards
```

Android communicates only with NestJS. GraphHopper and Photon have no Android
configuration.

## API base URL overrides

Use `GATHRA_API_BASE_URL` for debug and `GATHRA_RELEASE_API_BASE_URL` for
release. Values must be absolute HTTP(S) URLs with a host, contain no
credentials/query/fragment, and end with `/`. Release overrides must use HTTPS.

For an emulator connected to a local Backend on the development host:

```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew installDebug \
  -PGATHRA_API_BASE_URL=http://10.0.2.2:3000/
```

For a USB device, use `adb reverse` and the loopback address:

```bash
adb reverse tcp:3000 tcp:3000
JAVA_HOME=/opt/android-studio/jbr ./gradlew installDebug \
  -PGATHRA_API_BASE_URL=http://127.0.0.1:3000/
```

Debug cleartext is enabled only when the resolved override uses HTTP. Release
always disables cleartext.

## Build and test

Use the Android Studio JBR:

```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew clean
JAVA_HOME=/opt/android-studio/jbr ./gradlew testDebugUnitTest
JAVA_HOME=/opt/android-studio/jbr ./gradlew lintDebug
JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleDebug
JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleRelease
JAVA_HOME=/opt/android-studio/jbr ./gradlew compileDebugAndroidTestKotlin
```

Run connected tests when a compatible emulator or device is available:

```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew connectedDebugAndroidTest
```

The application variants are only `debug` and `release`. Both use
`RemoteRouteRepository`, `RemoteGeocodingRepository`,
`RemoteFloodHazardRepository`, `RemoteSensorRepository`, and fused foreground
location. Deterministic repositories and location fixtures live under test
sources and are not packaged into either variant.

## Contract verification

When changing network models, verify them against current Backend controllers,
DTOs, and tests:

- `POST /api/v1/routes/preview`
- `GET /api/v1/geocoding/autocomplete`
- `GET /api/v1/geocoding/search`
- `GET /api/v1/geocoding/places/:id`
- `GET /api/v1/geocoding/reverse`
- `GET /api/v1/flood-hazards`
- `GET /api/v1/sensors/:nodeId`
- `GET /api/v1/health`

The public sensor contract intentionally exposes current accepted distance,
water height, effective flood level, freshness, environmental readings, and a
sanitized Gateway summary. It does not expose raw distance, battery, flags,
reference configuration, history, or Gateway internals.

Validate these safety states in unit/UI tests:

- UNKNOWN is rendered as unavailable information, never LOW;
- STALE and NO_TELEMETRY coverage stays visible;
- multiplier zero prevents route selection;
- snapshot mismatch triggers guarded recalculation;
- sensor detail failure retains only the timestamped in-memory value;
- initial camera fitting uses Backend SENSOR polygon geometry and yields to
  user, route, or navigation ownership.

## Development constraints

- Keep map-selected coordinates authoritative.
- Keep provider DTOs out of domain and UI state.
- Request foreground location only.
- Keep active navigation execution in the repository/service layer.
- Keep user-facing text in Indonesian `strings.xml`.
- Do not commit `.env`, API keys, tokens, signing material, address-like logs,
  or deployment artifacts.
