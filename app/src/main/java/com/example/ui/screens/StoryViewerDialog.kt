package com.example.ui.screens

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
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.model.Story
import com.example.data.model.StoryFilter
import com.example.ui.components.StarMaxMediaImage
import com.example.ui.components.UserAvatar
import com.example.ui.components.formatRelativeTime
import com.example.ui.theme.StarMaxAmber
import com.example.ui.theme.StarMaxCoral
import com.example.ui.theme.StarMaxIndigo
import com.example.ui.theme.StarMaxMagenta
import com.example.ui.theme.StarMaxViolet
import kotlinx.coroutines.delay

@Composable
fun StoryViewerDialog(
    stories: List<Story>,
    initialIndex: Int,
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

    val story = stories[currentIndex]
    val activeFilter = remember(story.filterName) { StoryFilter.fromId(story.filterName) }

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
                                    Color.Black.copy(alpha = 0.65f)
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
                    IconButton(onClick = onDismiss) {
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

            // Bottom Caption + Quick Reply
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
                        modifier = Modifier.padding(bottom = 12.dp)
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
                        modifier = Modifier.weight(1f)
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
                        }
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
