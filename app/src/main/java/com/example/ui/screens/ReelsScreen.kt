package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.data.model.Reel
import com.example.data.model.UserProfile
import com.example.ui.components.StarMaxMediaImage
import com.example.ui.components.UserAvatar
import com.example.ui.components.VerifiedBadge
import com.example.ui.components.formatCompactCount
import com.example.ui.theme.LocalStarMaxColors
import kotlinx.coroutines.delay

@Composable
fun ReelsScreen(
    currentUserId: String,
    reels: List<Reel>,
    profiles: List<UserProfile>,
    likedIds: Set<String>,
    followingIds: Set<String>,
    isActiveTab: Boolean,
    onLikeToggle: (Reel) -> Unit,
    onOpenComments: (Reel) -> Unit,
    onFollowToggle: (UserProfile) -> Unit,
    onOpenProfile: (String) -> Unit
) {
    if (reels.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No Reels yet. Tap + to publish your first vertical video!",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { reels.size })
    var isMuted by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val reel = reels[page]
            val isPageActive = isActiveTab && pagerState.currentPage == page
            val authorProfile = profiles.firstOrNull { it.userId == reel.userId }
                ?: UserProfile(
                    userId = reel.userId,
                    username = reel.authorUsername,
                    fullName = reel.authorUsername,
                    avatarData = reel.authorAvatar
                )
            ReelPage(
                reel = reel,
                isLiked = reel.id in likedIds,
                isFollowing = reel.userId in followingIds,
                isOwnReel = reel.userId == currentUserId,
                isAuthorVerified = authorProfile.effectiveVerified,
                isPlayingPage = isPageActive,
                isMuted = isMuted,
                onToggleMute = { isMuted = !isMuted },
                onLikeToggle = { onLikeToggle(reel) },
                onCommentClick = { onOpenComments(reel) },
                onFollowClick = { onFollowToggle(authorProfile) },
                onAuthorClick = { onOpenProfile(reel.userId) }
            )
        }

        // Top Reels Header + Global Mute Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Reels",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = { isMuted = !isMuted },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .testTag("reel_mute_button")
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = if (isMuted) "Unmute reel" else "Mute reel",
                    tint = Color.White
                )
            }
        }
    }
}

private fun resolveReelUri(context: Context, rawUri: String): Uri? {
    return runCatching {
        if (rawUri.startsWith("raw:")) {
            val name = rawUri.removePrefix("raw:")
            val resId = when (name) {
                "reel_neon_tokyo" -> R.raw.reel_neon_tokyo
                "reel_amalfi_sunset" -> R.raw.reel_amalfi_sunset
                "reel_alpine_aurora" -> R.raw.reel_alpine_aurora
                else -> R.raw.reel_neon_tokyo
            }
            Uri.parse("android.resource://${context.packageName}/$resId")
        } else {
            rawUri.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
        }
    }.getOrNull()
}

@OptIn(UnstableApi::class)
@Composable
private fun ReelPage(
    reel: Reel,
    isLiked: Boolean,
    isFollowing: Boolean,
    isOwnReel: Boolean,
    isAuthorVerified: Boolean,
    isPlayingPage: Boolean,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onLikeToggle: () -> Unit,
    onCommentClick: () -> Unit,
    onFollowClick: () -> Unit,
    onAuthorClick: () -> Unit
) {
    val context = LocalContext.current
    val extended = LocalStarMaxColors.current
    var userPaused by remember(reel.id) { mutableStateOf(false) }
    var isVideoRendered by remember(reel.id) { mutableStateOf(false) }
    var playbackFailed by remember(reel.id) { mutableStateOf(false) }
    var showPlayPauseFeedback by remember { mutableStateOf(false) }

    val resolvedUri = remember(reel.videoUri) { resolveReelUri(context, reel.videoUri) }

    val exoPlayer = remember(reel.id, resolvedUri) {
        val uri = resolvedUri ?: return@remember null
        runCatching {
            ExoPlayer.Builder(context).build().apply {
                repeatMode = Player.REPEAT_MODE_ONE
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
            }
        }.getOrNull()
    }

    LaunchedEffect(exoPlayer, isPlayingPage, userPaused) {
        exoPlayer?.playWhenReady = isPlayingPage && !userPaused
    }

    LaunchedEffect(exoPlayer, isMuted) {
        exoPlayer?.volume = if (isMuted) 0f else 1f
    }

    LaunchedEffect(showPlayPauseFeedback) {
        if (showPlayPauseFeedback) {
            delay(700)
            showPlayPauseFeedback = false
        }
    }

    DisposableEffect(exoPlayer) {
        val player = exoPlayer
        if (player == null) {
            playbackFailed = true
            return@DisposableEffect onDispose {}
        }
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                isVideoRendered = true
                playbackFailed = false
            }

            override fun onPlayerError(error: PlaybackException) {
                playbackFailed = true
            }
        }
        player.addListener(listener)
        onDispose {
            runCatching {
                player.removeListener(listener)
                player.release()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable {
                userPaused = !userPaused
                showPlayPauseFeedback = true
            }
    ) {
        if (exoPlayer != null && !playbackFailed) {
            AndroidView(
                factory = { ctx ->
                    val inflated = runCatching {
                        LayoutInflater.from(ctx).inflate(R.layout.reel_player_view, null) as? PlayerView
                    }.getOrNull()
                    (inflated ?: PlayerView(ctx).apply { useController = false }).apply {
                        player = exoPlayer
                    }
                },
                update = { view ->
                    if (view.player !== exoPlayer) {
                        view.player = exoPlayer
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (reel.thumbnailData.isNotBlank() && (!isVideoRendered || playbackFailed)) {
            StarMaxMediaImage(
                mediaSource = reel.thumbnailData,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Bottom Scrim
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))
                    )
                )
        )

        // Tap Play/Pause Indicator
        AnimatedVisibility(
            visible = showPlayPauseFeedback || userPaused,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (userPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = if (userPaused) "Paused" else "Playing",
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        // Right-side Action Column
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onLikeToggle,
                    modifier = Modifier.testTag("reel_like_${reel.id}")
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like reel",
                        tint = if (isLiked) extended.heartRed else Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                val count = reel.likesCount.coerceAtLeast(0L)
                Text(
                    text = formatCompactCount(count),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onCommentClick,
                    modifier = Modifier.testTag("reel_comment_${reel.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "Comment on reel",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Text(
                    text = formatCompactCount(reel.commentsCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White
                )
            }

            IconButton(
                onClick = { shareReel(context, reel) },
                modifier = Modifier.testTag("reel_share_${reel.id}")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = "Share reel",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }

            IconButton(onClick = onToggleMute) {
                Icon(
                    imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Toggle audio",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        // Bottom Left Author & Caption
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.78f)
                .padding(start = 16.dp, bottom = 22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(
                    avatarData = reel.authorAvatar,
                    username = reel.authorUsername,
                    size = 36.dp,
                    onClick = onAuthorClick
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = reel.authorUsername,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    modifier = Modifier.clickable(onClick = onAuthorClick)
                )
                if (isAuthorVerified) {
                    Spacer(modifier = Modifier.width(4.dp))
                    VerifiedBadge(size = 15.dp)
                }
                if (!isOwnReel) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Surface(
                        color = if (isFollowing) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White),
                        modifier = Modifier.clickable { onFollowClick() }
                    ) {
                        Text(
                            text = if (isFollowing) "Following" else "Follow",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            if (reel.caption.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = reel.caption,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = Color.Black.copy(alpha = 0.45f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = reel.audioTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun shareReel(context: Context, reel: Reel) {
    runCatching {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Watch @${reel.authorUsername}'s Reel on Star Max: ${reel.caption}")
        }
        context.startActivity(Intent.createChooser(intent, "Share Reel"))
    }
}
