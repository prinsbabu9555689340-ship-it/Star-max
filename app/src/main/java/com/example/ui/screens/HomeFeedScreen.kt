package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Post
import com.example.data.model.Story
import com.example.data.model.ThemeMode
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.components.PostCard
import com.example.ui.components.StarMaxWordmark
import com.example.ui.components.UserAvatar
import com.example.ui.theme.LocalStarMaxColors

@Composable
fun HomeFeedScreen(
    currentUserId: String,
    currentUserProfile: UserProfile?,
    postsState: UiState<List<Post>>,
    storiesState: UiState<List<Story>>,
    profiles: List<UserProfile>,
    likedIds: Set<String>,
    savedIds: Set<String>,
    followingIds: Set<String>,
    unreadNotifications: Int,
    unreadMessages: Int,
    themeMode: ThemeMode,
    onCycleTheme: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenCreateStory: () -> Unit,
    onOpenStory: (List<Story>, Int) -> Unit,
    onLikeToggle: (Post) -> Unit,
    onDoubleTapLike: (Post) -> Unit,
    onOpenComments: (Post) -> Unit,
    onSaveToggle: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onFollowToggle: (UserProfile) -> Unit,
    onHashtagClick: (String) -> Unit,
    onArchiveToggle: (Post) -> Unit = {}
) {
    val extended = LocalStarMaxColors.current
    var showFollowingOnly by remember { mutableStateOf(true) }

    val stories = (storiesState as? UiState.Success)?.data.orEmpty()
    val allPosts = (postsState as? UiState.Success)?.data.orEmpty()
    val displayedPosts = remember(allPosts, followingIds, currentUserId, showFollowingOnly) {
        if (!showFollowingOnly) {
            allPosts
        } else {
            val followed = allPosts.filter { it.userId == currentUserId || it.userId in followingIds }
            followed.ifEmpty { allPosts }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Header
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StarMaxWordmark()
                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = onCycleTheme,
                    modifier = Modifier.testTag("theme_toggle_button")
                ) {
                    Icon(
                        imageVector = when (themeMode) {
                            ThemeMode.DARK -> Icons.Filled.DarkMode
                            ThemeMode.LIGHT -> Icons.Filled.LightMode
                            ThemeMode.SYSTEM -> Icons.Outlined.AutoAwesome
                        },
                        contentDescription = "Switch theme (${themeMode.label})",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                IconButton(
                    onClick = onOpenNotifications,
                    modifier = Modifier.testTag("notifications_button")
                ) {
                    BadgedBox(
                        badge = {
                            if (unreadNotifications > 0) {
                                Badge {
                                    Text(unreadNotifications.coerceAtMost(99).toString())
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FavoriteBorder,
                            contentDescription = "Notifications",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                IconButton(
                    onClick = onOpenMessages,
                    modifier = Modifier.testTag("dm_inbox_button")
                ) {
                    BadgedBox(
                        badge = {
                            if (unreadMessages > 0) {
                                Badge {
                                    Text(unreadMessages.coerceAtMost(99).toString())
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Send,
                            contentDescription = "Direct Messages",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = extended.subtleDivider, thickness = 0.5.dp)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 620.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Stories Strip
            item(key = "stories_strip") {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item(key = "add_story") {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(72.dp)
                                .clickable(onClick = onOpenCreateStory)
                                .testTag("add_story_button")
                        ) {
                            Box(modifier = Modifier.size(66.dp)) {
                                UserAvatar(
                                    avatarData = currentUserProfile?.avatarData.orEmpty(),
                                    username = currentUserProfile?.username ?: "You",
                                    size = 62.dp
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                        .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = "Add story",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(5.dp))
                            Text(
                                text = "Your story",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    itemsIndexed(stories, key = { _, s -> s.id }) { index, story ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(72.dp)
                                .clickable { onOpenStory(stories, index) }
                                .testTag("story_item_${story.id}")
                        ) {
                            UserAvatar(
                                avatarData = story.authorAvatar,
                                username = story.authorUsername,
                                size = 60.dp,
                                hasStoryRing = true
                            )
                            Spacer(modifier = Modifier.height(5.dp))
                            Text(
                                text = story.authorUsername,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                HorizontalDivider(color = extended.subtleDivider, thickness = 0.5.dp)
            }

            // Feed Filter Pills (Following vs Discover All)
            item(key = "feed_filter") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = showFollowingOnly,
                        onClick = { showFollowingOnly = true },
                        label = { Text("Following") },
                        shape = RoundedCornerShape(20.dp)
                    )
                    FilterChip(
                        selected = !showFollowingOnly,
                        onClick = { showFollowingOnly = false },
                        label = { Text("For You") },
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }

            when (postsState) {
                is UiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                is UiState.Error -> {
                    item {
                        Text(
                            text = postsState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }

                is UiState.Success -> {
                    if (displayedPosts.isEmpty()) {
                        item(key = "empty_feed") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Welcome to Star Max! Create your first post (+) or search creators in Explore.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        items(displayedPosts, key = { it.id }) { post ->
                            val authorProfile = profiles.firstOrNull { it.userId == post.userId }
                                ?: UserProfile(
                                    userId = post.userId,
                                    username = post.authorUsername,
                                    fullName = post.authorName,
                                    avatarData = post.authorAvatar
                                )
                            val isVerified = authorProfile.effectiveVerified ||
                                (post.userId == currentUserId && currentUserProfile?.effectiveVerified == true)
                            PostCard(
                                post = post,
                                isLiked = post.id in likedIds,
                                isSaved = post.id in savedIds,
                                isFollowingAuthor = post.userId in followingIds,
                                isOwnPost = post.userId == currentUserId,
                                isAuthorVerified = isVerified,
                                onLikeToggle = { onLikeToggle(post) },
                                onDoubleTapLike = { onDoubleTapLike(post) },
                                onCommentClick = { onOpenComments(post) },
                                onSaveToggle = { onSaveToggle(post.id) },
                                onAuthorClick = { onOpenProfile(post.userId) },
                                onFollowClick = { onFollowToggle(authorProfile) },
                                onHashtagClick = onHashtagClick,
                                onArchiveToggle = { onArchiveToggle(post) }
                            )
                        }
                    }
                }
            }
        }
    }
}
