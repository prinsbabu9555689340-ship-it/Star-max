package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.firebase.MediaUtils
import com.example.data.model.Comment
import com.example.data.model.Post
import com.example.data.model.SampleData
import com.example.ui.theme.LocalStarMaxColors
import com.google.firebase.Timestamp
import kotlinx.coroutines.delay

@Composable
fun StarMaxWordmark(
    modifier: Modifier = Modifier
) {
    val extended = LocalStarMaxColors.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Star Max",
            style = MaterialTheme.typography.displaySmall.copy(
                brush = extended.brandGradientBrush
            )
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun StarMaxMediaImage(
    mediaSource: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallbackText: String = "S"
) {
    val resId = SampleData.drawableRegistry[mediaSource]
    if (resId != null) {
        Image(
            painter = painterResource(id = resId),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
        return
    }

    if (mediaSource.startsWith("data:image")) {
        val bitmap = remember(mediaSource) {
            MediaUtils.decodeBase64DataUri(mediaSource)?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale
            )
            return
        }
    }

    if (mediaSource.isNotBlank()) {
        AsyncImage(
            model = mediaSource,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        Box(
            modifier = modifier.background(LocalStarMaxColors.current.brandGradientBrush),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = fallbackText.take(1).uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
        }
    }
}

@Composable
fun UserAvatar(
    avatarData: String,
    username: String,
    size: Dp = 40.dp,
    hasStoryRing: Boolean = false,
    showOnlineBadge: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val extended = LocalStarMaxColors.current
    val outerSize = if (hasStoryRing) size + 6.dp else size

    Box(
        modifier = modifier
            .size(outerSize)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (hasStoryRing) {
                        Modifier
                            .border(2.5.dp, extended.storyRingBrush, CircleShape)
                            .padding(4.dp)
                    } else {
                        Modifier.border(1.dp, extended.subtleDivider, CircleShape)
                    }
                )
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            StarMaxMediaImage(
                mediaSource = avatarData,
                contentDescription = "$username profile picture",
                modifier = Modifier.fillMaxSize(),
                fallbackText = username
            )
        }

        if (showOnlineBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size((size * 0.28f).coerceAtLeast(10.dp))
                    .clip(CircleShape)
                    .background(extended.onlineGreen)
                    .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
            )
        }
    }
}

@Composable
fun PostCard(
    post: Post,
    isLiked: Boolean,
    isSaved: Boolean,
    isFollowingAuthor: Boolean,
    isOwnPost: Boolean,
    onLikeToggle: () -> Unit,
    onDoubleTapLike: () -> Unit,
    onCommentClick: () -> Unit,
    onSaveToggle: () -> Unit,
    onAuthorClick: () -> Unit,
    onFollowClick: () -> Unit,
    onHashtagClick: (String) -> Unit,
    onArchiveToggle: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val extended = LocalStarMaxColors.current
    var showHeartOverlay by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    LaunchedEffect(showHeartOverlay) {
        if (showHeartOverlay) {
            delay(650)
            showHeartOverlay = false
        }
    }

    val likeScale by animateFloatAsState(
        targetValue = if (isLiked) 1.12f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "likeScale"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Post Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatar(
                avatarData = post.authorAvatar,
                username = post.authorUsername,
                size = 36.dp,
                hasStoryRing = true,
                onClick = onAuthorClick
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onAuthorClick)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = post.authorUsername,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (post.userId.startsWith("sample_")) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Filled.Verified,
                            contentDescription = "Verified creator",
                            tint = extended.verifiedBlue,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                if (post.location.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = post.location,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (!isOwnPost && !isFollowingAuthor) {
                TextButton(
                    onClick = onFollowClick,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(
                        text = "Follow",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (isOwnPost && onArchiveToggle != null && !post.id.startsWith("sample_")) {
                Box {
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Post options"
                        )
                    }
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        val isArchived = post.visibility == "archived"
                        DropdownMenuItem(
                            text = { Text(if (isArchived) "Restore to Profile" else "Archive Post") },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                onArchiveToggle()
                            }
                        )
                    }
                }
            }
        }

        // Media Container with Double-Tap Heart Animation
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (post.mediaType == "video") 4f / 5f else 1f)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(post.id) {
                    detectTapGestures(
                        onDoubleTap = {
                            showHeartOverlay = true
                            onDoubleTapLike()
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            StarMaxMediaImage(
                mediaSource = post.mediaData,
                contentDescription = "Post by ${post.authorUsername}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            if (post.mediaType == "video") {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Short video post",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (post.taggedUsers.isNotEmpty()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "@${post.taggedUsers.first()}${if (post.taggedUsers.size > 1) " +${post.taggedUsers.size - 1}" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = showHeartOverlay,
                enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(92.dp)
                )
            }
        }

        // Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onLikeToggle,
                modifier = Modifier
                    .scale(likeScale)
                    .testTag("like_button_${post.id}")
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isLiked) "Unlike post" else "Like post",
                    tint = if (isLiked) extended.heartRed else MaterialTheme.colorScheme.onBackground
                )
            }

            IconButton(
                onClick = onCommentClick,
                modifier = Modifier.testTag("comment_button_${post.id}")
            ) {
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = "Comments",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            IconButton(
                onClick = { sharePostExternally(context, post) },
                modifier = Modifier.testTag("share_button_${post.id}")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = "Share post",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            IconButton(
                onClick = onSaveToggle,
                modifier = Modifier.testTag("save_button_${post.id}")
            ) {
                Icon(
                    imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = if (isSaved) "Remove bookmark" else "Bookmark post",
                    tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                )
            }
        }

        // Likes, Caption, Comments & Timestamp
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(bottom = 14.dp)
        ) {
            val effectiveLikes = (post.likesCount + if (isLiked && post.id.startsWith("sample_")) 1L else 0L)
                .coerceAtLeast(0L)
            Text(
                text = "${formatCompactCount(effectiveLikes)} likes",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            if (post.caption.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                val captionAnnotated = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(post.authorUsername)
                        append(" ")
                    }
                    val tokens = post.caption.split(" ")
                    tokens.forEachIndexed { idx, word ->
                        if (word.startsWith("#") || word.startsWith("@")) {
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)) {
                                append(word)
                            }
                        } else {
                            append(word)
                        }
                        if (idx < tokens.lastIndex) append(" ")
                    }
                }
                Text(
                    text = captionAnnotated,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (post.hashtags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    post.hashtags.take(3).forEach { tag ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.clickable { onHashtagClick(tag) }
                        ) {
                            Text(
                                text = "#$tag",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            if (post.commentsCount > 0L) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "View all ${post.commentsCount} comments",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onCommentClick)
                )
            }

            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = formatRelativeTime(post.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider(color = extended.subtleDivider, thickness = 0.5.dp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsBottomSheet(
    comments: List<Comment>,
    onDismiss: () -> Unit,
    onSubmitComment: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var commentText by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp)
        ) {
            Text(
                text = "Comments",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = LocalStarMaxColors.current.subtleDivider)

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (comments.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Be the first to share a comment ✨",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(comments, key = { it.id.ifBlank { it.hashCode() } }) { comment ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            UserAvatar(
                                avatarData = comment.authorAvatar,
                                username = comment.authorUsername,
                                size = 32.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = comment.authorUsername,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = formatRelativeTime(comment.createdAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = comment.text,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    placeholder = { Text("Add a comment...") },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("comment_input")
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val trimmed = commentText.trim()
                        if (trimmed.isNotEmpty()) {
                            onSubmitComment(trimmed)
                            commentText = ""
                        }
                    },
                    enabled = commentText.isNotBlank(),
                    modifier = Modifier.testTag("send_comment_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Post comment",
                        tint = if (commentText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

fun sharePostExternally(context: Context, post: Post) {
    val shareBody = buildString {
        append("Check out @${post.authorUsername} on Star Max!")
        if (post.caption.isNotBlank()) append("\n\n${post.caption}")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareBody)
    }
    context.startActivity(Intent.createChooser(intent, "Share via Star Max"))
}

fun formatCompactCount(count: Long): String = when {
    count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
    count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
    else -> count.toString()
}

fun formatRelativeTime(timestamp: Timestamp?): String {
    if (timestamp == null) return "Just now"
    val diffSec = ((System.currentTimeMillis() - timestamp.toDate().time) / 1000L).coerceAtLeast(0L)
    return when {
        diffSec < 60L -> "Just now"
        diffSec < 3600L -> "${diffSec / 60L}m ago"
        diffSec < 86400L -> "${diffSec / 3600L}h ago"
        else -> "${diffSec / 86400L}d ago"
    }
}
