package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.firebase.MediaUtils
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

@Composable
fun CreateContentScreen(
    initialMode: CreateContentMode = CreateContentMode.POST,
    onPublishPost: (caption: String, location: String, taggedUsers: List<String>, mediaType: String, mediaData: String, videoUri: String) -> Unit,
    onPublishReel: (caption: String, audioTitle: String, videoUri: String, thumbnailData: String) -> Unit,
    onPublishStory: (mediaType: String, mediaData: String, textContent: String, bgGradient: String) -> Unit
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

            // Media Picker Buttons
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
                if (mode != CreateContentMode.STORY) {
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
                            StarMaxMediaImage(
                                mediaSource = encodedMediaData,
                                contentDescription = "Selected media preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
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
                            onPublishStory(storyType, encodedMediaData, caption, storyGradient)
                            caption = ""
                            encodedMediaData = ""
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
}
