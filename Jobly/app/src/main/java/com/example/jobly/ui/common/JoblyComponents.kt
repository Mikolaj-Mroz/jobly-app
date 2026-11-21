package com.example.jobly.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jobly.R
import com.example.jobly.ui.theme.AccentYellow
import com.example.jobly.ui.theme.CanvasGray
import com.example.jobly.ui.theme.CardSurface
import com.example.jobly.ui.theme.OnPrimaryContainer

private val TopSectionShape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
private val CardShape = RoundedCornerShape(12.dp)
private val AvatarShape = RoundedCornerShape(8.dp)
private val ScrimBrush = Brush.horizontalGradient(
    0.0f to Color.Transparent,
    0.35f to Color.Transparent,
    1.0f to Color(0x63000000)
)

@Composable
fun YellowTopSection(
    title: String,
    @DrawableRes iconRes: Int,
    iconWidth: Dp,
    iconHeight: Dp,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(TopSectionShape)
            .background(AccentYellow)
            .padding(top = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TopSectionShape)
                .background(CanvasGray)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(width = iconWidth, height = iconHeight)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    color = Color.Black,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 24.sp
                )
            }
            content()
        }
    }
}

@Composable
fun LeftAccentCard(
    accentColor: Color,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .size(width = width, height = height)
            .shadow(elevation = 3.dp, shape = CardShape)
            .clip(CardShape)
            .background(accentColor)
            .padding(start = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(CardShape)
                .background(CardSurface)
                .clickable(onClick = onClick)
                .padding(8.dp),
            content = content
        )
    }
}

@Composable
fun BottomAccentCard(
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(accentColor)
            .padding(bottom = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(CardSurface)
                .clickable(onClick = onClick),
            content = content
        )
    }
}

@Composable
fun OfferAvatar(
    initial: String,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(AvatarShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = OnPrimaryContainer,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun CarouselScrollOverlay(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    Box(
        modifier = modifier
            .width(93.dp)
            .fillMaxHeight()
            .background(ScrimBrush)
            .clickable(
                onClickLabel = stringResource(R.string.cd_scroll_next),
                onClick = onClick
            ),
        contentAlignment = Alignment.CenterEnd
    ) {
        Image(
            painter = painterResource(R.drawable.ic_carousel_chevron),
            contentDescription = stringResource(R.string.cd_scroll_next),
            modifier = Modifier
                .padding(end = 10.dp)
                .size(width = 12.dp, height = 20.dp)
        )
    }
}
