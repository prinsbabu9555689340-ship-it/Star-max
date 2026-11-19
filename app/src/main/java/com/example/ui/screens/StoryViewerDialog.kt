package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.R
import com.example.data.model.Story
import com.example.data.model.StoryFilter
import com.example.data.model.StoryReaction
import com.example.data.model.StoryReactionType
import com.example.ui.components.StarMaxMediaImage
import com.example.ui.components.UserAvatar
import com.example.ui.components.formatRelativeTime
import com.example.ui.theme.StarMaxAmber
import com.example.ui.theme.StarMaxCoral
import com.example.ui.theme.StarMaxIndigo
import com.example.ui.theme.StarMaxMagenta
import com.example.ui.theme.StarMaxViolet
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private data class FloatingReactionBurst(
    val id: Long,
    val type: StoryReactionType,
    val horizontalOffsetDp: Float
)

private val HeartReactionTint = Color(0xFFFF3366)
private val FireReactionTint = Color(0xFFFF7A18)
private val ClapReactionTint = Color(0xFFFFD166)

private val ClapHandsIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ClapHands",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Spark rays above clapping hands
        path(
            fill = SolidColor(Color.White),
            pathFillType = PathFillType.NonZero
        ) {
            // Left ray
            moveTo(7.4f, 2.2f)
            lineTo(8.8f, 5.1f)
            lineTo(7.3f, 5.8f)
            lineTo(5.9f, 2.9f)
            close()
            // Center ray
            moveTo(11.2f, 1.3f)
            lineTo(12.8f, 1.3f)
            lineTo(12.8f, 4.5f)
            lineTo(11.2f, 4.5f)
            close()
            // Right ray
            moveTo(16.6f, 2.2f)
            lineTo(18.1f, 2.9f)
            lineTo(16.7f, 5.8f)
            lineTo(15.2f, 5.1f)
            close()
        }
        // Back clapping hand silhouette
        path(
            fill = SolidColor(Color.White),
            fillAlpha = 0.72f,
            stroke = null,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(14.2f, 6.2f)
            curveTo(14.8f, 5.6f, 15.8f, 5.7f, 16.3f, 6.3f)
            lineTo(20.6f, 11.2f)
            curveTo(22.0f, 12.8f, 21.8f, 15.3f, 20.1f, 16.8f)
            lineTo(17.9f, 18.8f)
            lineTo(13.1f, 13.4f)
            lineTo(15.7f, 11.1f)
            lineTo(13.8f, 8.9f)
            curveTo(13.2f, 8.2f, 13.4f, 6.9f, 14.2f, 6.2f)
            close()
        }
        // Front clapping hand
        path(
            fill = SolidColor(Color.White),
            stroke = null,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(9.4f, 6.8f)
            curveTo(10.1f, 6.2f, 11.1f, 6.3f, 11.7f, 7.0f)
            lineTo(17.2f, 13.3f)
            curveTo(18.7f, 15.0f, 18.5f, 17.6f, 16.8f, 19.2f)
            lineTo(14.1f, 21.5f)
            curveTo(12.3f, 23.0f, 9.6f, 22.8f, 8.1f, 21.0f)
            lineTo(4.1f, 16.4f)
            curveTo(3.4f, 15.6f, 3.5f, 14.4f, 4.3f, 13.8f)
            curveTo(5.0f, 13.2f, 6.1f, 13.3f, 6.7f, 14.0f)
            lineTo(8.3f, 15.8f)
            lineTo(9.1f, 15.1f)
            lineTo(6.3f, 11.9f)
            curveTo(5.7f, 11.2f, 5.8f, 10.1f, 6.5f, 9.5f)
            curveTo(7.2f, 8.9f, 8.2f, 9.0f, 8.8f, 9.7f)
            lineTo(10.8f, 12.0f)
            lineTo(11.6f, 11.3f)
            lineTo(9.1f, 8.5f)
            curveTo(8.6f, 7.9f, 8.7f, 7.3f, 9.4f, 6.8f)
            close()
        }
    }.build()
}

private fun reactionIconFor(type: StoryReactionType): ImageVector = when (type) {
    StoryReactionType.HEART -> Icons.Filled.Favorite
    StoryReactionType.FIRE -> Icons.Filled.LocalFireDepartment
    StoryReactionType.CLAP -> ClapHandsIcon
}

private fun reactionTintFor(type: StoryReactionType): Color = when (type) {
    StoryReactionType.HEART -> HeartReactionTint
    StoryReactionType.FIRE -> FireReactionTint
    StoryReactionType.CLAP -> ClapReactionTint
}

@Composable
fun StoryViewerDialog(
    stories: List<Story>,
    initialIndex: Int,
    reactions: List<StoryReaction> = emptyList(),
    onStoryChanged: (Story) -> Unit = {},
    onReactToStory: (Story, StoryReactionType) -> Unit = { _, _ -> },
    onDismiss: () -> Unit,
    onSendReply: (Story, String) -> Unit
) {
    if (stories.isEmpty()) {
        onDismiss()
        return
    }
    val context = LocalContext.current
    var currentIndex by remember { mutableIntStateOf(initialIndex.coerceIn(0, stories.lastIndex)) }
    var progress by remember(currentIndex) { mutableFloatStateOf(0f) }
    var isPaused by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("") }
    var burstSeq by remember { mutableLongStateOf(0L) }
    var lastTappedType by remember(currentIndex) { mutableStateOf<StoryReactionType?>(null) }
    var tapPulseTick by remember(currentIndex) { mutableIntStateOf(0) }
    val floatingBursts = remember(currentIndex) { mutableStateListOf<FloatingReactionBurst>() }

    val story = stories[currentIndex]
    val activeFilter = remember(story.filterName) { StoryFilter.fromId(story.filterName) }

    LaunchedEffect(story.id) {
        onStoryChanged(story)
    }

    val reactionCounts = remember(reactions, story.id) {
        val storyOnly = reactions.filter { it.storyId == story.id }
        mapOf(
            StoryReactionType.HEART to storyOnly.count { it.reactionType == StoryReactionType.HEART.wireValue },
            StoryReactionType.FIRE to storyOnly.count { it.reactionType == StoryReactionType.FIRE.wireValue },
            StoryReactionType.CLAP to storyOnly.count { it.reactionType == StoryReactionType.CLAP.wireValue }
        )
    }

    val songPlayer = remember(story.id, story.songUrl) {
        if (story.songUrl.isNotBlank()) {
            runCatching {
                ExoPlayer.Builder(context).build().apply {
                    repeatMode = Player.REPEAT_MODE_ONE
                    setMediaItem(MediaItem.fromUri(story.songUrl))
                    prepare()
                }
            }.getOrNull()
        } else {
            null
        }
    }

    LaunchedEffect(songPlayer, isPaused) {
        songPlayer?.playWhenReady = !isPaused
    }

    LaunchedEffect(songPlayer, isMuted) {
        songPlayer?.volume = if (isMuted) 0f else 1f
    }

    DisposableEffect(songPlayer) {
        val player = songPlayer ?: return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // Keep Story viewable silently if stream cannot load
            }
        }
        player.addListener(listener)
        onDispose {
            runCatching {
                player.removeListener(listener)
                player.stop()
                player.release()
            }
        }
    }

    LaunchedEffect(currentIndex, isPaused) {
        val stepMillis = 50L
        val totalMillis = 5000L
        while (progress < 1f) {
            delay(stepMillis)
            if (!isPaused) {
                progress += stepMillis.toFloat() / totalMillis.toFloat()
            }
        }
        if (currentIndex < stories.lastIndex) {
            currentIndex++
        } else {
            onDismiss()
        }
    }

    val textBrush = when (story.bgGradient) {
        "ocean" -> Brush.linearGradient(listOf(StarMaxIndigo, Color(0xFF00B4D8)))
        "neon" -> Brush.linearGradient(listOf(StarMaxViolet, StarMaxMagenta))
        else -> Brush.linearGradient(listOf(StarMaxMagenta, StarMaxCoral, StarMaxAmber))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Story Content
            if (story.mediaType == "photo" && story.mediaData.isNotBlank()) {
                StarMaxMediaImage(
                    mediaSource = story.mediaData,
                    contentDescription = "Story by ${story.authorUsername}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    colorFilter = activeFilter.toColorFilter()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.5f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.72f)
                                )
                            )
                        )
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(textBrush),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = story.textContent.ifBlank { "✨ Star Max Story" },
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            }

            // Tap zones for Prev / Next / Hold-to-Pause
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .pointerInput(currentIndex) {
                            detectTapGestures(
                                onLongPress = { isPaused = true },
                                onPress = {
                                    tryAwaitRelease()
                                    isPaused = false
                                },
                                onTap = {
                                    if (currentIndex > 0) currentIndex-- else progress = 0f
                                }
                            )
                        }
                )
                Box(
                    modifier = Modifier
                        .weight(2f)
                        .fillMaxSize()
                        .pointerInput(currentIndex) {
                            detectTapGestures(
                                onLongPress = { isPaused = true },
                                onPress = {
                                    tryAwaitRelease()
                                    isPaused = false
                                },
                                onTap = {
                                    if (currentIndex < stories.lastIndex) currentIndex++ else onDismiss()
                                }
                            )
                        }
                )
            }

            // Floating reaction burst animations rising above the bottom bar
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(280.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 132.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                floatingBursts.forEach { burst ->
                    key(burst.id) {
                        FloatingReactionBurstItem(
                            burst = burst,
                            onFinished = {
                                floatingBursts.removeAll { it.id == burst.id }
                            }
                        )
                    }
                }
            }

            // Top Progress & Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    stories.forEachIndexed { index, _ ->
                        val p = when {
                            index < currentIndex -> 1f
                            index == currentIndex -> progress.coerceIn(0f, 1f)
                            else -> 0f
                        }
                        LinearProgressIndicator(
                            progress = { p },
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.35f),
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UserAvatar(
                        avatarData = story.authorAvatar,
                        username = story.authorUsername,
                        size = 34.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = story.authorUsername,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatRelativeTime(story.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (story.songUrl.isNotBlank()) {
                        IconButton(
                            onClick = { isMuted = !isMuted },
                            modifier = Modifier.testTag("story_music_mute_button")
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (isMuted) "Unmute story music" else "Mute story music",
                                tint = Color.White
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("story_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close story",
                            tint = Color.White
                        )
                    }
                }
                if (story.songTitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = Color.Black.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.testTag("story_viewer_music_sticker")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MusicNote,
                                contentDescription = null,
                                tint = StarMaxAmber,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (story.songArtist.isNotBlank()) {
                                    "${story.songTitle} • ${story.songArtist}"
                                } else {
                                    story.songTitle
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Bottom Caption + Story Reactions Bar + Quick Reply
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 14.dp)
            ) {
                if (story.mediaType == "photo" && story.textContent.isNotBlank()) {
                    Text(
                        text = story.textContent,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }

                // Story Reactions Bar (Heart, Fire, Clap)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .testTag("story_reactions_bar"),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StoryReactionPillButton(
                        type = StoryReactionType.HEART,
                        count = reactionCounts[StoryReactionType.HEART] ?: 0,
                        isRecentlyTapped = lastTappedType == StoryReactionType.HEART,
                        pulseTick = tapPulseTick,
                        contentDesc = stringResource(R.string.story_reaction_heart_cd),
                        testTagName = "story_reaction_heart",
                        onClick = {
                            lastTappedType = StoryReactionType.HEART
                            tapPulseTick++
                            burstSeq++
                            floatingBursts.add(
                                FloatingReactionBurst(
                                    id = burstSeq,
                                    type = StoryReactionType.HEART,
                                    horizontalOffsetDp = -76f + ((burstSeq % 3) - 1) * 12f
                                )
                            )
                            onReactToStory(story, StoryReactionType.HEART)
                        }
                    )
                    StoryReactionPillButton(
                        type = StoryReactionType.FIRE,
                        count = reactionCounts[StoryReactionType.FIRE] ?: 0,
                        isRecentlyTapped = lastTappedType == StoryReactionType.FIRE,
                        pulseTick = tapPulseTick,
                        contentDesc = stringResource(R.string.story_reaction_fire_cd),
                        testTagName = "story_reaction_fire",
                        onClick = {
                            lastTappedType = StoryReactionType.FIRE
                            tapPulseTick++
                            burstSeq++
                            floatingBursts.add(
                                FloatingReactionBurst(
                                    id = burstSeq,
                                    type = StoryReactionType.FIRE,
                                    horizontalOffsetDp = ((burstSeq % 3) - 1) * 14f
                                )
                            )
                            onReactToStory(story, StoryReactionType.FIRE)
                        }
                    )
                    StoryReactionPillButton(
                        type = StoryReactionType.CLAP,
                        count = reactionCounts[StoryReactionType.CLAP] ?: 0,
                        isRecentlyTapped = lastTappedType == StoryReactionType.CLAP,
                        pulseTick = tapPulseTick,
                        contentDesc = stringResource(R.string.story_reaction_clap_cd),
                        testTagName = "story_reaction_clap",
                        onClick = {
                            lastTappedType = StoryReactionType.CLAP
                            tapPulseTick++
                            burstSeq++
                            floatingBursts.add(
                                FloatingReactionBurst(
                                    id = burstSeq,
                                    type = StoryReactionType.CLAP,
                                    horizontalOffsetDp = 76f + ((burstSeq % 3) - 1) * 12f
                                )
                            )
                            onReactToStory(story, StoryReactionType.CLAP)
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = {
                            replyText = it
                            isPaused = it.isNotEmpty()
                        },
                        placeholder = {
                            Text("Send message to ${story.authorUsername}...", color = Color.White.copy(alpha = 0.7f))
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(26.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("story_reply_input")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            val trimmed = replyText.trim()
                            if (trimmed.isNotEmpty()) {
                                onSendReply(story, trimmed)
                                replyText = ""
                                isPaused = false
                            }
                        },
                        modifier = Modifier.testTag("story_reply_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Reply to story",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StoryReactionPillButton(
    type: StoryReactionType,
    count: Int,
    isRecentlyTapped: Boolean,
    pulseTick: Int,
    contentDesc: String,
    testTagName: String,
    onClick: () -> Unit
) {
    var popped by remember { mutableStateOf(false) }
    LaunchedEffect(isRecentlyTapped, pulseTick) {
        if (isRecentlyTapped && pulseTick > 0) {
            popped = true
            delay(160L)
            popped = false
        }
    }
    val scale by animateFloatAsState(
        targetValue = if (popped) 1.24f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "story_reaction_scale_${type.wireValue}"
    )
    val accent = reactionTintFor(type)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = Color.Black.copy(alpha = 0.56f),
        border = BorderStroke(
            width = 1.dp,
            color = if (popped) accent.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.24f)
        ),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .scale(scale)
            .semantics {
                role = Role.Button
                contentDescription = if (count > 0) "$contentDesc, $count reactions" else contentDesc
            }
            .testTag(testTagName)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = reactionIconFor(type),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(22.dp)
            )
            if (count > 0) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun FloatingReactionBurstItem(
    burst: FloatingReactionBurst,
    onFinished: () -> Unit
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(burst.id) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing)
        )
        onFinished()
    }

    val p = progress.value
    val risePx = -p * 340f
    val driftPx = burst.horizontalOffsetDp * 2.4f
    val alpha = (1f - (p * p)).coerceIn(0f, 1f)
    val iconScale = if (p < 0.25f) 0.7f + (p / 0.25f) * 0.65f else 1.35f - (p - 0.25f) * 0.45f

    Icon(
        imageVector = reactionIconFor(burst.type),
        contentDescription = null,
        tint = reactionTintFor(burst.type),
        modifier = Modifier
            .offset { IntOffset(driftPx.roundToInt(), risePx.roundToInt()) }
            .scale(iconScale)
            .alpha(alpha)
            .size(34.dp)
    )
}

