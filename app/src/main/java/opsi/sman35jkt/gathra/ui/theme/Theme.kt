package opsi.sman35jkt.gathra.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Immutable
data class GathraMapColors(
    val selectedRoute: Color,
    val alternativeRoute: Color,
    val routeCasing: Color,
    val originMarker: Color,
    val destinationMarker: Color,
    val pendingMarker: Color,
    val completedRoute: Color,
    val navigationPuck: Color,
    val navigationAccuracy: Color,
    val navigationRawLocation: Color,
)

@Immutable
data class GathraFloodColors(
    val lowContainer: Color,
    val lowContent: Color,
    val mediumContainer: Color,
    val mediumContent: Color,
    val highContainer: Color,
    val highContent: Color,
    val blockedContainer: Color,
    val blockedContent: Color,
    val unknownContainer: Color,
    val unknownContent: Color,
)

private val LightMapColors = GathraMapColors(
    selectedRoute = RouteSelectedLight,
    alternativeRoute = RouteAlternativeLight,
    routeCasing = RouteCasingLight,
    originMarker = OriginMarkerLight,
    destinationMarker = DestinationMarkerLight,
    pendingMarker = PendingMarkerLight,
    completedRoute = RouteCompletedLight,
    navigationPuck = NavigationPuckLight,
    navigationAccuracy = NavigationAccuracyLight,
    navigationRawLocation = NavigationRawLocationLight,
)

private val DarkMapColors = GathraMapColors(
    selectedRoute = RouteSelectedDark,
    alternativeRoute = RouteAlternativeDark,
    routeCasing = RouteCasingDark,
    originMarker = OriginMarkerDark,
    destinationMarker = DestinationMarkerDark,
    pendingMarker = PendingMarkerDark,
    completedRoute = RouteCompletedDark,
    navigationPuck = NavigationPuckDark,
    navigationAccuracy = NavigationAccuracyDark,
    navigationRawLocation = NavigationRawLocationDark,
)

private val LightFloodColors = GathraFloodColors(
    lowContainer = FloodLowContainerLight,
    lowContent = FloodLowContentLight,
    mediumContainer = FloodMediumContainerLight,
    mediumContent = FloodMediumContentLight,
    highContainer = FloodHighContainerLight,
    highContent = FloodHighContentLight,
    blockedContainer = FloodBlockedLight,
    blockedContent = FloodBlockedContentLight,
    unknownContainer = FloodUnknownContainerLight,
    unknownContent = FloodUnknownContentLight,
)

private val DarkFloodColors = GathraFloodColors(
    lowContainer = FloodLowContainerDark,
    lowContent = FloodLowContentDark,
    mediumContainer = FloodMediumContainerDark,
    mediumContent = FloodMediumContentDark,
    highContainer = FloodHighContainerDark,
    highContent = FloodHighContentDark,
    blockedContainer = FloodBlockedDark,
    blockedContent = FloodBlockedContentDark,
    unknownContainer = FloodUnknownContainerDark,
    unknownContent = FloodUnknownContentDark,
)

private val LocalGathraMapColors = staticCompositionLocalOf { LightMapColors }
private val LocalGathraFloodColors = staticCompositionLocalOf { LightFloodColors }

private val DarkColorScheme = darkColorScheme(
    primary = GathraBlue,
    onPrimary = OnGathraBlue,
    primaryContainer = GathraBlueContainerDark,
    onPrimaryContainer = OnGathraBlueContainerDark,
    secondary = GathraOrange,
    onSecondary = OnGathraOrange,
    secondaryContainer = GathraOrangeContainerDark,
    onSecondaryContainer = OnGathraOrangeContainerDark,
    tertiary = AlertAmberDark,
    onTertiary = OnAlertAmberDark,
    tertiaryContainer = AlertAmberContainerDark,
    onTertiaryContainer = OnAlertAmberContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnBackgroundDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    error = ErrorDark,
)

private val LightColorScheme = lightColorScheme(
    primary = GathraBlueLight,
    onPrimary = GathraWhite,
    primaryContainer = GathraBlueContainerLight,
    onPrimaryContainer = OnGathraBlueContainerLight,
    secondary = GathraOrangeLight,
    onSecondary = GathraWhite,
    secondaryContainer = GathraOrangeContainerLight,
    onSecondaryContainer = OnGathraOrangeContainerLight,
    tertiary = AlertAmberLight,
    onTertiary = OnAlertAmberLight,
    tertiaryContainer = AlertAmberContainerLight,
    onTertiaryContainer = OnAlertAmberContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnBackgroundLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    error = ErrorLight,
)

object GathraTheme {
    val mapColors: GathraMapColors
        @Composable
        @ReadOnlyComposable
        get() = LocalGathraMapColors.current

    val floodColors: GathraFloodColors
        @Composable
        @ReadOnlyComposable
        get() = LocalGathraFloodColors.current
}

@Composable
fun GATHRATheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalGathraMapColors provides if (darkTheme) DarkMapColors else LightMapColors,
        LocalGathraFloodColors provides if (darkTheme) DarkFloodColors else LightFloodColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = Typography,
        ) {
            GathraSystemBars(darkTheme)
            content()
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun GathraSystemBars(darkTheme: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return

    SideEffect {
        val activity = view.context.findActivity() ?: return@SideEffect
        val window = activity.window
        val barColor = if (darkTheme) SurfaceDark else SurfaceLight
        window.statusBarColor = barColor.toArgb()
        window.navigationBarColor = barColor.toArgb()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
