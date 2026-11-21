package com.example.jobly.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jobly.R
import com.example.jobly.data.repository.SampleSeedData
import com.example.jobly.model.CvItem
import com.example.jobly.model.JobOffer
import com.example.jobly.ui.common.CarouselScrollOverlay
import com.example.jobly.ui.common.LeftAccentCard
import com.example.jobly.ui.common.OfferAvatar
import com.example.jobly.ui.common.YellowTopSection
import com.example.jobly.ui.theme.AccentYellow
import com.example.jobly.ui.theme.CanvasGray
import com.example.jobly.ui.theme.CardAccentPalette
import com.example.jobly.ui.theme.JoblyTheme
import com.example.jobly.ui.theme.OnSurface
import com.example.jobly.ui.theme.Outline
import com.example.jobly.ui.theme.SalaryGreen
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    closeOffers: List<JobOffer>,
    bestOffers: List<JobOffer>,
    cvItems: List<CvItem>,
    onOfferClick: (JobOffer) -> Unit,
    onCvClick: (CvItem) -> Unit,
    loadCvThumbnail: (suspend (CvItem) -> ImageBitmap?)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasGray)
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 12.dp)
    ) {
        OfferCarouselSection(
            title = stringResource(R.string.section_next_to_you),
            iconRes = R.drawable.ic_section_location,
            iconWidth = 11.dp,
            iconHeight = 16.dp,
            offers = closeOffers,
            onOfferClick = onOfferClick
        )

        Spacer(modifier = Modifier.height(8.dp))

        OfferCarouselSection(
            title = stringResource(R.string.section_best_for_you),
            iconRes = R.drawable.ic_section_flame,
            iconWidth = 12.dp,
            iconHeight = 15.dp,
            offers = bestOffers,
            onOfferClick = onOfferClick
        )

        Spacer(modifier = Modifier.height(8.dp))

        CvCarouselSection(
            cvItems = cvItems,
            onCvClick = onCvClick,
            loadCvThumbnail = loadCvThumbnail
        )
    }
}

@Composable
private fun OfferCarouselSection(
    title: String,
    iconRes: Int,
    iconWidth: androidx.compose.ui.unit.Dp,
    iconHeight: androidx.compose.ui.unit.Dp,
    offers: List<JobOffer>,
    onOfferClick: (JobOffer) -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val scrollStepPx = with(LocalDensity.current) { 240.dp.toPx() }

    YellowTopSection(
        title = title,
        iconRes = iconRes,
        iconWidth = iconWidth,
        iconHeight = iconHeight
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .height(162.dp)
        ) {
            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(offers, key = { it.id }) { offer ->
                    OfferCard(offer = offer, onClick = { onOfferClick(offer) })
                }
            }

            CarouselScrollOverlay(
                visible = listState.canScrollForward,
                onClick = {
                    coroutineScope.launch { listState.animateScrollBy(scrollStepPx) }
                },
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
private fun CvCarouselSection(
    cvItems: List<CvItem>,
    onCvClick: (CvItem) -> Unit,
    loadCvThumbnail: (suspend (CvItem) -> ImageBitmap?)?
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val scrollStepPx = with(LocalDensity.current) { 240.dp.toPx() }

    YellowTopSection(
        title = stringResource(R.string.section_your_cvs),
        iconRes = R.drawable.ic_section_cv,
        iconWidth = 12.dp,
        iconHeight = 16.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .height(262.dp)
        ) {
            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(cvItems, key = { it.id }) { cv ->
                    CvCard(
                        cv = cv,
                        onClick = { onCvClick(cv) },
                        loadCvThumbnail = loadCvThumbnail
                    )
                }
            }

            CarouselScrollOverlay(
                visible = listState.canScrollForward,
                onClick = {
                    coroutineScope.launch { listState.animateScrollBy(scrollStepPx) }
                },
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
private fun OfferCard(
    offer: JobOffer,
    onClick: () -> Unit
) {
    val palette = CardAccentPalette.forAccent(offer.accent)

    LeftAccentCard(
        accentColor = palette.borderColor,
        width = 303.dp,
        height = 146.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OfferAvatar(initial = offer.initial, backgroundColor = palette.avatarColor)

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = offer.title,
                    color = OnSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = offer.salaryRange,
                        color = SalaryGreen,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 20.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (offer.salaryUnit.isNotBlank()) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = offer.salaryUnit,
                            color = Outline,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            offer.skills.forEach { skill ->
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.chipBrush)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = skill,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = offer.companyName,
            color = Color.Black,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp)
        )

        Spacer(modifier = Modifier.height(2.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, end = 2.dp)
        ) {
            MetadataLabel(offer.level)
            MetadataDot()
            MetadataLabel(offer.contractType)
            MetadataDot()
            MetadataLabel(offer.workSchedule)
            offer.matchScore?.let { score ->
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFE6F4EA))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "$score%",
                        color = SalaryGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun MetadataLabel(text: String) {
    Text(
        text = text,
        color = Color.Black,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 14.sp
    )
}

@Composable
private fun MetadataDot() {
    Image(
        painter = painterResource(R.drawable.ic_meta_dot),
        contentDescription = null,
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .size(6.dp)
    )
}

@Composable
private fun CvCard(
    cv: CvItem,
    onClick: () -> Unit,
    loadCvThumbnail: (suspend (CvItem) -> ImageBitmap?)?
) {
    var thumbnail by remember(cv.id, cv.fileName, cv.updatedLabel, cv.fileSizeBytes, cv.pageCount) {
        mutableStateOf<ImageBitmap?>(null)
    }

    LaunchedEffect(cv.id, cv.fileName, cv.updatedLabel, cv.fileSizeBytes, cv.pageCount) {
        if (loadCvThumbnail != null) {
            thumbnail = loadCvThumbnail(cv)
        }
    }

    LeftAccentCard(
        accentColor = AccentYellow,
        width = 303.dp,
        height = 246.dp,
        onClick = onClick
    ) {
        Text(
            text = cv.fileName,
            color = OnSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
        ) {
            val loadedThumb = thumbnail
            if (loadedThumb != null) {
                Image(
                    bitmap = loadedThumb,
                    contentDescription = "${cv.fileName} preview",
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.img_cv_preview),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.7f,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(2.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    JoblyTheme {
        HomeScreen(
            closeOffers = SampleSeedData.sampleCloseOffers,
            bestOffers = SampleSeedData.sampleBestOffers,
            cvItems = SampleSeedData.sampleCvItems,
            onOfferClick = {},
            onCvClick = {}
        )
    }
}
