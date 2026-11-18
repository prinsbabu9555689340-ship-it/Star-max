package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.firebase.MediaUtils
import com.example.data.model.StoryAudioTrack
import com.example.data.model.StoryFilter
import com.example.data.model.StoryMusicCatalog
import com.example.ui.components.StarMaxMediaImage
import com.example.ui.theme.LocalStarMaxColors
import com.example.ui.theme.StarMaxAmber
import com.example.ui.theme.StarMaxCoral
import com.example.ui.theme.StarMaxIndigo
import com.example.ui.theme.StarMaxMagenta
import com.example.ui.theme.StarMaxViolet
import kotlinx.coroutines.launch

enum class CreateContentMode(val label: String) {
    POST("Post"),
    REEL("Reel"),
    STORY("24h Story")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateContentScreen(
    initialMode: CreateContentMode = CreateContentMode.POST,
    onPublishPost: (caption: String, location: String, taggedUsers: List<String>, mediaType: String, mediaData: String, videoUri: String) -> Unit,
    onPublishReel: (caption: String, audioTitle: String, videoUri: String, thumbnailData: String) -> Unit,
    onPublishStory: (
        mediaType: String,
        mediaData: String,
        textContent: String,
        bgGradient: String,
        filterName: String,
        songTitle: String,
        songArtist: String,
        songUrl: String
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val extended = LocalStarMaxColors.current

    var mode by remember(initialMode) { mutableStateOf(initialMode) }
    var caption by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var taggedPeople by remember { mutableStateOf("") }
    var audioTitle by remember { mutableStateOf("") }
    var storyGradient by remember { mutableStateOf("sunset") }
    var selectedFilter by remember { mutableStateOf(StoryFilter.NORMAL) }
    var selectedTrack by remember { mutableStateOf<StoryAudioTrack?>(null) }
    var showMusicPicker by remember { mutableStateOf(false) }

    var selectedMediaType by remember { mutableStateOf("photo") }
    var encodedMediaData by remember { mutableStateOf("") }
    var localVideoUri by remember { mutableStateOf("") }
    var isProcessingMedia by remember { mutableStateOf(false) }
    var mediaError by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessingMedia = true
            mediaError = null
            scope.launch {
                val encoded = MediaUtils.encodeImageUriToDataUri(context, uri)
                if (encoded != null) {
                    encodedMediaData = encoded
                    localVideoUri = ""
                    selectedMediaType = "photo"
                } else {
                    mediaError = "Could not load selected image."
                }
                isProcessingMedia = false
            }
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessingMedia = true
            mediaError = null
            scope.launch {
                val result = MediaUtils.copyVideoUriToAppStorage(context, uri)
                if (result != null) {
                    localVideoUri = result.first
                    encodedMediaData = result.second.ifBlank { "res:img_post_neon_tokyo" }
                    selectedMediaType = "video"
                } else {
                    mediaError = "Could not load selected video."
                }
                isProcessingMedia = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Create",
                style = MaterialTheme.typography.headlineSmall
            )

            // Mode Switcher
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                CreateContentMode.entries.forEach { entry ->
                    FilterChip(
                        selected = mode == entry,
                        onClick = {
                            mode = entry
                            mediaError = null
                        },
                        label = { Text(entry.label) },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.testTag("create_mode_${entry.name.lowercase()}")
                    )
                }
            }

            // Media Picker Buttons + Story Music Button
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (mode != CreateContentMode.REEL) {
                    OutlinedButton(
                        onClick = {
                            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pick_photo_button")
                    ) {
                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pick Photo")
                    }
                }
                if (mode == CreateContentMode.STORY) {
                    OutlinedButton(
                        onClick = { showMusicPicker = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("story_add_music_button")
                    ) {
                        Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = selectedTrack?.title ?: "Add Music / Song",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pick_video_button")
                    ) {
                        Icon(Icons.Filled.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pick Video")
                    }
                }
            }

            // Preview Frame
            val previewBrush = when (storyGradient) {
                "ocean" -> Brush.linearGradient(listOf(StarMaxIndigo, Color(0xFF00B4D8)))
                "neon" -> Brush.linearGradient(listOf(StarMaxViolet, StarMaxMagenta))
                else -> Brush.linearGradient(listOf(StarMaxMagenta, StarMaxCoral, StarMaxAmber))
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (mode == CreateContentMode.POST) 4f / 3f else 3f / 4f)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isProcessingMedia -> {
                            CircularProgressIndicator()
                        }

                        encodedMediaData.isNotBlank() && (mode != CreateContentMode.STORY || selectedMediaType == "photo") -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (mode == CreateContentMode.STORY) {
                                            Modifier.pointerInput(mode, selectedMediaType, encodedMediaData) {
                                                var dragDelta = 0f
                                                var switchedInDrag = false
                                                detectHorizontalDragGestures(
                                                    onDragStart = {
                                                        dragDelta = 0f
                                                        switchedInDrag = false
                                                    },
                                                    onHorizontalDrag = { _, dragAmount ->
                                                        dragDelta += dragAmount
                                                        if (!switchedInDrag && kotlin.math.abs(dragDelta) > 64f) {
                                                            val filters = StoryFilter.entries
                                                            val currentIdx = filters.indexOf(selectedFilter)
                                                            val nextIdx = if (dragDelta < 0f) {
                                                                (currentIdx + 1) % filters.size
                                                            } else {
                                                                (currentIdx - 1 + filters.size) % filters.size
                                                            }
                                                            selectedFilter = filters[nextIdx]
                                                            switchedInDrag = true
                                                        }
                                                    }
                                                )
                                            }
                                        } else {
                                            Modifier
                                        }
                                    )
                            ) {
                                StarMaxMediaImage(
                                    mediaSource = encodedMediaData,
                                    contentDescription = "Selected media preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                    colorFilter = if (mode == CreateContentMode.STORY) selectedFilter.toColorFilter() else null
                                )
                            }
                            if (selectedMediaType == "video") {
                                Surface(
                                    color = Color.Black.copy(alpha = 0.65f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = "Video Ready",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                            if (mode == CreateContentMode.STORY && selectedFilter != StoryFilter.NORMAL) {
                                Surface(
                                    color = Color.Black.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = "Filter: ${selectedFilter.label}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        mode == CreateContentMode.STORY -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(previewBrush),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = caption.ifBlank { "Type a 24h story message or pick a photo above ✨" },
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(24.dp)
                                )
                            }
                        }

                        else -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(
                                    imageVector = if (mode == CreateContentMode.REEL) Icons.Filled.VideoLibrary else Icons.Filled.AddPhotoAlternate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (mode == CreateContentMode.REEL) {
                                        "Choose a short vertical video from your device"
                                    } else {
                                        "Choose a photo or short video from your gallery"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // Music Sticker Overlay inside Story preview
                    if (mode == CreateContentMode.STORY && selectedTrack != null) {
                        val track = selectedTrack!!
                        Surface(
                            color = Color.Black.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 14.dp, vertical = 14.dp)
                                .testTag("story_music_sticker_preview")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MusicNote,
                                    contentDescription = null,
                                    tint = StarMaxAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${track.title} • ${track.artist}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { selectedTrack = null },
                                    modifier = Modifier
                                        .size(28.dp)
                                        .testTag("story_remove_music_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Remove song",
                                        tint = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Story Photo Filters Strip (swipeable horizontally)
            if (mode == CreateContentMode.STORY && encodedMediaData.isNotBlank() && selectedMediaType == "photo") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.AutoFixHigh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Filters",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("story_filter_strip")
                    ) {
                        items(StoryFilter.entries, key = { it.id }) { filter ->
                            val isSelected = selectedFilter == filter
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable { selectedFilter = filter }
                                    .testTag("story_filter_${filter.id.lowercase()}")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(
                                            width = if (isSelected) 2.5.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else extended.subtleDivider,
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                ) {
                                    StarMaxMediaImage(
                                        mediaSource = encodedMediaData,
                                        contentDescription = "${filter.label} filter preview",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                        colorFilter = filter.toColorFilter()
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = filter.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (mode == CreateContentMode.STORY && encodedMediaData.isBlank()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Background:", style = MaterialTheme.typography.labelMedium)
                    listOf(
                        "sunset" to listOf(StarMaxMagenta, StarMaxCoral, StarMaxAmber),
                        "ocean" to listOf(StarMaxIndigo, Color(0xFF00B4D8)),
                        "neon" to listOf(StarMaxViolet, StarMaxMagenta)
                    ).forEach { (key, colors) ->
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(colors))
                                .border(
                                    width = if (storyGradient == key) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    shape = CircleShape
                                )
                                .clickable { storyGradient = key }
                        )
                    }
                }
            }

            if (mediaError != null) {
                Text(
                    text = mediaError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it },
                label = {
                    Text(if (mode == CreateContentMode.STORY) "Story text / caption" else "Write a caption with #hashtags")
                },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("create_caption_input")
            )

            if (mode == CreateContentMode.POST) {
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Add location (e.g. Tokyo, Japan)") },
                    leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = taggedPeople,
                    onValueChange = { taggedPeople = it },
                    label = { Text("Tag people (comma-separated @usernames)") },
                    leadingIcon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (mode == CreateContentMode.REEL) {
                OutlinedTextField(
                    value = audioTitle,
                    onValueChange = { audioTitle = it },
                    label = { Text("Audio track title (optional)") },
                    leadingIcon = { Icon(Icons.Filled.MusicNote, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            val canPublish = when (mode) {
                CreateContentMode.POST -> encodedMediaData.isNotBlank() && !isProcessingMedia
                CreateContentMode.REEL -> localVideoUri.isNotBlank() && !isProcessingMedia
                CreateContentMode.STORY -> (encodedMediaData.isNotBlank() || caption.isNotBlank()) && !isProcessingMedia
            }

            Button(
                onClick = {
                    when (mode) {
                        CreateContentMode.POST -> {
                            val tags = taggedPeople.split(",")
                                .map { it.trim().removePrefix("@") }
                                .filter { it.isNotBlank() }
                            onPublishPost(caption, location, tags, selectedMediaType, encodedMediaData, localVideoUri)
                            caption = ""
                            location = ""
                            taggedPeople = ""
                            encodedMediaData = ""
                            localVideoUri = ""
                        }

                        CreateContentMode.REEL -> {
                            onPublishReel(caption, audioTitle, localVideoUri, encodedMediaData)
                            caption = ""
                            audioTitle = ""
                            encodedMediaData = ""
                            localVideoUri = ""
                        }

                        CreateContentMode.STORY -> {
                            val storyType = if (encodedMediaData.isNotBlank()) "photo" else "text"
                            onPublishStory(
                                storyType,
                                encodedMediaData,
                                caption,
                                storyGradient,
                                selectedFilter.id,
                                selectedTrack?.title.orEmpty(),
                                selectedTrack?.artist.orEmpty(),
                                selectedTrack?.streamUrl.orEmpty()
                            )
                            caption = ""
                            encodedMediaData = ""
                            selectedFilter = StoryFilter.NORMAL
                            selectedTrack = null
                        }
                    }
                },
                enabled = canPublish,
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(vertical = 15.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (canPublish) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .then(
                        if (canPublish) Modifier.background(extended.brandGradientBrush) else Modifier
                    )
                    .testTag("publish_content_button")
            ) {
                Text(
                    text = when (mode) {
                        CreateContentMode.POST -> "Share Post"
                        CreateContentMode.REEL -> "Publish Reel"
                        CreateContentMode.STORY -> "Post to 24h Story"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (canPublish) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showMusicPicker) {
        StoryMusicPickerBottomSheet(
            selectedTrack = selectedTrack,
            onSelectTrack = { track ->
                selectedTrack = track
                showMusicPicker = false
            },
            onRemoveTrack = {
                selectedTrack = null
                showMusicPicker = false
            },
            onDismiss = { showMusicPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoryMusicPickerBottomSheet(
    selectedTrack: StoryAudioTrack?,
    onSelectTrack: (StoryAudioTrack) -> Unit,
    onRemoveTrack: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var previewingTrackId by remember { mutableStateOf<String?>(null) }

    val previewPlayer = remember(context) {
        runCatching { ExoPlayer.Builder(context).build() }.getOrNull()
    }

    DisposableEffect(previewPlayer) {
        val player = previewPlayer ?: return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                previewingTrackId = null
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    previewingTrackId = null
                }
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("story_music_picker_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Trending Story Music",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                if (selectedTrack != null) {
                    TextButton(onClick = onRemoveTrack) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            HorizontalDivider(color = LocalStarMaxColors.current.subtleDivider)

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(StoryMusicCatalog.trendingTracks, key = { it.id }) { track ->
                    val isSelected = selectedTrack?.id == track.id
                    val isPlaying = previewingTrackId == track.id
                    Surface(
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectTrack(track) }
                            .testTag("music_select_${track.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    val player = previewPlayer ?: return@IconButton
                                    if (isPlaying) {
                                        runCatching { player.pause() }
                                        previewingTrackId = null
                                    } else {
                                        runCatching {
                                            player.setMediaItem(MediaItem.fromUri(track.streamUrl))
                                            player.prepare()
                                            player.playWhenReady = true
                                        }.onSuccess {
                                            previewingTrackId = track.id
                                        }.onFailure {
                                            previewingTrackId = null
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                                    .testTag("music_preview_${track.id}")
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause preview" else "Preview ${track.title}",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${track.artist} • ${track.durationLabel}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Selected track",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
