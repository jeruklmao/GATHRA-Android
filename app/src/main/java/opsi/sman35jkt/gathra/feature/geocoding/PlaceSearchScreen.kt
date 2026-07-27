package opsi.sman35jkt.gathra.feature.geocoding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import opsi.sman35jkt.gathra.R
import opsi.sman35jkt.gathra.core.model.PlaceSuggestion

@Composable
fun PlaceSearchScreen(
    state: PlaceSearchUiState,
    onAction: (PlaceSearchAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = state.isVisible) {
        onAction(PlaceSearchAction.Dismiss)
    }
    val focusRequester = androidx.compose.runtime.remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.isVisible) {
        if (state.isVisible) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag(PlaceSearchTestTags.Surface),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .windowInsetsPadding(WindowInsets.ime),
        ) {
            SearchField(
                state = state,
                focusRequester = focusRequester,
                onAction = onAction,
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            SearchQuickAction(
                icon = Icons.Rounded.MyLocation,
                title = stringResource(R.string.geocoding_current_location),
                description = stringResource(
                    R.string.geocoding_current_location_description,
                ),
                contentDescription = stringResource(
                    R.string.geocoding_current_location_accessibility,
                ),
                testTag = PlaceSearchTestTags.CurrentLocation,
                onClick = {
                    keyboardController?.hide()
                    onAction(PlaceSearchAction.CurrentLocationSelected)
                },
            )
            SearchQuickAction(
                icon = Icons.Rounded.Map,
                title = stringResource(R.string.geocoding_choose_on_map),
                description = stringResource(
                    R.string.geocoding_choose_on_map_description,
                ),
                contentDescription = stringResource(
                    R.string.geocoding_choose_on_map_accessibility,
                ),
                testTag = PlaceSearchTestTags.ChooseOnMap,
                onClick = {
                    keyboardController?.hide()
                    onAction(PlaceSearchAction.ChooseOnMap)
                },
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            SearchContent(
                state = state,
                onAction = onAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

@Composable
private fun SearchField(
    state: PlaceSearchUiState,
    focusRequester: FocusRequester,
    onAction: (PlaceSearchAction) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onAction(PlaceSearchAction.Dismiss) }) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(
                    R.string.geocoding_close_search,
                ),
            )
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = {
                onAction(PlaceSearchAction.QueryChanged(it))
            },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .testTag(PlaceSearchTestTags.Field),
            placeholder = {
                Text(
                    stringResource(
                        if (state.targetField == SearchTargetField.ORIGIN) {
                            R.string.geocoding_search_origin
                        } else {
                            R.string.geocoding_search_destination
                        },
                    ),
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                )
            },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = { onAction(PlaceSearchAction.Submit) },
            ),
        )
    }
}

@Composable
private fun SearchQuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    contentDescription: String,
    testTag: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
            )
        },
        supportingContent = {
            Text(
                text = description,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            }
            .testTag(testTag),
    )
}

@Composable
private fun SearchContent(
    state: PlaceSearchUiState,
    onAction: (PlaceSearchAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state.status) {
        PlaceSearchStatus.RESULTS -> SuggestionList(
            suggestions = state.suggestions,
            selectedSuggestionId = state.selectedSuggestionId,
            onSelected = {
                onAction(PlaceSearchAction.SuggestionSelected(it))
            },
            modifier = modifier,
        )
        PlaceSearchStatus.LOADING -> SearchCenteredMessage(
            modifier = modifier.testTag(PlaceSearchTestTags.Loading),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(stringResource(R.string.geocoding_searching))
        }
        PlaceSearchStatus.EMPTY -> SearchCenteredMessage(
            modifier = modifier.testTag(PlaceSearchTestTags.Empty),
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.geocoding_no_results),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.geocoding_no_results_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PlaceSearchStatus.ERROR -> SearchError(
            error = state.error ?: PlaceSearchError.UNKNOWN,
            onRetry = { onAction(PlaceSearchAction.Retry) },
            modifier = modifier,
        )
        PlaceSearchStatus.IDLE,
        PlaceSearchStatus.TYPING,
        -> SearchCenteredMessage(modifier = modifier) {
            Text(
                text = if (state.query.isBlank()) {
                    stringResource(R.string.geocoding_search_hint)
                } else {
                    stringResource(R.string.geocoding_keep_typing)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SuggestionList(
    suggestions: List<PlaceSuggestion>,
    selectedSuggestionId: String?,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.testTag(PlaceSearchTestTags.Suggestions),
    ) {
        items(
            items = suggestions,
            key = { it.id },
        ) { suggestion ->
            val enabled = suggestion.insideSupportedRegion &&
                selectedSuggestionId == null
            val accessibility = if (suggestion.insideSupportedRegion) {
                stringResource(
                    R.string.geocoding_suggestion_accessibility,
                    suggestion.primaryText,
                    suggestion.secondaryText.orEmpty(),
                )
            } else {
                stringResource(
                    R.string.geocoding_suggestion_outside_accessibility,
                    suggestion.primaryText,
                )
            }
            ListItem(
                headlineContent = {
                    Text(
                        text = suggestion.primaryText,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    Column {
                        suggestion.secondaryText?.let {
                            Text(
                                text = it,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (!suggestion.insideSupportedRegion) {
                            Text(
                                text = stringResource(
                                    R.string.geocoding_outside_region,
                                ),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Rounded.LocationOn,
                        contentDescription = null,
                        tint = if (suggestion.insideSupportedRegion) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
                trailingContent = {
                    if (selectedSuggestionId == suggestion.id) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                },
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = if (
                            suggestion.insideSupportedRegion
                        ) {
                            1f
                        } else {
                            0.58f
                        }
                    }
                    .clickable(
                        enabled = enabled,
                        onClick = { onSelected(suggestion.id) },
                    )
                    .semantics {
                        contentDescription = accessibility
                        role = Role.Button
                        if (!suggestion.insideSupportedRegion) disabled()
                    }
                    .testTag(
                        PlaceSearchTestTags.suggestion(suggestion.id),
                    ),
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 56.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

@Composable
private fun SearchError(
    error: PlaceSearchError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SearchCenteredMessage(
        modifier = modifier.testTag(PlaceSearchTestTags.Error),
    ) {
        Text(
            text = stringResource(
                when (error) {
                    PlaceSearchError.INVALID_QUERY ->
                        R.string.geocoding_invalid_query
                    PlaceSearchError.OFFLINE ->
                        R.string.geocoding_offline
                    PlaceSearchError.TIMEOUT ->
                        R.string.geocoding_timeout
                    PlaceSearchError.PLACE_NOT_FOUND ->
                        R.string.geocoding_place_not_found
                    PlaceSearchError.OUTSIDE_COVERAGE ->
                        R.string.geocoding_outside_region
                    PlaceSearchError.SERVICE_UNAVAILABLE,
                    PlaceSearchError.INVALID_RESPONSE,
                    PlaceSearchError.UNKNOWN,
                    -> R.string.geocoding_service_unavailable
                },
            ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.geocoding_error_body),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.testTag(PlaceSearchTestTags.Retry),
        ) {
            Text(stringResource(R.string.retry))
        }
    }
}

@Composable
private fun SearchCenteredMessage(
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content,
        )
    }
}
