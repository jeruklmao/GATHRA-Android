package opsi.sman35jkt.gathra.core.map

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.view.Gravity
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.color
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.match
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.fillOutlineColor
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import opsi.sman35jkt.gathra.core.model.FloodHazardSnapshot
import opsi.sman35jkt.gathra.core.model.GeoPoint
import opsi.sman35jkt.gathra.core.model.RouteOption
import opsi.sman35jkt.gathra.data.navigation.GeoMath
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

enum class NavigationCameraMode {
    FOLLOW,
    FREE,
    OVERVIEW,
}

@Immutable
data class NavigationMapColors(
    val remainingRoute: Color,
    val remainingRouteOutline: Color,
    val completedRoute: Color,
    val accuracyHalo: Color,
    val accuracyHaloOutline: Color,
    val userPuck: Color,
    val userPuckHeading: Color,
    val userPuckStroke: Color,
    val destinationMarker: Color,
    val destinationMarkerStroke: Color,
)

@Composable
fun MapLibreNavigationMap(
    activeRoute: RouteOption,
    travelledDistanceMeters: Double,
    matchedLocation: GeoPoint?,
    rawLocation: GeoPoint?,
    bearingDegrees: Double?,
    accuracyMeters: Double?,
    destination: GeoPoint,
    cameraMode: NavigationCameraMode,
    topOverlayClearance: Dp,
    bottomOverlayClearance: Dp,
    colors: NavigationMapColors,
    floodSnapshot: FloodHazardSnapshot? = null,
    onManualPan: () -> Unit,
    onMapError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestCameraMode by rememberUpdatedState(cameraMode)
    val latestOnManualPan by rememberUpdatedState(onManualPan)
    val latestOnMapError by rememberUpdatedState(onMapError)

    val mapView = remember(context, lifecycleOwner) {
        createNavigationMapViewOrNull(context)
    }

    if (mapView == null) {
        LaunchedEffect(Unit) {
            latestOnMapError()
        }
        Box(modifier = modifier)
        return
    }

    val renderer = remember(mapView) {
        MapNavigationRenderer(mapView)
    }

    DisposableEffect(mapView, lifecycleOwner) {
        val lifecycleBridge = NavigationMapLifecycleBridge(mapView)
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> lifecycleBridge.onStart()
                Lifecycle.Event.ON_RESUME -> lifecycleBridge.onResume()
                Lifecycle.Event.ON_PAUSE -> lifecycleBridge.onPause()
                Lifecycle.Event.ON_STOP -> lifecycleBridge.onStop()
                Lifecycle.Event.ON_DESTROY -> {
                    renderer.dispose()
                    lifecycleBridge.onDestroy()
                }
                else -> Unit
            }
        }
        val lowMemoryCallbacks = NavigationMapLowMemoryCallbacks(lifecycleBridge::onLowMemory)

        renderer.attach(
            cameraMode = { latestCameraMode },
            onManualPan = { latestOnManualPan() },
            onMapError = { latestOnMapError() },
        )
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        context.applicationContext.registerComponentCallbacks(lowMemoryCallbacks)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            context.applicationContext.unregisterComponentCallbacks(lowMemoryCallbacks)
            renderer.dispose()
            lifecycleBridge.onDestroy()
        }
    }

    AndroidView(
        factory = {
            mapView.apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        modifier = modifier,
        update = {
            renderer.render(
                NavigationMapRenderModel(
                    activeRoute = activeRoute,
                    travelledDistanceMeters = travelledDistanceMeters
                        .takeIf(Double::isFinite)
                        ?.coerceAtLeast(0.0)
                        ?: 0.0,
                    matchedLocation = matchedLocation,
                    rawLocation = rawLocation,
                    bearingDegrees = bearingDegrees
                        ?.takeIf(Double::isFinite)
                        ?.normalisedBearing(),
                    accuracyMeters = accuracyMeters
                        ?.takeIf(Double::isFinite)
                        ?.coerceAtLeast(0.0),
                    destination = destination,
                    cameraMode = cameraMode,
                    topOverlayClearancePx = with(density) {
                        topOverlayClearance.coerceAtLeast(0.dp).roundToPx()
                    },
                    bottomOverlayClearancePx = with(density) {
                        bottomOverlayClearance.coerceAtLeast(0.dp).roundToPx()
                    },
                    density = density.density,
                    colors = colors,
                    floodSnapshot = floodSnapshot,
                ),
            )
        },
    )
}

private fun createNavigationMapViewOrNull(context: Context): MapView? =
    runCatching {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).also { mapView ->
            mapView.onCreate(null)
        }
    }.getOrNull()

private data class NavigationMapRenderModel(
    val activeRoute: RouteOption,
    val travelledDistanceMeters: Double,
    val matchedLocation: GeoPoint?,
    val rawLocation: GeoPoint?,
    val bearingDegrees: Double?,
    val accuracyMeters: Double?,
    val destination: GeoPoint,
    val cameraMode: NavigationCameraMode,
    val topOverlayClearancePx: Int,
    val bottomOverlayClearancePx: Int,
    val density: Float,
    val colors: NavigationMapColors,
    val floodSnapshot: FloodHazardSnapshot?,
)

private class MapNavigationRenderer(
    private val mapView: MapView,
) {
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var latestModel: NavigationMapRenderModel? = null
    private var lastAppliedModel: NavigationMapRenderModel? = null
    private var cameraMoveStartedListener: MapLibreMap.OnCameraMoveStartedListener? = null
    private var mapFailureListener: MapView.OnDidFailLoadingMapListener? = null
    private var lastCameraMode: NavigationCameraMode? = null
    private var lastFollowTarget: GeoPoint? = null
    private var lastFollowBearing: Double? = null
    private var lastOverviewRouteId: String? = null
    private var cameraRequestVersion = 0L
    private var disposed = false
    private var errorReported = false

    fun attach(
        cameraMode: () -> NavigationCameraMode,
        onManualPan: () -> Unit,
        onMapError: () -> Unit,
    ) {
        if (disposed || mapFailureListener != null) return

        val failureListener = MapView.OnDidFailLoadingMapListener {
            reportError(onMapError)
        }
        mapFailureListener = failureListener
        mapView.addOnDidFailLoadingMapListener(failureListener)

        mapView.getMapAsync { readyMap ->
            if (disposed) return@getMapAsync

            map = readyMap
            configureMapUi(readyMap)
            readyMap.cameraPosition = CameraPosition.Builder()
                .target(
                    LatLng(
                        MapStyleConfig.INITIAL_FALLBACK_LATITUDE,
                        MapStyleConfig.INITIAL_FALLBACK_LONGITUDE,
                    ),
                )
                .zoom(MapStyleConfig.INITIAL_FALLBACK_ZOOM)
                .build()

            val moveStartedListener = MapLibreMap.OnCameraMoveStartedListener { reason ->
                if (
                    reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE &&
                    cameraMode() != NavigationCameraMode.FREE
                ) {
                    onManualPan()
                }
            }
            cameraMoveStartedListener = moveStartedListener
            readyMap.addOnCameraMoveStartedListener(moveStartedListener)

            runCatching {
                readyMap.setStyle(
                    Style.Builder().fromUri(MapStyleConfig.PUBLIC_STYLE_URI),
                ) { loadedStyle ->
                    if (disposed) return@setStyle

                    runCatching {
                        installSourcesAndLayers(loadedStyle)
                        style = loadedStyle
                        errorReported = false
                        latestModel?.let(::applyModel)
                    }.onFailure {
                        reportError(onMapError)
                    }
                }
            }.onFailure {
                reportError(onMapError)
            }
        }
    }

    fun render(model: NavigationMapRenderModel) {
        if (disposed) return
        latestModel = model
        if (style != null && model != lastAppliedModel) {
            runCatching {
                applyModel(model)
            }.onFailure {
                mapFailureListener?.onDidFailLoadingMap(it.message.orEmpty())
            }
        }
    }

    private fun configureMapUi(map: MapLibreMap) {
        map.uiSettings.apply {
            isCompassEnabled = false
            setLogoGravity(Gravity.BOTTOM or Gravity.START)
            setAttributionGravity(Gravity.BOTTOM or Gravity.START)
        }
    }

    private fun installSourcesAndLayers(style: Style) {
        val emptyFeatures = FeatureCollection.fromFeatures(emptyArray<Feature>())

        style.addSource(GeoJsonSource(FLOOD_SOURCE_ID, emptyFeatures))
        style.addSource(GeoJsonSource(COMPLETED_ROUTE_SOURCE_ID, emptyFeatures))
        style.addSource(GeoJsonSource(REMAINING_ROUTE_SOURCE_ID, emptyFeatures))
        style.addSource(GeoJsonSource(ACCURACY_HALO_SOURCE_ID, emptyFeatures))
        style.addSource(GeoJsonSource(DESTINATION_SOURCE_ID, emptyFeatures))
        style.addSource(GeoJsonSource(USER_PUCK_SOURCE_ID, emptyFeatures))
        style.addSource(GeoJsonSource(USER_HEADING_SOURCE_ID, emptyFeatures))

        style.addLayer(
            FillLayer(FLOOD_FILL_LAYER_ID, FLOOD_SOURCE_ID).withProperties(
                fillColor(
                    match(
                        get(FLOOD_VISUAL_STATE_PROPERTY),
                        literal("LOW"), color(0x332196F3.toInt()),
                        literal("MEDIUM"), color(0x40FF9800.toInt()),
                        literal("HIGH"), color(0x4DEF5350.toInt()),
                        literal("BLOCKED"), color(0x59B71C1C.toInt()),
                        color(0x339E9E9E.toInt()),
                    ),
                ),
                fillOpacity(
                    match(
                        get(FLOOD_VISUAL_STATE_PROPERTY),
                        literal("STALE"), literal(0.55f),
                        literal("NO_TELEMETRY"), literal(0.45f),
                        literal("UNSPECIFIED_SENSOR"), literal(0.45f),
                        literal(1.0f),
                    ),
                ),
            ),
        )
        style.addLayerAbove(
            LineLayer(FLOOD_OUTLINE_LAYER_ID, FLOOD_SOURCE_ID).withProperties(
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
                lineWidth(2.5f),
                lineOpacity(
                    match(
                        get(FLOOD_VISUAL_STATE_PROPERTY),
                        literal("STALE"), literal(0.0f),
                        literal("NO_TELEMETRY"), literal(0.0f),
                        literal("UNSPECIFIED_SENSOR"), literal(0.0f),
                        literal(1.0f),
                    ),
                ),
                lineColor(
                    match(
                        get(FLOOD_VISUAL_STATE_PROPERTY),
                        literal("LOW"), color(0xFF2196F3.toInt()),
                        literal("MEDIUM"), color(0xFFFF9800.toInt()),
                        literal("HIGH"), color(0xFFEF5350.toInt()),
                        literal("BLOCKED"), color(0xFFB71C1C.toInt()),
                        color(0xFF9E9E9E.toInt()),
                    ),
                ),
            ),
            FLOOD_FILL_LAYER_ID,
        )
        style.addLayerAbove(
            LineLayer(FLOOD_UNCERTAIN_OUTLINE_LAYER_ID, FLOOD_SOURCE_ID).withProperties(
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
                lineWidth(2.5f),
                lineColor(0xFF9E9E9E.toInt()),
                lineDasharray(arrayOf(1.25f, 1.5f)),
                lineOpacity(
                    match(
                        get(FLOOD_VISUAL_STATE_PROPERTY),
                        literal("STALE"), literal(0.72f),
                        literal("NO_TELEMETRY"), literal(0.58f),
                        literal("UNSPECIFIED_SENSOR"), literal(0.58f),
                        literal(0.0f),
                    ),
                ),
            ),
            FLOOD_OUTLINE_LAYER_ID,
        )

        style.addLayerAbove(
            LineLayer(COMPLETED_ROUTE_LAYER_ID, COMPLETED_ROUTE_SOURCE_ID).withProperties(
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
                lineWidth(COMPLETED_ROUTE_WIDTH),
                lineOpacity(COMPLETED_ROUTE_OPACITY),
            ),
            FLOOD_UNCERTAIN_OUTLINE_LAYER_ID,
        )
        style.addLayerAbove(
            LineLayer(REMAINING_ROUTE_OUTLINE_LAYER_ID, REMAINING_ROUTE_SOURCE_ID).withProperties(
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
                lineWidth(REMAINING_ROUTE_OUTLINE_WIDTH),
            ),
            COMPLETED_ROUTE_LAYER_ID,
        )
        style.addLayerAbove(
            LineLayer(REMAINING_ROUTE_LAYER_ID, REMAINING_ROUTE_SOURCE_ID).withProperties(
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
                lineWidth(REMAINING_ROUTE_WIDTH),
            ),
            REMAINING_ROUTE_OUTLINE_LAYER_ID,
        )
        style.addLayerAbove(
            FillLayer(ACCURACY_HALO_LAYER_ID, ACCURACY_HALO_SOURCE_ID).withProperties(
                fillOpacity(ACCURACY_HALO_OPACITY),
            ),
            REMAINING_ROUTE_LAYER_ID,
        )
        style.addLayerAbove(
            CircleLayer(DESTINATION_LAYER_ID, DESTINATION_SOURCE_ID).withProperties(
                circleRadius(DESTINATION_RADIUS),
                circleStrokeWidth(DESTINATION_STROKE_WIDTH),
            ),
            ACCURACY_HALO_LAYER_ID,
        )
        style.addLayerAbove(
            CircleLayer(USER_HEADING_LAYER_ID, USER_HEADING_SOURCE_ID).withProperties(
                circleRadius(USER_HEADING_RADIUS),
                circleStrokeWidth(USER_HEADING_STROKE_WIDTH),
            ),
            DESTINATION_LAYER_ID,
        )
        style.addLayerAbove(
            CircleLayer(USER_PUCK_LAYER_ID, USER_PUCK_SOURCE_ID).withProperties(
                circleRadius(USER_PUCK_RADIUS),
                circleStrokeWidth(USER_PUCK_STROKE_WIDTH),
            ),
            USER_HEADING_LAYER_ID,
        )
    }

    private fun applyModel(model: NavigationMapRenderModel) {
        val loadedStyle = style ?: return
        val routeSplit = splitRouteForDisplay(
            route = model.activeRoute,
            travelledDistanceMeters = model.travelledDistanceMeters,
        )
        val puckLocation = model.matchedLocation?.takeIf(GeoPoint::isRenderable)
            ?: model.rawLocation?.takeIf(GeoPoint::isRenderable)
        val rawLocation = model.rawLocation?.takeIf(GeoPoint::isRenderable)
        val destination = model.destination.takeIf(GeoPoint::isRenderable)
        val headingPoint = if (puckLocation != null && model.bearingDegrees != null) {
            puckLocation.pointAtDistanceAndBearing(
                distanceMeters = HEADING_INDICATOR_DISTANCE_METERS,
                bearingDegrees = model.bearingDegrees.toDouble(),
            )
        } else {
            null
        }

        if (model.floodSnapshot != null) {
            loadedStyle.navigationGeoJsonSource(FLOOD_SOURCE_ID).setGeoJson(
                floodFeatureCollection(model.floodSnapshot),
            )
        } else {
            loadedStyle.navigationGeoJsonSource(FLOOD_SOURCE_ID).setGeoJson(
                FeatureCollection.fromFeatures(emptyArray<Feature>()),
            )
        }

        loadedStyle.navigationGeoJsonSource(COMPLETED_ROUTE_SOURCE_ID).setGeoJson(
            lineFeatureCollection(routeSplit.completed),
        )
        loadedStyle.navigationGeoJsonSource(REMAINING_ROUTE_SOURCE_ID).setGeoJson(
            lineFeatureCollection(routeSplit.remaining),
        )
        loadedStyle.navigationGeoJsonSource(ACCURACY_HALO_SOURCE_ID).setGeoJson(
            accuracyHaloFeatureCollection(
                center = rawLocation,
                accuracyMeters = model.accuracyMeters,
            ),
        )
        loadedStyle.navigationGeoJsonSource(DESTINATION_SOURCE_ID).setGeoJson(
            navigationPointFeatureCollection(destination),
        )
        loadedStyle.navigationGeoJsonSource(USER_PUCK_SOURCE_ID).setGeoJson(
            navigationPointFeatureCollection(puckLocation),
        )
        loadedStyle.navigationGeoJsonSource(USER_HEADING_SOURCE_ID).setGeoJson(
            navigationPointFeatureCollection(headingPoint),
        )

        updateLayerColors(loadedStyle, model.colors)
        updateMapControlMargins(model)
        updateCamera(model, routeSplit.remaining, puckLocation)
        lastAppliedModel = model
    }

    private fun updateLayerColors(
        style: Style,
        colors: NavigationMapColors,
    ) {
        (style.getLayer(COMPLETED_ROUTE_LAYER_ID) as? LineLayer)?.setProperties(
            lineColor(colors.completedRoute.toArgb()),
        )
        (style.getLayer(REMAINING_ROUTE_OUTLINE_LAYER_ID) as? LineLayer)?.setProperties(
            lineColor(colors.remainingRouteOutline.toArgb()),
        )
        (style.getLayer(REMAINING_ROUTE_LAYER_ID) as? LineLayer)?.setProperties(
            lineColor(colors.remainingRoute.toArgb()),
        )
        (style.getLayer(ACCURACY_HALO_LAYER_ID) as? FillLayer)?.setProperties(
            fillColor(colors.accuracyHalo.toArgb()),
            fillOutlineColor(colors.accuracyHaloOutline.toArgb()),
        )
        (style.getLayer(DESTINATION_LAYER_ID) as? CircleLayer)?.setProperties(
            circleColor(colors.destinationMarker.toArgb()),
            circleStrokeColor(colors.destinationMarkerStroke.toArgb()),
        )
        (style.getLayer(USER_HEADING_LAYER_ID) as? CircleLayer)?.setProperties(
            circleColor(colors.userPuckHeading.toArgb()),
            circleStrokeColor(colors.userPuckStroke.toArgb()),
            circleOpacity(USER_HEADING_OPACITY),
        )
        (style.getLayer(USER_PUCK_LAYER_ID) as? CircleLayer)?.setProperties(
            circleColor(colors.userPuck.toArgb()),
            circleStrokeColor(colors.userPuckStroke.toArgb()),
        )
    }

    private fun updateMapControlMargins(model: NavigationMapRenderModel) {
        val margin = dpToPx(MAP_CONTROL_MARGIN_DP, model.density)
        val bottomMargin = model.bottomOverlayClearancePx + margin
        map?.uiSettings?.apply {
            setLogoMargins(margin, margin, margin, bottomMargin)
            setAttributionMargins(
                dpToPx(MAP_ATTRIBUTION_START_MARGIN_DP, model.density),
                margin,
                margin,
                bottomMargin,
            )
        }
    }

    private fun updateCamera(
        model: NavigationMapRenderModel,
        remainingRoute: List<GeoPoint>,
        puckLocation: GeoPoint?,
    ) {
        val modeChanged = model.cameraMode != lastCameraMode
        when (model.cameraMode) {
            NavigationCameraMode.FOLLOW -> updateFollowCamera(
                model = model,
                puckLocation = puckLocation,
                force = modeChanged,
            )
            NavigationCameraMode.FREE -> {
                if (modeChanged) {
                    resetCameraPadding(model)
                }
                lastFollowTarget = null
                lastFollowBearing = null
            }
            NavigationCameraMode.OVERVIEW -> {
                val routeChanged = lastOverviewRouteId != model.activeRoute.id
                if (modeChanged || routeChanged) {
                    fitRemainingRoute(model, remainingRoute, puckLocation)
                }
                lastFollowTarget = null
                lastFollowBearing = null
            }
        }
        lastCameraMode = model.cameraMode
        if (model.cameraMode != NavigationCameraMode.OVERVIEW) {
            lastOverviewRouteId = null
        }
    }

    private fun updateFollowCamera(
        model: NavigationMapRenderModel,
        puckLocation: GeoPoint?,
        force: Boolean,
    ) {
        val target = puckLocation?.takeIf(GeoPoint::isRenderable) ?: return
        val previousTarget = lastFollowTarget
        val movedEnough = previousTarget == null ||
            previousTarget.distanceTo(target) >= MINIMUM_FOLLOW_MOVEMENT_METERS
        val bearingChanged = bearingDelta(lastFollowBearing, model.bearingDegrees) >=
            MINIMUM_BEARING_CHANGE_DEGREES
        if (!force && !movedEnough && !bearingChanged) return

        val requestVersion = ++cameraRequestVersion
        mapView.post {
            if (disposed || requestVersion != cameraRequestVersion) return@post
            val currentModel = latestModel ?: return@post
            if (
                currentModel.cameraMode != NavigationCameraMode.FOLLOW ||
                currentModel.activeRoute.id != model.activeRoute.id
            ) {
                return@post
            }
            val readyMap = map ?: return@post
            val currentPuck = currentModel.matchedLocation?.takeIf(GeoPoint::isRenderable)
                ?: currentModel.rawLocation?.takeIf(GeoPoint::isRenderable)
                ?: return@post
            val currentBearing = currentModel.bearingDegrees
            val horizontalPadding = dpToPx(FOLLOW_HORIZONTAL_PADDING_DP, currentModel.density)
            val followOffset = dpToPx(FOLLOW_BELOW_CENTER_OFFSET_DP, currentModel.density)
            val cameraBuilder = CameraPosition.Builder(readyMap.cameraPosition)
                .target(currentPuck.toLatLng())
                .zoom(FOLLOW_ZOOM)
                .tilt(if (currentBearing == null) FOLLOW_FLAT_TILT else FOLLOW_TILT)
                .padding(
                    horizontalPadding.toDouble(),
                    (currentModel.topOverlayClearancePx + followOffset).toDouble(),
                    horizontalPadding.toDouble(),
                    currentModel.bottomOverlayClearancePx.toDouble(),
                )
            if (currentBearing != null) {
                cameraBuilder.bearing(currentBearing.toDouble())
            }
            readyMap.easeCamera(
                CameraUpdateFactory.newCameraPosition(cameraBuilder.build()),
                FOLLOW_CAMERA_ANIMATION_DURATION_MS,
                false,
            )
            lastFollowTarget = currentPuck
            lastFollowBearing = currentBearing
        }
    }

    private fun resetCameraPadding(model: NavigationMapRenderModel) {
        val requestVersion = ++cameraRequestVersion
        mapView.post {
            if (disposed || requestVersion != cameraRequestVersion) return@post
            val currentModel = latestModel ?: return@post
            if (currentModel.cameraMode != NavigationCameraMode.FREE) return@post
            val horizontalPadding = dpToPx(FREE_CAMERA_HORIZONTAL_PADDING_DP, model.density)
            map?.moveCamera(
                CameraUpdateFactory.paddingTo(
                    horizontalPadding.toDouble(),
                    currentModel.topOverlayClearancePx.toDouble(),
                    horizontalPadding.toDouble(),
                    currentModel.bottomOverlayClearancePx.toDouble(),
                ),
            )
        }
    }

    private fun fitRemainingRoute(
        model: NavigationMapRenderModel,
        remainingRoute: List<GeoPoint>,
        puckLocation: GeoPoint?,
    ) {
        val points = buildList {
            addAll(remainingRoute.filter(GeoPoint::isRenderable))
            puckLocation?.takeIf(GeoPoint::isRenderable)?.let(::add)
            model.destination.takeIf(GeoPoint::isRenderable)?.let(::add)
        }.distinctBy { point -> point.latitude to point.longitude }
        if (points.isEmpty()) return

        val requestVersion = ++cameraRequestVersion
        mapView.post {
            if (disposed || requestVersion != cameraRequestVersion) return@post
            val currentModel = latestModel ?: return@post
            if (
                currentModel.cameraMode != NavigationCameraMode.OVERVIEW ||
                currentModel.activeRoute.id != model.activeRoute.id
            ) {
                return@post
            }
            val readyMap = map ?: return@post
            val horizontalPadding = min(
                dpToPx(OVERVIEW_HORIZONTAL_PADDING_DP, currentModel.density),
                mapView.width / OVERVIEW_HORIZONTAL_PADDING_DIVISOR,
            )
            val topPadding = min(
                currentModel.topOverlayClearancePx +
                    dpToPx(OVERVIEW_EXTRA_PADDING_DP, currentModel.density),
                mapView.height / OVERVIEW_VERTICAL_PADDING_DIVISOR,
            )
            val bottomPadding = min(
                currentModel.bottomOverlayClearancePx +
                    dpToPx(OVERVIEW_EXTRA_PADDING_DP, currentModel.density),
                mapView.height / OVERVIEW_VERTICAL_PADDING_DIVISOR,
            )
            val update = if (points.size == 1) {
                CameraUpdateFactory.newLatLngZoom(points.first().toLatLng(), OVERVIEW_SINGLE_POINT_ZOOM)
            } else {
                val bounds = LatLngBounds.Builder()
                    .includes(points.map(GeoPoint::toLatLng))
                    .build()
                CameraUpdateFactory.newLatLngBounds(
                    bounds,
                    horizontalPadding,
                    topPadding,
                    horizontalPadding,
                    bottomPadding,
                )
            }
            readyMap.easeCamera(update, OVERVIEW_CAMERA_ANIMATION_DURATION_MS)
            lastOverviewRouteId = currentModel.activeRoute.id
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        cameraRequestVersion++

        cameraMoveStartedListener?.let { listener ->
            map?.removeOnCameraMoveStartedListener(listener)
        }
        mapFailureListener?.let(mapView::removeOnDidFailLoadingMapListener)
        cameraMoveStartedListener = null
        mapFailureListener = null
        style = null
        map = null
        latestModel = null
        lastAppliedModel = null
    }

    private fun reportError(onMapError: () -> Unit) {
        if (disposed || errorReported) return
        errorReported = true
        onMapError()
    }
}

private class NavigationMapLifecycleBridge(
    private val mapView: MapView,
) {
    private var started = false
    private var resumed = false
    private var destroyed = false

    fun onStart() {
        if (destroyed || started) return
        mapView.onStart()
        started = true
    }

    fun onResume() {
        if (destroyed || resumed) return
        if (!started) onStart()
        mapView.onResume()
        resumed = true
    }

    fun onPause() {
        if (destroyed || !resumed) return
        mapView.onPause()
        resumed = false
    }

    fun onStop() {
        if (destroyed || !started) return
        if (resumed) onPause()
        mapView.onStop()
        started = false
    }

    fun onLowMemory() {
        if (!destroyed) mapView.onLowMemory()
    }

    fun onDestroy() {
        if (destroyed) return
        if (resumed) onPause()
        if (started) onStop()
        mapView.onDestroy()
        destroyed = true
    }
}

@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
private class NavigationMapLowMemoryCallbacks(
    private val onLowMemory: () -> Unit,
) : ComponentCallbacks2 {
    override fun onConfigurationChanged(newConfig: Configuration) = Unit

    override fun onLowMemory() {
        onLowMemory.invoke()
    }

    override fun onTrimMemory(level: Int) = Unit
}

private data class DisplayRouteSplit(
    val completed: List<GeoPoint>,
    val remaining: List<GeoPoint>,
)

private fun splitRouteForDisplay(
    route: RouteOption,
    travelledDistanceMeters: Double,
): DisplayRouteSplit {
    val points = route.geometry.points.filter(GeoPoint::isRenderable)
    if (points.size < MINIMUM_LINE_POINT_COUNT) {
        return DisplayRouteSplit(emptyList(), emptyList())
    }

    val routeDistance = route.summary.distanceMeters.toDouble().coerceAtLeast(1.0)
    val travelledFraction = (travelledDistanceMeters / routeDistance).coerceIn(0.0, 1.0)
    if (travelledFraction <= 0.0) {
        return DisplayRouteSplit(emptyList(), points)
    }
    if (travelledFraction >= 1.0) {
        return DisplayRouteSplit(points, emptyList())
    }

    val segmentLengths = points.zipWithNext { a, b -> a.distanceTo(b) }
    val visibleLength = segmentLengths.sum()
    if (visibleLength <= 0.0 || !visibleLength.isFinite()) {
        return DisplayRouteSplit(emptyList(), points)
    }
    val splitDistance = visibleLength * travelledFraction
    var accumulatedDistance = 0.0

    segmentLengths.forEachIndexed { index, segmentLength ->
        val segmentEnd = accumulatedDistance + segmentLength
        if (splitDistance <= segmentEnd && segmentLength > 0.0) {
            val fractionAlongSegment =
                ((splitDistance - accumulatedDistance) / segmentLength).coerceIn(0.0, 1.0)
            val splitPoint = interpolate(
                start = points[index],
                end = points[index + 1],
                fraction = fractionAlongSegment,
            )
            val completedPrefix = points.take(index + 1)
            val remainingSuffix = points.drop(index + 1)
            return DisplayRouteSplit(
                completed = if (completedPrefix.lastOrNull() == splitPoint) {
                    completedPrefix
                } else {
                    completedPrefix + splitPoint
                },
                remaining = if (remainingSuffix.firstOrNull() == splitPoint) {
                    remainingSuffix
                } else {
                    listOf(splitPoint) + remainingSuffix
                },
            )
        }
        accumulatedDistance = segmentEnd
    }

    return DisplayRouteSplit(points, emptyList())
}

private fun lineFeatureCollection(points: List<GeoPoint>): FeatureCollection {
    val renderablePoints = points
        .filter(GeoPoint::isRenderable)
        .map(GeoPoint::toNavigationGeoJsonPoint)
    return if (renderablePoints.size >= MINIMUM_LINE_POINT_COUNT) {
        FeatureCollection.fromFeature(
            Feature.fromGeometry(LineString.fromLngLats(renderablePoints)),
        )
    } else {
        FeatureCollection.fromFeatures(emptyArray<Feature>())
    }
}

private fun navigationPointFeatureCollection(point: GeoPoint?): FeatureCollection =
    if (point?.isRenderable() == true) {
        FeatureCollection.fromFeature(
            Feature.fromGeometry(point.toNavigationGeoJsonPoint()),
        )
    } else {
        FeatureCollection.fromFeatures(emptyArray<Feature>())
    }

private fun accuracyHaloFeatureCollection(
    center: GeoPoint?,
    accuracyMeters: Double?,
): FeatureCollection {
    val renderableCenter = center?.takeIf(GeoPoint::isRenderable)
    val radiusMeters = accuracyMeters
        ?.takeIf { it > 0.0 }
        ?: return FeatureCollection.fromFeatures(emptyArray<Feature>())
    renderableCenter ?: return FeatureCollection.fromFeatures(emptyArray<Feature>())

    val ring = buildList {
        for (index in 0..ACCURACY_HALO_SEGMENTS) {
            val bearing = 360.0 * index / ACCURACY_HALO_SEGMENTS
            add(
                renderableCenter
                    .pointAtDistanceAndBearing(radiusMeters, bearing)
                    .toNavigationGeoJsonPoint(),
            )
        }
    }
    return FeatureCollection.fromFeature(
        Feature.fromGeometry(Polygon.fromLngLats(listOf(ring))),
    )
}

private fun interpolate(
    start: GeoPoint,
    end: GeoPoint,
    fraction: Double,
): GeoPoint {
    val startLatRad = Math.toRadians(start.latitude)
    val startLonRad = Math.toRadians(start.longitude)
    val endLatRad = Math.toRadians(end.latitude)
    val endLonRad = Math.toRadians(end.longitude)

    val deltaLat = endLatRad - startLatRad
    val deltaLon = endLonRad - startLonRad

    val haversine = sin(deltaLat / 2.0) * sin(deltaLat / 2.0) +
        cos(startLatRad) * cos(endLatRad) * sin(deltaLon / 2.0) * sin(deltaLon / 2.0)
    val angularDistance = 2.0 * atan2(sqrt(haversine), sqrt(1.0 - haversine))
    if (angularDistance == 0.0) return start

    val ratioA = sin((1.0 - fraction) * angularDistance) / sin(angularDistance)
    val ratioB = sin(fraction * angularDistance) / sin(angularDistance)

    val x = ratioA * cos(startLatRad) * cos(startLonRad) + ratioB * cos(endLatRad) * cos(endLonRad)
    val y = ratioA * cos(startLatRad) * sin(startLonRad) + ratioB * cos(endLatRad) * sin(endLonRad)
    val z = ratioA * sin(startLatRad) + ratioB * sin(endLatRad)

    val resultLatRad = atan2(z, sqrt(x * x + y * y))
    val resultLonRad = atan2(y, x)

    return GeoPoint(
        latitude = Math.toDegrees(resultLatRad),
        longitude = Math.toDegrees(resultLonRad),
    )
}

private fun GeoPoint.pointAtDistanceAndBearing(
    distanceMeters: Double,
    bearingDegrees: Double,
): GeoPoint {
    val angularDistance = distanceMeters / EARTH_RADIUS_METERS
    val bearingRad = Math.toRadians(bearingDegrees)
    val startLatRad = Math.toRadians(latitude)
    val startLonRad = Math.toRadians(longitude)

    val endLatRad = asin(
        sin(startLatRad) * cos(angularDistance) +
            cos(startLatRad) * sin(angularDistance) * cos(bearingRad),
    )
    val endLonRad = startLonRad + atan2(
        sin(bearingRad) * sin(angularDistance) * cos(startLatRad),
        cos(angularDistance) - sin(startLatRad) * sin(endLatRad),
    )

    return GeoPoint(
        latitude = Math.toDegrees(endLatRad),
        longitude = Math.toDegrees(endLonRad),
    )
}

private fun GeoPoint.distanceTo(other: GeoPoint): Double =
    GeoMath.distanceMeters(this, other)

private fun Double.normalisedBearing(): Double {
    val normalised = this % 360.0
    return if (normalised < 0.0) normalised + 360.0 else normalised
}

private fun bearingDelta(
    previous: Double?,
    current: Double?,
): Double {
    if (previous == null || current == null) return 360.0
    val diff = (current - previous).normalisedBearing()
    return if (diff > 180.0) 360.0 - diff else diff
}

private fun Style.navigationGeoJsonSource(id: String): GeoJsonSource =
    requireNotNull(getSource(id) as? GeoJsonSource) {
        "Expected GeoJSON source $id to exist in the loaded navigation map style."
    }

private fun GeoPoint.toNavigationGeoJsonPoint(): Point = Point.fromLngLat(longitude, latitude)

private fun GeoPoint.toLatLng(): LatLng = LatLng(latitude, longitude)

private fun GeoPoint.isRenderable(): Boolean =
    latitude.isFinite() &&
        longitude.isFinite() &&
        latitude in -90.0..90.0 &&
        longitude in -180.0..180.0

private fun dpToPx(dp: Int, density: Float): Int = (dp * density).toInt()

private const val FLOOD_SOURCE_ID = "gathra-flood-source"
private const val FLOOD_FILL_LAYER_ID = "gathra-flood-fill"
private const val FLOOD_OUTLINE_LAYER_ID = "gathra-flood-outline"
private const val FLOOD_UNCERTAIN_OUTLINE_LAYER_ID = "gathra-flood-uncertain-outline"

private const val COMPLETED_ROUTE_SOURCE_ID = "gathra-nav-completed-route-source"
private const val REMAINING_ROUTE_SOURCE_ID = "gathra-nav-remaining-route-source"
private const val ACCURACY_HALO_SOURCE_ID = "gathra-nav-accuracy-halo-source"
private const val DESTINATION_SOURCE_ID = "gathra-nav-destination-source"
private const val USER_PUCK_SOURCE_ID = "gathra-nav-user-puck-source"
private const val USER_HEADING_SOURCE_ID = "gathra-nav-user-heading-source"

private const val COMPLETED_ROUTE_LAYER_ID = "gathra-nav-completed-route-layer"
private const val REMAINING_ROUTE_OUTLINE_LAYER_ID = "gathra-nav-remaining-route-outline-layer"
private const val REMAINING_ROUTE_LAYER_ID = "gathra-nav-remaining-route-layer"
private const val ACCURACY_HALO_LAYER_ID = "gathra-nav-accuracy-halo-layer"
private const val DESTINATION_LAYER_ID = "gathra-nav-destination-layer"
private const val USER_HEADING_LAYER_ID = "gathra-nav-user-heading-layer"
private const val USER_PUCK_LAYER_ID = "gathra-nav-user-puck-layer"

private const val COMPLETED_ROUTE_WIDTH = 6.0f
private const val COMPLETED_ROUTE_OPACITY = 0.5f
private const val REMAINING_ROUTE_OUTLINE_WIDTH = 11.0f
private const val REMAINING_ROUTE_WIDTH = 8.0f
private const val ACCURACY_HALO_OPACITY = 0.16f
private const val DESTINATION_RADIUS = 9.0f
private const val DESTINATION_STROKE_WIDTH = 3.0f
private const val USER_HEADING_RADIUS = 13.0f
private const val USER_HEADING_OPACITY = 0.45f
private const val USER_HEADING_STROKE_WIDTH = 2.0f
private const val USER_PUCK_RADIUS = 8.0f
private const val USER_PUCK_STROKE_WIDTH = 3.0f

private const val MINIMUM_LINE_POINT_COUNT = 2
private const val ACCURACY_HALO_SEGMENTS = 32
private const val HEADING_INDICATOR_DISTANCE_METERS = 18.0
private const val MINIMUM_FOLLOW_MOVEMENT_METERS = 2.5
private const val MINIMUM_BEARING_CHANGE_DEGREES = 4.0
private const val FOLLOW_ZOOM = 16.5
private const val FOLLOW_TILT = 45.0
private const val FOLLOW_FLAT_TILT = 0.0
private const val OVERVIEW_SINGLE_POINT_ZOOM = 15.0
private const val EARTH_RADIUS_METERS = 6_371_000.0

private const val FOLLOW_CAMERA_ANIMATION_DURATION_MS = 600
private const val OVERVIEW_CAMERA_ANIMATION_DURATION_MS = 700

private const val FOLLOW_HORIZONTAL_PADDING_DP = 24
private const val FOLLOW_BELOW_CENTER_OFFSET_DP = 72
private const val FREE_CAMERA_HORIZONTAL_PADDING_DP = 16
private const val OVERVIEW_HORIZONTAL_PADDING_DP = 40
private const val OVERVIEW_EXTRA_PADDING_DP = 36
private const val OVERVIEW_HORIZONTAL_PADDING_DIVISOR = 5
private const val OVERVIEW_VERTICAL_PADDING_DIVISOR = 3
private const val MAP_CONTROL_MARGIN_DP = 12
private const val MAP_ATTRIBUTION_START_MARGIN_DP = 76
