package com.lagradost.cloudstream3.ui.netflix

import android.widget.ImageView
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage

/**
 * Pure Netflix Dark Theme Color Tokens
 */
object NetflixColors {
    val DeepBlack = Color(0xFF141414)
    val SurfaceBlack = Color(0xFF181818)
    val CardElevated = Color(0xFF232323)
    val SignatureRed = Color(0xFFE50914)
    val PureWhite = Color(0xFFFFFFFF)
    val CoolLightGrey = Color(0xFFE5E5E5)
    val MediumGrey = Color(0xFFA3A3A3)
    val DarkGrey = Color(0xFF404040)
    val FocusBorder = Color(0xFFFFFFFF)

    val BillboardGradient = Brush.verticalGradient(
        0.0f to Color.Transparent,
        0.55f to Color(0x33141414),
        0.85f to Color(0xCC141414),
        1.0f to DeepBlack
    )
}

val NetflixTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp,
        color = NetflixColors.PureWhite
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        color = NetflixColors.PureWhite
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        color = NetflixColors.PureWhite
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = NetflixColors.CoolLightGrey
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp,
        color = NetflixColors.MediumGrey
    )
)

private val NetflixDarkColorScheme = darkColorScheme(
    primary = NetflixColors.SignatureRed,
    onPrimary = NetflixColors.PureWhite,
    background = NetflixColors.DeepBlack,
    onBackground = NetflixColors.PureWhite,
    surface = NetflixColors.SurfaceBlack,
    onSurface = NetflixColors.PureWhite,
    surfaceVariant = NetflixColors.CardElevated,
    onSurfaceVariant = NetflixColors.CoolLightGrey
)

@Composable
fun NetflixAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NetflixDarkColorScheme,
        typography = NetflixTypography,
        content = content
    )
}

@Composable
fun NetflixRemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    scaleType: ImageView.ScaleType = ImageView.ScaleType.CENTER_CROP
) {
    AndroidView(
        factory = { ctx ->
            ImageView(ctx).apply {
                this.scaleType = scaleType
                loadImage(url)
            }
        },
        update = { imageView ->
            imageView.loadImage(url)
        },
        modifier = modifier
    )
}

// -------------------------------------------------------------
// 1. Profile Selection Screen ("Who's Watching?")
// -------------------------------------------------------------

data class NetflixProfile(
    val id: String,
    val name: String,
    val avatarUrl: String,
    val isPinProtected: Boolean = false
)

@Composable
fun WhoIsWatchingScreen(
    profiles: List<NetflixProfile>,
    isTv: Boolean,
    onProfileSelected: (NetflixProfile) -> Unit,
    onAddProfile: () -> Unit,
    onManageProfiles: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NetflixColors.DeepBlack)
            .padding(horizontal = if (isTv) 56.dp else 24.dp, vertical = 32.dp)
    ) {
        if (!isTv) {
            IconButton(
                onClick = onManageProfiles,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_baseline_edit_24),
                    contentDescription = "Manage Profiles",
                    tint = NetflixColors.CoolLightGrey
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Who's Watching?",
                style = if (isTv) NetflixTypography.headlineLarge.copy(fontSize = 42.sp)
                        else NetflixTypography.headlineMedium,
                color = NetflixColors.PureWhite,
                modifier = Modifier.padding(bottom = if (isTv) 48.dp else 32.dp)
            )

            val columns = if (isTv) 5 else 2
            val cardSize = if (isTv) 130.dp else 110.dp

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                horizontalArrangement = Arrangement.spacedBy(if (isTv) 32.dp else 20.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(if (isTv) 32.dp else 24.dp),
                modifier = Modifier.wrapContentSize()
            ) {
                items(profiles) { profile ->
                    NetflixProfileCard(
                        profile = profile,
                        size = cardSize,
                        isTv = isTv,
                        onClick = { onProfileSelected(profile) }
                    )
                }

                item {
                    NetflixAddProfileCard(
                        size = cardSize,
                        isTv = isTv,
                        onClick = onAddProfile
                    )
                }
            }
        }
    }
}

@Composable
fun NetflixProfileCard(
    profile: NetflixProfile,
    size: Dp,
    isTv: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isTv && isFocused) 1.15f else 1.0f, label = "profileScale")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale)
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = if (isTv && isFocused) 3.dp else 0.dp,
                    color = if (isTv && isFocused) NetflixColors.PureWhite else Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                )
        ) {
            NetflixRemoteImage(
                url = profile.avatarUrl,
                contentDescription = profile.name,
                modifier = Modifier.fillMaxSize()
            )

            if (profile.isPinProtected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x40000000))
                        .padding(6.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.video_locked),
                        contentDescription = "PIN Protected",
                        tint = NetflixColors.PureWhite,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = profile.name,
            style = NetflixTypography.titleMedium,
            color = if (isTv && isFocused) NetflixColors.PureWhite else NetflixColors.MediumGrey
        )
    }
}

@Composable
fun NetflixAddProfileCard(
    size: Dp,
    isTv: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isTv && isFocused) 1.15f else 1.0f, label = "addScale")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale)
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = if (isTv && isFocused) 3.dp else 1.5.dp,
                    color = if (isTv && isFocused) NetflixColors.PureWhite else NetflixColors.DarkGrey,
                    shape = RoundedCornerShape(8.dp)
                )
                .background(NetflixColors.SurfaceBlack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_baseline_add_24),
                contentDescription = "Add Profile",
                tint = if (isTv && isFocused) NetflixColors.PureWhite else NetflixColors.MediumGrey,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Add Profile",
            style = NetflixTypography.titleMedium,
            color = if (isTv && isFocused) NetflixColors.PureWhite else NetflixColors.MediumGrey
        )
    }
}

// -------------------------------------------------------------
// 2. Hero Billboard & Horizontal Content Rails
// -------------------------------------------------------------

data class NetflixMediaCard(
    val id: String,
    val title: String,
    val posterUrl: String,
    val backdropUrl: String? = null,
    val synopsis: String = "",
    val genres: List<String> = emptyList(),
    val isTop10: Boolean = false
)

data class NetflixRail(
    val title: String,
    val items: List<NetflixMediaCard>
)

@Composable
fun NetflixHeroBillboard(
    media: NetflixMediaCard,
    isTv: Boolean,
    onPlayClicked: () -> Unit,
    onDetailsClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (isTv) 500.dp else 420.dp)
    ) {
        NetflixRemoteImage(
            url = media.backdropUrl ?: media.posterUrl,
            contentDescription = media.title,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NetflixColors.BillboardGradient)
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = if (isTv) 56.dp else 20.dp,
                    end = 20.dp,
                    bottom = 24.dp
                )
                .widthIn(max = if (isTv) 600.dp else 360.dp)
        ) {
            if (media.isTop10) {
                Surface(
                    color = NetflixColors.SignatureRed,
                    shape = RoundedCornerShape(2.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(
                        text = "TOP 10",
                        style = NetflixTypography.labelSmall,
                        color = NetflixColors.PureWhite,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = media.title,
                style = if (isTv) NetflixTypography.headlineLarge else NetflixTypography.headlineMedium,
                color = NetflixColors.PureWhite
            )

            if (media.genres.isNotEmpty()) {
                Text(
                    text = media.genres.joinToString(" • "),
                    style = NetflixTypography.labelSmall,
                    color = NetflixColors.CoolLightGrey,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            if (isTv && media.synopsis.isNotEmpty()) {
                Text(
                    text = media.synopsis,
                    style = NetflixTypography.bodyMedium,
                    maxLines = 3,
                    color = NetflixColors.CoolLightGrey,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onPlayClicked,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NetflixColors.PureWhite,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_play_arrow_24),
                        contentDescription = null,
                        tint = Color.Black
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Play", style = NetflixTypography.titleMedium.copy(color = Color.Black))
                }

                Button(
                    onClick = onDetailsClicked,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0x66545454),
                        contentColor = NetflixColors.PureWhite
                    ),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_outline_info_24),
                        contentDescription = null,
                        tint = NetflixColors.PureWhite
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "More Info", style = NetflixTypography.titleMedium)
                }
            }
        }
    }
}

@Composable
fun NetflixContentRail(
    title: String,
    items: List<NetflixMediaCard>,
    isTv: Boolean,
    onItemClick: (NetflixMediaCard) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = NetflixTypography.titleMedium.copy(fontSize = if (isTv) 20.sp else 16.sp),
            color = NetflixColors.PureWhite,
            modifier = Modifier.padding(horizontal = if (isTv) 56.dp else 16.dp, vertical = 8.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = if (isTv) 56.dp else 16.dp),
            horizontalArrangement = Arrangement.spacedBy(if (isTv) 16.dp else 10.dp)
        ) {
            items(items) { item ->
                NetflixPosterCard(
                    item = item,
                    isTv = isTv,
                    onClick = { onItemClick(item) }
                )
            }
        }
    }
}

@Composable
fun NetflixPosterCard(
    item: NetflixMediaCard,
    isTv: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isTv && isFocused) 1.12f else 1.0f, label = "posterScale")

    Box(
        modifier = Modifier
            .width(if (isTv) 150.dp else 115.dp)
            .aspectRatio(2f / 3f)
            .scale(scale)
            .clip(RoundedCornerShape(6.dp))
            .border(
                width = if (isTv && isFocused) 2.5.dp else 0.dp,
                color = if (isTv && isFocused) NetflixColors.PureWhite else Color.Transparent,
                shape = RoundedCornerShape(6.dp)
            )
            .background(NetflixColors.SurfaceBlack)
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        NetflixRemoteImage(
            url = item.posterUrl,
            contentDescription = item.title,
            modifier = Modifier.fillMaxSize()
        )
    }
}

// -------------------------------------------------------------
// 3. Android TV Left Collapsible Navigation Rail
// -------------------------------------------------------------

enum class NetflixTvNavDestination(val label: String, val iconRes: Int) {
    Search("Search", R.drawable.search_icon),
    Home("Home", R.drawable.ic_outline_home_24),
    Settings("Settings", R.drawable.ic_outline_settings_24)
}

@Composable
fun NetflixTvNavigationRail(
    selectedDestination: NetflixTvNavDestination,
    onDestinationSelected: (NetflixTvNavDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val animatedWidth by animateDpAsState(targetValue = if (isExpanded) 220.dp else 68.dp, label = "navWidth")

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(animatedWidth)
            .background(Color(0xE6141414))
            .padding(vertical = 24.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            NetflixTvNavDestination.values().filter { it != NetflixTvNavDestination.Settings }.forEach { destination ->
                NetflixTvNavItem(
                    destination = destination,
                    isSelected = destination == selectedDestination,
                    isExpanded = isExpanded,
                    onFocusChanged = { if (it) isExpanded = true },
                    onClick = {
                        onDestinationSelected(destination)
                        isExpanded = false
                    }
                )
            }
        }

        NetflixTvNavItem(
            destination = NetflixTvNavDestination.Settings,
            isSelected = selectedDestination == NetflixTvNavDestination.Settings,
            isExpanded = isExpanded,
            onFocusChanged = { if (it) isExpanded = true },
            onClick = {
                onDestinationSelected(NetflixTvNavDestination.Settings)
                isExpanded = false
            }
        )
    }
}

@Composable
fun NetflixTvNavItem(
    destination: NetflixTvNavDestination,
    isSelected: Boolean,
    isExpanded: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(isFocused) {
        onFocusChanged(isFocused)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isFocused -> NetflixColors.CardElevated
                    isSelected -> Color(0x33E50914)
                    else -> Color.Transparent
                }
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) NetflixColors.PureWhite else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp)
    ) {
        Icon(
            painter = painterResource(id = destination.iconRes),
            contentDescription = destination.label,
            tint = when {
                isFocused -> NetflixColors.PureWhite
                isSelected -> NetflixColors.SignatureRed
                else -> NetflixColors.MediumGrey
            },
            modifier = Modifier.size(24.dp)
        )

        if (isExpanded) {
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = destination.label,
                style = NetflixTypography.titleMedium,
                color = if (isFocused || isSelected) NetflixColors.PureWhite else NetflixColors.MediumGrey,
                maxLines = 1
            )
        }
    }
}
