package opsi.sman35jkt.gathra.ui.theme

import androidx.compose.ui.graphics.Color

// Official GATHRA identity colors. Keep these values centralized so screens
// consume Material roles instead of repeating brand literals.
internal val GathraBlue = Color(0xFF44B5F8)
internal val GathraOrange = Color(0xFFFF751F)
internal val GathraWhite = Color(0xFFFFFFFF)
internal val OnGathraBlue = Color(0xFF00344F)
internal val OnGathraOrange = Color(0xFF4A1700)

// Material tones used for identity text/icons on light surfaces. The supplied
// brand colors remain the primary dark-theme and map/asset colors; these tones
// preserve the same hue family while meeting normal text contrast in light UI.
internal val GathraBlueLight = Color(0xFF006C9C)
internal val GathraOrangeLight = Color(0xFFB84300)

internal val GathraBlueContainerLight = Color(0xFFC8EAFE)
internal val OnGathraBlueContainerLight = Color(0xFF001E2D)
internal val GathraOrangeContainerLight = Color(0xFFFFDBC7)
internal val OnGathraOrangeContainerLight = Color(0xFF351000)

internal val GathraBlueContainerDark = Color(0xFF0A4D70)
internal val OnGathraBlueContainerDark = Color(0xFFBDE7FF)
internal val GathraOrangeContainerDark = Color(0xFF71300D)
internal val OnGathraOrangeContainerDark = Color(0xFFFFDBC7)

internal val BackgroundLight = Color(0xFFF7FAFC)
internal val OnBackgroundLight = Color(0xFF182127)
internal val SurfaceLight = Color(0xFFF7FAFC)
internal val SurfaceVariantLight = Color(0xFFDDE7EC)
internal val OnSurfaceVariantLight = Color(0xFF3F4D55)
internal val OutlineLight = Color(0xFF6E7E87)
internal val ErrorLight = Color(0xFFBA1A1A)

internal val BackgroundDark = Color(0xFF101820)
internal val OnBackgroundDark = Color(0xFFE8F2F8)
internal val SurfaceDark = Color(0xFF131F29)
internal val SurfaceVariantDark = Color(0xFF293843)
internal val OnSurfaceVariantDark = Color(0xFFBECED8)
internal val OutlineDark = Color(0xFF8799A5)
internal val ErrorDark = Color(0xFFFFB4AB)

// Warning/status roles remain separate from the GATHRA orange identity color.
internal val AlertAmberLight = Color(0xFF815700)
internal val OnAlertAmberLight = GathraWhite
internal val AlertAmberContainerLight = Color(0xFFFFE1A8)
internal val OnAlertAmberContainerLight = Color(0xFF291800)
internal val AlertAmberDark = Color(0xFFF4BB4C)
internal val OnAlertAmberDark = Color(0xFF402D00)
internal val AlertAmberContainerDark = Color(0xFF604300)
internal val OnAlertAmberContainerDark = Color(0xFFFFE1A8)

// Flood safety indicators deliberately stay independent from brand identity.
internal val FloodLowContainerLight = Color(0xFFD8F3DC)
internal val FloodLowContentLight = Color(0xFF0B3D1B)
internal val FloodMediumContainerLight = Color(0xFFFFE8B0)
internal val FloodMediumContentLight = Color(0xFF4A2C00)
internal val FloodHighContainerLight = Color(0xFFFFDAD6)
internal val FloodHighContentLight = Color(0xFF410002)
internal val FloodBlockedLight = Color(0xFFBA1A1A)
internal val FloodBlockedContentLight = GathraWhite
internal val FloodUnknownContainerLight = Color(0xFFE3E7EA)
internal val FloodUnknownContentLight = Color(0xFF263238)

internal val FloodLowContainerDark = Color(0xFF1B5428)
internal val FloodLowContentDark = Color(0xFFB8F2BE)
internal val FloodMediumContainerDark = Color(0xFF684B00)
internal val FloodMediumContentDark = Color(0xFFFFDEA6)
internal val FloodHighContainerDark = Color(0xFF733532)
internal val FloodHighContentDark = Color(0xFFFFDAD6)
internal val FloodBlockedDark = Color(0xFFFFB4AB)
internal val FloodBlockedContentDark = Color(0xFF690005)
internal val FloodUnknownContainerDark = Color(0xFF36444D)
internal val FloodUnknownContentDark = Color(0xFFDCE5EA)

// Map route/marker tokens use the official colors for identity while retaining
// neutral casing and completed-route treatments for legibility over map tiles.
internal val RouteSelectedLight = GathraBlue
internal val RouteAlternativeLight = GathraOrange
internal val RouteCasingLight = Color(0xFF0B3E5A)
internal val OriginMarkerLight = GathraBlue
internal val DestinationMarkerLight = GathraOrange
internal val PendingMarkerLight = GathraOrange
internal val RouteCompletedLight = Color(0xFF687D89)
internal val NavigationPuckLight = GathraBlue
internal val NavigationAccuracyLight = Color(0x6644B5F8)
internal val NavigationRawLocationLight = GathraOrange

internal val RouteSelectedDark = GathraBlue
internal val RouteAlternativeDark = GathraOrange
internal val RouteCasingDark = Color(0xFF073149)
internal val OriginMarkerDark = GathraBlue
internal val DestinationMarkerDark = GathraOrange
internal val PendingMarkerDark = GathraOrange
internal val RouteCompletedDark = Color(0xFF718692)
internal val NavigationPuckDark = GathraBlue
internal val NavigationAccuracyDark = Color(0x6644B5F8)
internal val NavigationRawLocationDark = GathraOrange
