package opsi.sman35jkt.gathra.feature.geocoding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import opsi.sman35jkt.gathra.core.model.GeoPoint
import opsi.sman35jkt.gathra.core.model.PlaceCategory
import opsi.sman35jkt.gathra.core.model.PlaceSuggestion
import opsi.sman35jkt.gathra.ui.theme.GATHRATheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlaceSearchScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun supportedResultCanBeSelectedButOutsideResultIsDisabled() {
        val actions = mutableListOf<PlaceSearchAction>()
        val inside = suggestion("inside", true)
        val outside = suggestion("outside", false)
        setScreen(
            state = PlaceSearchUiState(
                isVisible = true,
                query = "Monas",
                status = PlaceSearchStatus.RESULTS,
                suggestions = listOf(inside, outside),
            ),
            onAction = actions::add,
        )

        composeRule.onNodeWithTag(
            PlaceSearchTestTags.suggestion(outside.id),
        ).assertIsNotEnabled()
        composeRule.onNodeWithText("Di luar wilayah layanan GATHRA")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(
            PlaceSearchTestTags.suggestion(inside.id),
        ).performClick()

        assertEquals(
            PlaceSearchAction.SuggestionSelected(inside.id),
            actions.last(),
        )
    }

    @Test
    fun currentLocationAndMapFallbackActionsRemainAvailable() {
        val actions = mutableListOf<PlaceSearchAction>()
        setScreen(onAction = actions::add)

        composeRule.onNodeWithTag(PlaceSearchTestTags.CurrentLocation)
            .performClick()
        composeRule.onNodeWithTag(PlaceSearchTestTags.ChooseOnMap)
            .performClick()

        assertEquals(
            listOf(
                PlaceSearchAction.CurrentLocationSelected,
                PlaceSearchAction.ChooseOnMap,
            ),
            actions,
        )
    }

    @Test
    fun emptyStateIsVisible() {
        setScreen(
            state = PlaceSearchUiState(
                isVisible = true,
                query = "Tidak ada",
                status = PlaceSearchStatus.EMPTY,
            ),
        )
        composeRule.onNodeWithTag(PlaceSearchTestTags.Empty)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Lokasi tidak ditemukan")
            .assertIsDisplayed()
    }

    @Test
    fun retryActionIsAvailableForServerError() {
        var retries = 0
        setScreen(
            state = PlaceSearchUiState(
                isVisible = true,
                query = "Monas",
                status = PlaceSearchStatus.ERROR,
                error = PlaceSearchError.SERVICE_UNAVAILABLE,
            ),
            onAction = {
                if (it == PlaceSearchAction.Retry) retries += 1
            },
        )
        composeRule.onNodeWithTag(PlaceSearchTestTags.Retry).performClick()
        assertEquals(1, retries)
    }

    private fun setScreen(
        state: PlaceSearchUiState = PlaceSearchUiState(isVisible = true),
        onAction: (PlaceSearchAction) -> Unit = {},
    ) {
        composeRule.setContent {
            GATHRATheme {
                PlaceSearchScreen(
                    state = state,
                    onAction = onAction,
                )
            }
        }
    }

    private fun suggestion(
        id: String,
        inside: Boolean,
    ) = PlaceSuggestion(
        id = id,
        primaryText = if (inside) {
            "Monumen Nasional"
        } else {
            "Tempat di luar wilayah"
        },
        secondaryText = "Indonesia",
        category = PlaceCategory.LANDMARK,
        position = GeoPoint(-6.1754, 106.8272),
        distanceMeters = 1_000,
        insideSupportedRegion = inside,
    )
}
