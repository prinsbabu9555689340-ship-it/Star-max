package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Post
import com.example.data.model.UserProfile
import com.example.ui.components.PostCard
import com.example.ui.components.StarMaxMediaImage
import com.example.ui.components.UserAvatar
import com.example.ui.components.VerifiedBadge
import com.example.ui.theme.LocalStarMaxColors

@Composable
fun ExploreScreen(
    currentUserId: String,
    initialQuery: String,
    profiles: List<UserProfile>,
    posts: List<Post>,
    likedIds: Set<String>,
    savedIds: Set<String>,
    followingIds: Set<String>,
    onFollowToggle: (UserProfile) -> Unit,
    onOpenProfile: (String) -> Unit,
    onLikeToggle: (Post) -> Unit,
    onDoubleTapLike: (Post) -> Unit,
    onOpenComments: (Post) -> Unit,
    onSaveToggle: (String) -> Unit
) {
    var query by remember(initialQuery) { mutableStateOf(initialQuery) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var inspectedPost by remember { mutableStateOf<Post?>(null) }

    val trendingTags = remember(posts) {
        val extracted = posts.flatMap { it.hashtags }.distinct()
        (listOf("tokyo", "travel", "coffee", "aurora", "nightlife") + extracted).distinct().take(10)
    }

    val cleanQuery = query.trim().removePrefix("#").removePrefix("@").lowercase()

    val matchingUsers = remember(profiles, cleanQuery, currentUserId) {
        if (cleanQuery.isBlank()) {
            emptyList()
        } else {
            profiles.filter {
                it.userId != currentUserId &&
                    (it.usernameLower.contains(cleanQuery) || it.fullName.lowercase().contains(cleanQuery))
            }
        }
    }

    val filteredPosts = remember(posts, cleanQuery, selectedTag) {
        posts.filter { post ->
            val tagMatch = selectedTag == null || post.hashtags.any { it.equals(selectedTag, ignoreCase = true) }
            val queryMatch = cleanQuery.isBlank() ||
                post.caption.lowercase().contains(cleanQuery) ||
                post.authorUsername.lowercase().contains(cleanQuery) ||
                post.location.lowercase().contains(cleanQuery) ||
                post.hashtags.any { it.lowercase().contains(cleanQuery) }
            tagMatch && queryMatch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    selectedTag = null
                },
                placeholder = { Text("Search users, #hashtags, places...") },
                leadingIcon = {
                    Icon(imageVector = Icons.Filled.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(imageVector = Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("explore_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedTag == null,
                        onClick = { selectedTag = null },
                        label = { Text("Trending") },
                        shape = RoundedCornerShape(20.dp)
                    )
                }
                items(trendingTags) { tag ->
                    FilterChip(
                        selected = selectedTag == tag,
                        onClick = { selectedTag = if (selectedTag == tag) null else tag },
                        label = { Text("#$tag") },
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }
        }

        if (matchingUsers.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .padding(bottom = 10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(matchingUsers, key = { it.userId }) { user ->
                    val isFollowing = user.userId in followingIds
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        modifier = Modifier
                            .width(156.dp)
                            .clickable { onOpenProfile(user.userId) }
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            UserAvatar(
                                avatarData = user.avatarData,
                                username = user.username,
                                size = 50.dp,
                                showOnlineBadge = user.online
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = user.username,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (user.verified) {
                                    Spacer(modifier = Modifier.width(3.dp))
                                    VerifiedBadge(size = 13.dp)
                                }
                            }
                            Text(
                                text = user.fullName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            if (isFollowing) {
                                OutlinedButton(
                                    onClick = { onFollowToggle(user) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Following", style = MaterialTheme.typography.labelMedium)
                                }
                            } else {
                                Button(
                                    onClick = { onFollowToggle(user) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Follow", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (filteredPosts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No matching posts or hashtags found.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Adaptive(minSize = 140.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp),
                contentPadding = PaddingValues(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalItemSpacing = 4.dp
            ) {
                itemsIndexed(filteredPosts, key = { _, p -> p.id }) { index, post ->
                    val ratio = when {
                        post.mediaType == "video" -> 0.72f
                        index % 3 == 0 -> 0.78f
                        else -> 1f
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(ratio)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { inspectedPost = post }
                            .testTag("explore_tile_${post.id}")
                    ) {
                        StarMaxMediaImage(
                            mediaSource = post.mediaData,
                            contentDescription = "Explore post by ${post.authorUsername}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        if (post.mediaType == "video") {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .padding(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Video",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    inspectedPost?.let { selected ->
        val latest = posts.firstOrNull { it.id == selected.id } ?: selected
        val authorProfile = profiles.firstOrNull { it.userId == latest.userId }
            ?: UserProfile(
                userId = latest.userId,
                username = latest.authorUsername,
                fullName = latest.authorName,
                avatarData = latest.authorAvatar
            )
        Dialog(
            onDismissRequest = { inspectedPost = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Explore Post",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { inspectedPost = null }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close post")
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        PostCard(
                            post = latest,
                            isLiked = latest.id in likedIds,
                            isSaved = latest.id in savedIds,
                            isFollowingAuthor = latest.userId in followingIds,
                            isOwnPost = latest.userId == currentUserId,
                            isAuthorVerified = authorProfile.verified,
                            onLikeToggle = { onLikeToggle(latest) },
                            onDoubleTapLike = { onDoubleTapLike(latest) },
                            onCommentClick = { onOpenComments(latest) },
                            onSaveToggle = { onSaveToggle(latest.id) },
                            onAuthorClick = {
                                inspectedPost = null
                                onOpenProfile(latest.userId)
                            },
                            onFollowClick = { onFollowToggle(authorProfile) },
                            onHashtagClick = { tag ->
                                inspectedPost = null
                                selectedTag = tag
                            }
                        )
                    }
                }
            }
        }
    }
}
