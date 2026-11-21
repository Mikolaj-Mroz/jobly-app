package com.example.jobly.ui.search

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jobly.R
import com.example.jobly.data.repository.SampleSeedData
import com.example.jobly.data.repository.SearchSort
import com.example.jobly.model.JobOffer
import com.example.jobly.ui.common.OfferAvatar
import com.example.jobly.ui.theme.AccentYellow
import com.example.jobly.ui.theme.CanvasGray
import com.example.jobly.ui.theme.CardAccentPalette
import com.example.jobly.ui.theme.CardSurface
import com.example.jobly.ui.theme.JoblyTheme
import com.example.jobly.ui.theme.OnPrimaryContainer
import com.example.jobly.ui.theme.OnSecondaryContainer
import com.example.jobly.ui.theme.OnSurface
import com.example.jobly.ui.theme.OnSurfaceVariant
import com.example.jobly.ui.theme.Outline
import com.example.jobly.ui.theme.OutlineVariant
import com.example.jobly.ui.theme.SalaryGreen
import com.example.jobly.ui.theme.SecondaryContainer
import com.example.jobly.ui.theme.SurfaceWhite
import com.example.jobly.ui.theme.TopBarDivider

private val WORK_MODES = listOf("remote" to "Remote", "hybrid" to "Hybrid", "office" to "Office")
private val LEVELS = listOf("JUNIOR" to "Junior", "MID" to "Mid", "SENIOR" to "Senior", "MANAGER" to "Manager")
private val CONTRACTS = listOf("B2B" to "B2B", "UoP" to "UoP")
private val SALARY_OPTIONS = listOf(
    null to "Any salary",
    10_000 to "10 000+ PLN",
    15_000 to "15 000+ PLN",
    20_000 to "20 000+ PLN",
    25_000 to "25 000+ PLN"
)
private val SUGGESTED_KEYWORDS = listOf("Python", "Java", "DevOps", "TypeScript", "Kraków", "Warszawa")

@Composable
fun SearchScreen(
    state: SearchUiState,
    appliedOfferIds: Set<String> = emptySet(),
    autoFocus: Boolean = true,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSubmitSearch: (String?) -> Unit,
    onToggleWorkMode: (String) -> Unit,
    onToggleLevel: (String) -> Unit,
    onToggleContract: (String) -> Unit,
    onSelectMinSalary: (Int?) -> Unit,
    onSelectSort: (SearchSort) -> Unit,
    onClearFilters: () -> Unit,
    onClearAll: () -> Unit,
    onRetry: () -> Unit,
    onOfferClick: (JobOffer) -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var showSortMenu by remember { mutableStateOf(false) }
    var showSalaryMenu by remember { mutableStateOf(false) }

    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CanvasGray,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceWhite)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 6.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            keyboardController?.hide()
                            onBack()
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_profile_arrow_right),
                            contentDescription = "Back",
                            modifier = Modifier
                                .size(18.dp)
                                .rotate(180f)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CanvasGray.copy(alpha = 0.7f))
                            .border(1.dp, OutlineVariant, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_search),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicTextField(
                            value = state.query,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(color = OnSurface, fontSize = 15.sp),
                            cursorBrush = SolidColor(OnPrimaryContainer),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    keyboardController?.hide()
                                    onSubmitSearch(null)
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester)
                                .semantics { contentDescription = "Search jobs input" },
                            decorationBox = { innerTextField ->
                                if (state.query.isEmpty()) {
                                    Text(
                                        text = "Search jobs, skills, company, city...",
                                        color = OnSurfaceVariant,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                innerTextField()
                            }
                        )
                        if (state.query.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable { onQueryChange("") }
                                    .semantics { contentDescription = "Clear search" },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "✕",
                                    color = OnSurfaceVariant,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "sort") {
                        Box {
                            SearchFilterChip(
                                label = "Sort: ${state.sort.label} ▾",
                                selected = state.sort != SearchSort.RELEVANCE,
                                onClick = { showSortMenu = true }
                            )
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                SearchSort.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (option == state.sort) "✓ ${option.label}" else option.label,
                                                fontWeight = if (option == state.sort) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            showSortMenu = false
                                            onSelectSort(option)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    items(WORK_MODES, key = { "mode_${it.first}" }) { (value, label) ->
                        SearchFilterChip(
                            label = label,
                            selected = state.selectedWorkMode.equals(value, ignoreCase = true),
                            onClick = { onToggleWorkMode(value) }
                        )
                    }

                    items(LEVELS, key = { "level_${it.first}" }) { (value, label) ->
                        SearchFilterChip(
                            label = label,
                            selected = state.selectedLevel.equals(value, ignoreCase = true),
                            onClick = { onToggleLevel(value) }
                        )
                    }

                    items(CONTRACTS, key = { "contract_${it.first}" }) { (value, label) ->
                        SearchFilterChip(
                            label = label,
                            selected = state.selectedContract.equals(value, ignoreCase = true),
                            onClick = { onToggleContract(value) }
                        )
                    }

                    item(key = "salary") {
                        Box {
                            val salaryLabel = state.minSalary?.let { "${it / 1000}k+ PLN" } ?: "Salary ▾"
                            SearchFilterChip(
                                label = salaryLabel,
                                selected = state.minSalary != null,
                                onClick = { showSalaryMenu = true }
                            )
                            DropdownMenu(
                                expanded = showSalaryMenu,
                                onDismissRequest = { showSalaryMenu = false }
                            ) {
                                SALARY_OPTIONS.forEach { (amount, text) ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (amount == state.minSalary) "✓ $text" else text,
                                                fontWeight = if (amount == state.minSalary) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            showSalaryMenu = false
                                            onSelectMinSalary(amount)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (state.activeFilterCount > 0) {
                        item(key = "clear_filters") {
                            Box(
                                modifier = Modifier
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .border(1.dp, OutlineVariant, RoundedCornerShape(999.dp))
                                    .clickable(onClick = onClearFilters)
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Clear filters",
                                    color = OnSurfaceVariant,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(thickness = 0.5.dp, color = TopBarDivider)
                if (state.isLoading) {
                    LinearProgressIndicator(
                        color = AccentYellow,
                        trackColor = Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.query.isBlank()) {
                val quickChips = remember(state.recentQueries) {
                    val combined = state.recentQueries + SUGGESTED_KEYWORDS.filterNot { sug ->
                        state.recentQueries.any { it.equals(sug, ignoreCase = true) }
                    }
                    combined.take(8)
                }
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceWhite.copy(alpha = 0.7f))
                        .padding(vertical = 8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(quickChips, key = { "quick_$it" }) { chip ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(SecondaryContainer.copy(alpha = 0.65f))
                                .clickable {
                                    keyboardController?.hide()
                                    onSubmitSearch(chip)
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = chip,
                                color = OnSecondaryContainer,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val countLabel = if (state.totalCount == 1) "1 job found" else "${state.totalCount} jobs found"
                Text(
                    text = countLabel,
                    color = OnSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Sorted by ${state.sort.label}",
                    color = OnSurfaceVariant,
                    fontSize = 12.sp
                )
            }

            when {
                state.errorMessage != null && state.results.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceWhite)
                                .border(1.dp, OutlineVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = state.errorMessage,
                                color = OnSurface,
                                fontSize = 14.sp
                            )
                            Button(
                                onClick = onRetry,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentYellow,
                                    contentColor = OnPrimaryContainer
                                )
                            ) {
                                Text("Retry", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                !state.isLoading && state.hasSearched && state.results.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceWhite)
                                .border(1.dp, OutlineVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "No matching jobs found",
                                color = OnSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Try broader keywords, fewer filters, or check spelling.",
                                color = OnSurfaceVariant,
                                fontSize = 13.sp
                            )
                            Button(
                                onClick = onClearAll,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentYellow,
                                    contentColor = OnPrimaryContainer
                                )
                            ) {
                                Text("Clear search & filters", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.results, key = { it.id }) { offer ->
                            SearchResultOfferCard(
                                offer = offer,
                                isApplied = offer.id in appliedOfferIds,
                                onClick = { onOfferClick(offer) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) AccentYellow.copy(alpha = 0.25f) else CanvasGray.copy(alpha = 0.7f)
    val borderColor = if (selected) AccentYellow else OutlineVariant
    Box(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = OnSurface,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@Composable
private fun SearchResultOfferCard(
    offer: JobOffer,
    isApplied: Boolean,
    onClick: () -> Unit
) {
    val palette = CardAccentPalette.forAccent(offer.accent)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(CardSurface)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .width(5.dp)
                .fillMaxHeight()
                .background(palette.borderColor)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OfferAvatar(initial = offer.initial, backgroundColor = palette.avatarColor)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = offer.title,
                        color = OnSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 20.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = offer.salaryRange,
                            color = SalaryGreen,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (offer.salaryUnit.isNotBlank()) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = offer.salaryUnit,
                                color = Outline,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = offer.companyName,
                        color = Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${offer.level} • ${offer.contractType} • ${offer.workSchedule}",
                        color = OnSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isApplied) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFE6F4EA))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Applied",
                            color = SalaryGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                offer.matchScore?.let { score ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFE6F4EA))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "$score%",
                            color = SalaryGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            offer.matchReasons.firstOrNull()?.takeIf { it.isNotBlank() }?.let { reason ->
                Text(
                    text = reason,
                    color = OnSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (offer.skills.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    offer.skills.forEach { skill ->
                        Box(
                            modifier = Modifier
                                .height(26.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(palette.chipBrush)
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = skill,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchScreenPreview() {
    JoblyTheme {
        SearchScreen(
            state = SearchUiState(
                query = "Python",
                results = SampleSeedData.sampleBestOffers.mapIndexed { idx, o ->
                    o.copy(matchScore = 92 - idx * 8, matchReasons = listOf("Skills: Kubernetes, Linux"))
                },
                totalCount = 3,
                hasSearched = true
            ),
            autoFocus = false,
            onBack = {},
            onQueryChange = {},
            onSubmitSearch = {},
            onToggleWorkMode = {},
            onToggleLevel = {},
            onToggleContract = {},
            onSelectMinSalary = {},
            onSelectSort = {},
            onClearFilters = {},
            onClearAll = {},
            onRetry = {},
            onOfferClick = {}
        )
    }
}
