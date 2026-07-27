package opsi.sman35jkt.gathra.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

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

private val LocalGathraMapColors = staticCompositionLocalOf { LightMapColors }

private val DarkColorScheme = darkColorScheme(
    primary = ViridianDark,
    onPrimary = OnViridianDark,
    primaryContainer = ViridianContainerDark,
    onPrimaryContainer = OnViridianContainerDark,
    secondary = VioletDark,
    onSecondary = OnVioletDark,
    secondaryContainer = VioletContainerDark,
    onSecondaryContainer = OnVioletContainerDark,
    tertiary = AmberDark,
    onTertiary = OnAmberDark,
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = OnAmberContainerDark,
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
    primary = ViridianLight,
    onPrimary = OnViridianLight,
    primaryContainer = ViridianContainerLight,
    onPrimaryContainer = OnViridianContainerLight,
    secondary = VioletLight,
    onSecondary = OnVioletLight,
    secondaryContainer = VioletContainerLight,
    onSecondaryContainer = OnVioletContainerLight,
    tertiary = AmberLight,
    onTertiary = OnAmberLight,
    tertiaryContainer = AmberContainerLight,
    onTertiaryContainer = OnAmberContainerLight,
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
}

@Composable
fun GATHRATheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalGathraMapColors provides if (darkTheme) DarkMapColors else LightMapColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = Typography,
            content = content,
        )
    }
}
