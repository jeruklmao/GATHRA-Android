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
import opsi.sman35jkt.gathra.core.model.FloodHazardSnapshot
import opsi.sman35jkt.gathra.core.model.GeoBounds
import opsi.sman35jkt.gathra.core.model.GeoPoint
import opsi.sman35jkt.gathra.core.model.RouteOption
import kotlin.math.min
import kotlin.math.roundToInt

@Immutable
data class RouteMapColors(
    val selectedRoute: Color,
    val selectedRouteOutline: Color,
    val alternativeRoute: Color,
    val originMarker: Color,
    val destinationMarker: Color,
    val pendingMarker: Color,
    val markerStroke: Color,
)

@Composable
fun MapLibreRouteMap(
    origin: GeoPoint?,
    destination: GeoPoint?,
    pendingPoint: GeoPoint?,
    routes: List<RouteOption>,
    selectedRouteId: String?,
    selectionEnabled: Boolean,
    bottomOverlayClearance: Dp,
    colors: RouteMapColors,
    floodSnapshot: FloodHazardSnapshot? = null,
    isFloodLayerVisible: Boolean = true,
    onMapTap: (GeoPoint) -> Unit,
    onFloodHazardSelected: (String) -> Unit = {},
    onViewportSettled: (GeoBounds) -> Unit = {},
    onMapError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnMapTap by rememberUpdatedState(onMapTap)
    val latestOnFloodHazardSelected by rememberUpdatedState(onFloodHazardSelected)
    val latestOnViewportSettled by rememberUpdatedState(onViewportSettled)
    val latestOnMapError by rememberUpdatedState(onMapError)
    val latestSelectionEnabled by rememberUpdatedState(selectionEnabled)

    val mapView = remember(context, lifecycleOwner) {
        createMapViewOrNull(context)
    }

    if (mapView == null) {
        LaunchedEffect(Unit) {
            latestOnMapError()
        }
        Box(modifier = modifier)
        return
    }

    val renderer = remember(mapView) {
        MapRouteRenderer(
            mapView = mapView,
            density = density,
        )
    }

    DisposableEffect(mapView, lifecycleOwner) {
        val lifecycleBridge = MapViewLifecycleBridge(mapView)
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
        val lowMemoryCallbacks = MapLowMemoryCallbacks(lifecycleBridge::onLowMemory)

        renderer.attach(
            isSelectionEnabled = { latestSelectionEnabled },
            onMapTap = { latestOnMapTap },
            onFloodHazardSelected = { latestOnFloodHazardSelected },
            onViewportSettled = { latestOnViewportSettled },
            onMapError = { latestOnMapError },
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
                MapRenderModel(
                    origin = origin,
                    destination = destination,
                    pendingPoint = pendingPoint,
                    routes = routes,
                    selectedRouteId = selectedRouteId,
                    bottomOverlayClearanceDp = bottomOverlayClearance.value.roundToInt(),
                    colors = colors,
                    floodSnapshot = floodSnapshot,
                    isFloodLayerVisible = isFloodLayerVisible,
                ),
            )
        },
    )
}

private fun createMapViewOrNull(context: Context): MapView? =
    runCatching {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).also { mapView ->
            mapView.onCreate(null)
        }
    }.getOrNull()

private data class MapRenderModel(
    val origin: GeoPoint?,
    val destination: GeoPoint?,
    val pendingPoint: GeoPoint?,
    val routes: List<RouteOption>,
    val selectedRouteId: String?,
    val bottomOverlayClearanceDp: Int,
    val colors: RouteMapColors,
    val floodSnapshot: FloodHazardSnapshot?,
    val isFloodLayerVisible: Boolean,
)

private class MapRouteRenderer(
    private val mapView: MapView,
    private val density: Float,
) {
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var latestModel: MapRenderModel? = null
    private var mapClickListener: MapLibreMap.OnMapClickListener? = null
    private var cameraIdleListener: MapLibreMap.OnCameraIdleListener? = null
    private var mapFailureListener: MapView.OnDidFailLoadingMapListener? = null
    private var lastCameraGeometryKey: String? = null
    private var pendingCameraGeometryKey: String? = null
    private var lastStandaloneOriginKey: String? = null
    private var disposed = false
    private var errorReported = false

    fun attach(
        isSelectionEnabled: () -> Boolean,
        onMapTap: () -> (GeoPoint) -> Unit,
        onFloodHazardSelected: () -> (String) -> Unit,
        onViewportSettled: () -> (GeoBounds) -> Unit,
        onMapError: () -> () -> Unit,
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
                        MapStyleConfig.INITIAL_JAKARTA_LATITUDE,
                        MapStyleConfig.INITIAL_JAKARTA_LONGITUDE,
                    ),
                )
                .zoom(MapStyleConfig.INITIAL_ZOOM)
                .build()

            val clickListener = MapLibreMap.OnMapClickListener { coordinate ->
                if (isSelectionEnabled()) {
                    onMapTap().invoke(
                        GeoPoint(
                            latitude = coordinate.latitude,
                            longitude = coordinate.longitude,
                        ),
                    )
                    return@OnMapClickListener true
                }

                val pixel = readyMap.projection.toScreenLocation(coordinate)
                val floodFeatures = readyMap.queryRenderedFeatures(pixel, FLOOD_FILL_LAYER_ID)

                if (floodFeatures.isNotEmpty()) {
                    val hazardId = floodFeatures.first().id()
                    if (!hazardId.isNullOrEmpty()) {
                        onFloodHazardSelected().invoke(hazardId)
                        return@OnMapClickListener true
                    }
                }

                false
            }
            mapClickListener = clickListener
            readyMap.addOnMapClickListener(clickListener)

            val idleListener = MapLibreMap.OnCameraIdleListener {
                val bounds = readyMap.projection.visibleRegion.latLngBounds
                onViewportSettled().invoke(
                    GeoBounds(
                        minLat = bounds.latitudeNorth.coerceAtMost(bounds.latitudeSouth),
                        minLon = bounds.longitudeWest,
                        maxLat = bounds.latitudeNorth.coerceAtGreater(bounds.latitudeSouth),
                        maxLon = bounds.longitudeEast,
                    ),
                )
            }
            cameraIdleListener = idleListener
            readyMap.addOnCameraIdleListener(idleListener)

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

    fun render(model: MapRenderModel) {
        if (disposed) return
        latestModel = model
        if (style != null) {
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
        style.addSource(GeoJsonSource(ALTERNATIVE_ROUTE_SOURCE_ID, emptyFeatures))
        style.addSource(GeoJsonSource(SELECTED_ROUTE_SOURCE_ID, emptyFeatures))
        style.addSource(GeoJsonSource(ORIGIN_SOURCE_ID, emptyFeatures))
        style.addSource(GeoJsonSource(DESTINATION_SOURCE_ID, emptyFeatures))
        style.addSource(GeoJsonSource(PENDING_SOURCE_ID, emptyFeatures))

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
            LineLayer(ALTERNATIVE_ROUTE_LAYER_ID, ALTERNATIVE_ROUTE_SOURCE_ID).withProperties(
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
                lineWidth(ALTERNATIVE_ROUTE_WIDTH),
                lineOpacity(ALTERNATIVE_ROUTE_OPACITY),
                lineDasharray(arrayOf(1.25f, 1.5f)),
            ),
            FLOOD_UNCERTAIN_OUTLINE_LAYER_ID,
        )
        style.addLayerAbove(
            LineLayer(SELECTED_ROUTE_OUTLINE_LAYER_ID, SELECTED_ROUTE_SOURCE_ID).withProperties(
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
                lineWidth(SELECTED_ROUTE_OUTLINE_WIDTH),
            ),
            ALTERNATIVE_ROUTE_LAYER_ID,
        )
        style.addLayerAbove(
            LineLayer(SELECTED_ROUTE_LAYER_ID, SELECTED_ROUTE_SOURCE_ID).withProperties(
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
                lineWidth(SELECTED_ROUTE_WIDTH),
            ),
            SELECTED_ROUTE_OUTLINE_LAYER_ID,
        )
        style.addLayerAbove(
            markerLayer(ORIGIN_LAYER_ID, ORIGIN_SOURCE_ID, MARKER_RADIUS),
            SELECTED_ROUTE_LAYER_ID,
        )
        style.addLayerAbove(
            markerLayer(DESTINATION_LAYER_ID, DESTINATION_SOURCE_ID, MARKER_RADIUS),
            ORIGIN_LAYER_ID,
        )
        style.addLayerAbove(
            markerLayer(PENDING_LAYER_ID, PENDING_SOURCE_ID, PENDING_MARKER_RADIUS).withProperties(
                circleOpacity(PENDING_MARKER_OPACITY),
            ),
            DESTINATION_LAYER_ID,
        )
    }

    private fun markerLayer(
        layerId: String,
        sourceId: String,
        radius: Float,
    ) = CircleLayer(layerId, sourceId).withProperties(
        circleRadius(radius),
        circleStrokeWidth(MARKER_STROKE_WIDTH),
    )

    private fun applyModel(model: MapRenderModel) {
        val loadedStyle = style ?: return
        val selectedRoute = model.routes.firstOrNull { it.id == model.selectedRouteId }
            ?: model.routes.firstOrNull()
        val alternativeRoutes = model.routes.filterNot { it.id == selectedRoute?.id }

        if (model.isFloodLayerVisible && model.floodSnapshot != null) {
            loadedStyle.geoJsonSource(FLOOD_SOURCE_ID).setGeoJson(
                floodFeatureCollection(model.floodSnapshot),
            )
        } else {
            loadedStyle.geoJsonSource(FLOOD_SOURCE_ID).setGeoJson(
                FeatureCollection.fromFeatures(emptyArray<Feature>()),
            )
        }

        loadedStyle.geoJsonSource(ALTERNATIVE_ROUTE_SOURCE_ID).setGeoJson(
            routeFeatureCollection(alternativeRoutes),
        )
        loadedStyle.geoJsonSource(SELECTED_ROUTE_SOURCE_ID).setGeoJson(
            routeFeatureCollection(listOfNotNull(selectedRoute)),
        )
        loadedStyle.geoJsonSource(ORIGIN_SOURCE_ID).setGeoJson(
            pointFeatureCollection(model.origin),
        )
        loadedStyle.geoJsonSource(DESTINATION_SOURCE_ID).setGeoJson(
            pointFeatureCollection(model.destination),
        )
        loadedStyle.geoJsonSource(PENDING_SOURCE_ID).setGeoJson(
            pointFeatureCollection(model.pendingPoint),
        )

        updateLayerColors(loadedStyle, model.colors)
        updateMapControlMargins(model.bottomOverlayClearanceDp)
        updateCamera(model)
    }

    private fun updateMapControlMargins(bottomOverlayClearanceDp: Int) {
        val bottomMargin = dpToPx(
            bottomOverlayClearanceDp.coerceAtLeast(0) + MAP_CONTROL_MARGIN_DP,
        )
        val compactMargin = dpToPx(MAP_CONTROL_MARGIN_DP)
        map?.uiSettings?.apply {
            setLogoMargins(
                compactMargin,
                compactMargin,
                compactMargin,
                bottomMargin,
            )
            setAttributionMargins(
                dpToPx(MAP_ATTRIBUTION_START_MARGIN_DP),
                compactMargin,
                compactMargin,
                bottomMargin,
            )
        }
    }

    private fun updateCamera(model: MapRenderModel) {
        if (model.routes.isNotEmpty()) {
            lastStandaloneOriginKey = null
            fitCameraIfRouteChanged(model.routes)
        } else if (model.destination == null) {
            lastCameraGeometryKey = null
            pendingCameraGeometryKey = null
            centerStandaloneOriginIfChanged(model.origin)
        }
    }

    private fun centerStandaloneOriginIfChanged(origin: GeoPoint?) {
        val renderableOrigin = origin?.takeIf(GeoPoint::isRenderable) ?: return
        val originKey = "${renderableOrigin.latitude},${renderableOrigin.longitude}"
        if (originKey == lastStandaloneOriginKey) return

        mapView.post {
            if (disposed) return@post
            val currentModel = latestModel ?: return@post
            val currentOrigin = currentModel.origin?.takeIf(GeoPoint::isRenderable)
                ?: return@post
            val currentKey = "${currentOrigin.latitude},${currentOrigin.longitude}"
            if (
                currentKey != originKey ||
                currentModel.destination != null ||
                currentModel.routes.isNotEmpty()
            ) {
                return@post
            }
            val readyMap = map ?: return@post
            val target = currentOrigin.toLatLng()
            val cameraUpdate = CameraUpdateFactory.newLatLngZoom(
                target,
                STANDALONE_ORIGIN_ZOOM,
            )
            val currentTarget = readyMap.cameraPosition.target
            val shouldMoveImmediately =
                lastStandaloneOriginKey == null ||
                    currentTarget == null ||
                    currentTarget.distanceTo(target) >
                    FAR_CAMERA_JUMP_METERS

            if (shouldMoveImmediately) {
                readyMap.moveCamera(cameraUpdate)
            } else {
                readyMap.animateCamera(
                    cameraUpdate,
                    CAMERA_ANIMATION_DURATION_MS,
                )
            }
            lastStandaloneOriginKey = originKey
        }
    }

    private fun updateLayerColors(
        style: Style,
        colors: RouteMapColors,
    ) {
        (style.getLayer(ALTERNATIVE_ROUTE_LAYER_ID) as? LineLayer)?.setProperties(
            lineColor(colors.alternativeRoute.toArgb()),
        )
        (style.getLayer(SELECTED_ROUTE_OUTLINE_LAYER_ID) as? LineLayer)?.setProperties(
            lineColor(colors.selectedRouteOutline.toArgb()),
        )
        (style.getLayer(SELECTED_ROUTE_LAYER_ID) as? LineLayer)?.setProperties(
            lineColor(colors.selectedRoute.toArgb()),
        )
        (style.getLayer(ORIGIN_LAYER_ID) as? CircleLayer)?.setProperties(
            circleColor(colors.originMarker.toArgb()),
            circleStrokeColor(colors.markerStroke.toArgb()),
        )
        (style.getLayer(DESTINATION_LAYER_ID) as? CircleLayer)?.setProperties(
            circleColor(colors.destinationMarker.toArgb()),
            circleStrokeColor(colors.markerStroke.toArgb()),
        )
        (style.getLayer(PENDING_LAYER_ID) as? CircleLayer)?.setProperties(
            circleColor(colors.pendingMarker.toArgb()),
            circleStrokeColor(colors.markerStroke.toArgb()),
        )
    }

    private fun fitCameraIfRouteChanged(routes: List<RouteOption>) {
        val routePoints = routes.flatMap { route ->
            route.geometry.points.filter(GeoPoint::isRenderable)
        }
        val geometryKey = routes.cameraGeometryKey()

        if (geometryKey == null) {
            lastCameraGeometryKey = null
            pendingCameraGeometryKey = null
            return
        }
        if (
            geometryKey == lastCameraGeometryKey ||
            geometryKey == pendingCameraGeometryKey ||
            routePoints.isEmpty()
        ) {
            return
        }

        pendingCameraGeometryKey = geometryKey
        mapView.post {
            if (disposed) return@post

            val currentRoutes = latestModel?.routes.orEmpty()
            val currentKey = currentRoutes.cameraGeometryKey()
            if (currentKey != geometryKey) {
                pendingCameraGeometryKey = null
                fitCameraIfRouteChanged(currentRoutes)
                return@post
            }

            val currentPoints = currentRoutes
                .flatMap { route -> route.geometry.points }
                .filter(GeoPoint::isRenderable)
                .distinctBy { point -> point.latitude to point.longitude }
            val readyMap = map
            if (readyMap == null || currentPoints.isEmpty()) {
                pendingCameraGeometryKey = null
                return@post
            }

            runCatching {
                val update = if (currentPoints.size == 1) {
                    CameraUpdateFactory.newLatLngZoom(currentPoints.first().toLatLng(), SINGLE_POINT_ZOOM)
                } else {
                    val bounds = LatLngBounds.Builder()
                        .includes(currentPoints.map(GeoPoint::toLatLng))
                        .build()
                    val horizontalPadding = min(
                        dpToPx(CAMERA_HORIZONTAL_PADDING_DP),
                        mapView.width / CAMERA_HORIZONTAL_PADDING_DIVISOR,
                    )
                    val topPadding = min(
                        dpToPx(CAMERA_TOP_PADDING_DP),
                        mapView.height / CAMERA_VERTICAL_PADDING_DIVISOR,
                    )
                    val bottomPadding = min(
                        dpToPx(CAMERA_BOTTOM_PADDING_DP),
                        mapView.height / CAMERA_VERTICAL_PADDING_DIVISOR,
                    )
                    CameraUpdateFactory.newLatLngBounds(
                        bounds,
                        horizontalPadding,
                        topPadding,
                        horizontalPadding,
                        bottomPadding,
                    )
                }
                readyMap.easeCamera(update, CAMERA_ANIMATION_DURATION_MS)
                lastCameraGeometryKey = geometryKey
            }
            pendingCameraGeometryKey = null
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true

        mapClickListener?.let { listener ->
            map?.removeOnMapClickListener(listener)
        }
        cameraIdleListener?.let { listener ->
            map?.removeOnCameraIdleListener(listener)
        }
        mapFailureListener?.let(mapView::removeOnDidFailLoadingMapListener)
        mapClickListener = null
        cameraIdleListener = null
        mapFailureListener = null
        style = null
        map = null
        latestModel = null
    }

    private fun reportError(onMapError: () -> () -> Unit) {
        if (disposed || errorReported) return
        errorReported = true
        onMapError().invoke()
    }

    private fun dpToPx(dp: Int): Int = (dp * density).toInt()
}

private class MapViewLifecycleBridge(
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
private class MapLowMemoryCallbacks(
    private val onLowMemory: () -> Unit,
) : ComponentCallbacks2 {
    override fun onConfigurationChanged(newConfig: Configuration) = Unit

    override fun onLowMemory() {
        onLowMemory.invoke()
    }

    override fun onTrimMemory(level: Int) = Unit
}

private fun Style.geoJsonSource(id: String): GeoJsonSource =
    requireNotNull(getSource(id) as? GeoJsonSource) {
        "Expected GeoJSON source $id to exist in the loaded map style."
    }

private fun routeFeatureCollection(routes: List<RouteOption>): FeatureCollection {
    val features = routes.mapNotNull { route ->
        val points = route.geometry.points
            .filter(GeoPoint::isRenderable)
            .map(GeoPoint::toGeoJsonPoint)
        if (points.size < MINIMUM_LINE_POINT_COUNT) {
            null
        } else {
            Feature.fromGeometry(LineString.fromLngLats(points))
        }
    }
    return FeatureCollection.fromFeatures(features.toTypedArray())
}

private fun pointFeatureCollection(point: GeoPoint?): FeatureCollection =
    if (point?.isRenderable() == true) {
        FeatureCollection.fromFeature(Feature.fromGeometry(point.toGeoJsonPoint()))
    } else {
        FeatureCollection.fromFeatures(emptyArray<Feature>())
    }

private fun Double.coerceAtMost(maxVal: Double): Double = if (this > maxVal) maxVal else this
private fun Double.coerceAtGreater(minVal: Double): Double = if (this < minVal) minVal else this

private fun GeoPoint.toGeoJsonPoint(): Point = Point.fromLngLat(longitude, latitude)

private fun GeoPoint.toLatLng(): LatLng = LatLng(latitude, longitude)

private fun GeoPoint.isRenderable(): Boolean =
    latitude.isFinite() &&
        longitude.isFinite() &&
        latitude in -90.0..90.0 &&
        longitude in -180.0..180.0

private fun List<RouteOption>.cameraGeometryKey(): String? {
    if (isEmpty()) return null
    return joinToString(separator = "|") { route ->
        buildString {
            append(route.id)
            append(':')
            route.geometry.points.forEach { point ->
                append(point.latitude)
                append(',')
                append(point.longitude)
                append(';')
            }
        }
    }
}

private const val FLOOD_SOURCE_ID = "gathra-flood-source"
private const val FLOOD_FILL_LAYER_ID = "gathra-flood-fill"
private const val FLOOD_OUTLINE_LAYER_ID = "gathra-flood-outline"
private const val FLOOD_UNCERTAIN_OUTLINE_LAYER_ID = "gathra-flood-uncertain-outline"

private const val ALTERNATIVE_ROUTE_SOURCE_ID = "gathra-route-alternative-source"
private const val SELECTED_ROUTE_SOURCE_ID = "gathra-route-selected-source"
private const val ORIGIN_SOURCE_ID = "gathra-origin-source"
private const val DESTINATION_SOURCE_ID = "gathra-destination-source"
private const val PENDING_SOURCE_ID = "gathra-pending-point-source"

private const val ALTERNATIVE_ROUTE_LAYER_ID = "gathra-route-alternative-layer"
private const val SELECTED_ROUTE_OUTLINE_LAYER_ID = "gathra-route-selected-outline-layer"
private const val SELECTED_ROUTE_LAYER_ID = "gathra-route-selected-layer"
private const val ORIGIN_LAYER_ID = "gathra-origin-layer"
private const val DESTINATION_LAYER_ID = "gathra-destination-layer"
private const val PENDING_LAYER_ID = "gathra-pending-point-layer"

private const val ALTERNATIVE_ROUTE_WIDTH = 4.0f
private const val ALTERNATIVE_ROUTE_OPACITY = 0.72f
private const val SELECTED_ROUTE_OUTLINE_WIDTH = 9.0f
private const val SELECTED_ROUTE_WIDTH = 6.0f
private const val MARKER_RADIUS = 8.0f
private const val PENDING_MARKER_RADIUS = 10.0f
private const val PENDING_MARKER_OPACITY = 0.82f
private const val MARKER_STROKE_WIDTH = 3.0f

private const val MINIMUM_LINE_POINT_COUNT = 2
private const val SINGLE_POINT_ZOOM = 15.0
private const val STANDALONE_ORIGIN_ZOOM = 14.0
private const val FAR_CAMERA_JUMP_METERS = 100_000.0
private const val CAMERA_ANIMATION_DURATION_MS = 700
private const val CAMERA_HORIZONTAL_PADDING_DP = 48
private const val CAMERA_TOP_PADDING_DP = 196
private const val CAMERA_BOTTOM_PADDING_DP = 248
private const val CAMERA_HORIZONTAL_PADDING_DIVISOR = 5
private const val CAMERA_VERTICAL_PADDING_DIVISOR = 3
private const val MAP_CONTROL_MARGIN_DP = 12
private const val MAP_ATTRIBUTION_START_MARGIN_DP = 76
