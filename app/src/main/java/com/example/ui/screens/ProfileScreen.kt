package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.firebase.MediaUtils
import com.example.data.model.Post
import com.example.data.model.SavedAccountRecord
import com.example.data.model.ThemeMode
import com.example.data.model.UserProfile
import com.example.ui.components.AccountSwitcherBottomSheet
import com.example.ui.components.PostCard
import com.example.ui.components.StarMaxMediaImage
import com.example.ui.components.UserAvatar
import com.example.ui.components.formatCompactCount
import com.example.ui.theme.LocalStarMaxColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    profile: UserProfile,
    isOwnProfile: Boolean,
    posts: List<Post>,
    savedIds: Set<String>,
    likedIds: Set<String>,
    followingIds: Set<String>,
    sentFollowRequestIds: Set<String> = emptySet(),
    pendingRequestsCount: Int = 0,
    themeMode: ThemeMode,
    onCycleTheme: () -> Unit,
    onSaveProfileEdits: (username: String, fullName: String, bio: String, website: String, avatarData: String?) -> Unit,
    onFollowToggle: (UserProfile) -> Unit,
    onMessageUser: (UserProfile) -> Unit,
    onLikeToggle: (Post) -> Unit,
    onDoubleTapLike: (Post) -> Unit,
    onOpenComments: (Post) -> Unit,
    onSaveToggle: (String) -> Unit,
    onArchiveToggle: (Post) -> Unit = {},
    onOpenSettingsAndActivity: () -> Unit = {},
    savedAccounts: List<SavedAccountRecord> = emptyList(),
    onSwitchAccount: (SavedAccountRecord) -> Unit = {},
    onAddAccount: () -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    if (onBack != null) {
        BackHandler(onBack = onBack)
    }
    val context = LocalContext.current
    val extended = LocalStarMaxColors.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var showEditSheet by remember { mutableStateOf(false) }
    var showAccountSwitcher by remember { mutableStateOf(false) }
    var inspectedPost by remember { mutableStateOf<Post?>(null) }

    val userPosts = remember(posts, profile.userId) {
        posts.filter { it.userId == profile.userId }
    }
    val savedPosts = remember(posts, savedIds) {
        posts.filter { it.id in savedIds }
    }
    val gridPosts = if (selectedTab == 0 || !isOwnProfile) userPosts else savedPosts
    val isFollowing = profile.userId in followingIds
    val isRequested = profile.userId in sentFollowRequestIds

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Profile Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (isOwnProfile) {
                            Modifier
                                .clickable { showAccountSwitcher = true }
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                                .testTag("profile_account_switcher")
                        } else {
                            Modifier
                        }
                    )
            ) {
                if (profile.isPrivate) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Private account",
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(16.dp)
                    )
                }
                Text(
                    text = profile.username.ifBlank { "star_user" },
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (profile.verified) {
                    Spacer(modifier = Modifier.width(5.dp))
                    Icon(
                        imageVector = Icons.Filled.Verified,
                        contentDescription = "Verified",
                        tint = extended.verifiedBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (isOwnProfile) {
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Switch account",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (isOwnProfile) {
                IconButton(onClick = onCycleTheme) {
                    Icon(
                        imageVector = when (themeMode) {
                            ThemeMode.DARK -> Icons.Filled.DarkMode
                            ThemeMode.LIGHT -> Icons.Filled.LightMode
                            ThemeMode.SYSTEM -> Icons.Outlined.AutoAwesome
                        },
                        contentDescription = "Toggle theme (${themeMode.label})"
                    )
                }
                IconButton(
                    onClick = onOpenSettingsAndActivity,
                    modifier = Modifier.testTag("profile_menu_button")
                ) {
                    BadgedBox(
                        badge = {
                            if (pendingRequestsCount > 0) {
                                Badge { Text(pendingRequestsCount.toString()) }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Menu,
                            contentDescription = "Settings and Activity"
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = extended.subtleDivider, thickness = 0.5.dp)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // Avatar + Counters
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserAvatar(
                    avatarData = profile.avatarData,
                    username = profile.username,
                    size = 80.dp,
                    hasStoryRing = true,
                    showOnlineBadge = !isOwnProfile && profile.online
                )
                Spacer(modifier = Modifier.width(20.dp))
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ProfileCounter(userPosts.size.toLong().coerceAtLeast(profile.postsCount), "Posts")
                    ProfileCounter(profile.followersCount, "Followers")
                    ProfileCounter(profile.followingCount, "Following")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = profile.fullName.ifBlank { profile.username },
                style = MaterialTheme.typography.titleSmall
            )
            if (profile.bio.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = profile.bio,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (profile.website.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        val url = if (profile.website.startsWith("http")) profile.website else "https://${profile.website}"
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = profile.website,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (isOwnProfile) {
                OutlinedButton(
                    onClick = { showEditSheet = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_profile_button")
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Edit Profile")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isFollowing || isRequested) {
                        OutlinedButton(
                            onClick = { onFollowToggle(profile) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isFollowing) "Following" else "Requested")
                        }
                    } else {
                        Button(
                            onClick = { onFollowToggle(profile) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (profile.isPrivate) "Request" else "Follow")
                        }
                    }
                    OutlinedButton(
                        onClick = { onMessageUser(profile) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Message")
                    }
                }
            }
        }

        // Grid Tabs (Posts / Saved)
        if (isOwnProfile) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                modifier = Modifier.widthIn(max = 680.dp)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Filled.GridOn, contentDescription = "My Posts") },
                    modifier = Modifier.testTag("profile_posts_tab")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Filled.Bookmark, contentDescription = "Saved Posts") },
                    modifier = Modifier.testTag("profile_saved_tab")
                )
            }
        } else {
            HorizontalDivider(color = extended.subtleDivider, thickness = 0.5.dp)
        }

        if (gridPosts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (selectedTab == 1 && isOwnProfile) {
                        "No saved posts yet. Tap the bookmark icon on any post!"
                    } else {
                        "No posts shared yet."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp),
                contentPadding = PaddingValues(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(gridPosts, key = { it.id }) { post ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { inspectedPost = post }
                    ) {
                        StarMaxMediaImage(
                            mediaSource = post.mediaData,
                            contentDescription = "Post thumbnail",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        if (post.mediaType == "video") {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Video",
                                tint = Color.White,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditSheet) {
        EditProfileSheet(
            profile = profile,
            onDismiss = { showEditSheet = false },
            onSave = { username, fullName, bio, website, avatarData ->
                onSaveProfileEdits(username, fullName, bio, website, avatarData)
                showEditSheet = false
            }
        )
    }

    if (showAccountSwitcher && isOwnProfile) {
        val displayAccounts = savedAccounts.ifEmpty {
            listOf(
                SavedAccountRecord(
                    uid = profile.userId,
                    slotName = "",
                    username = profile.username,
                    fullName = profile.fullName,
                    avatarData = profile.avatarData,
                    verified = profile.verified
                )
            )
        }
        AccountSwitcherBottomSheet(
            activeUserId = profile.userId,
            accounts = displayAccounts,
            onSelectAccount = onSwitchAccount,
            onAddAccount = onAddAccount,
            onDismiss = { showAccountSwitcher = false }
        )
    }

    inspectedPost?.let { selected ->
        val latest = posts.firstOrNull { it.id == selected.id } ?: selected
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
                        Text("Post", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { inspectedPost = null }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close")
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
                            isOwnPost = latest.userId == profile.userId && isOwnProfile,
                            onLikeToggle = { onLikeToggle(latest) },
                            onDoubleTapLike = { onDoubleTapLike(latest) },
                            onCommentClick = { onOpenComments(latest) },
                            onSaveToggle = { onSaveToggle(latest.id) },
                            onAuthorClick = { inspectedPost = null },
                            onFollowClick = {},
                            onHashtagClick = { inspectedPost = null },
                            onArchiveToggle = {
                                onArchiveToggle(latest)
                                inspectedPost = null
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileCounter(count: Long, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = formatCompactCount(count),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileSheet(
    profile: UserProfile,
    onDismiss: () -> Unit,
    onSave: (username: String, fullName: String, bio: String, website: String, avatarData: String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var username by remember { mutableStateOf(profile.username) }
    var fullName by remember { mutableStateOf(profile.fullName) }
    var bio by remember { mutableStateOf(profile.bio) }
    var website by remember { mutableStateOf(profile.website) }
    var newAvatarData by remember { mutableStateOf<String?>(null) }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                newAvatarData = MediaUtils.encodeImageUriToDataUri(context, uri, maxDimension = 360, quality = 70)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Edit Profile", style = MaterialTheme.typography.titleLarge)

            UserAvatar(
                avatarData = newAvatarData ?: profile.avatarData,
                username = username,
                size = 76.dp,
                onClick = {
                    avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            )
            TextButton(
                onClick = {
                    avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            ) {
                Text("Change profile photo")
            }

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_username_input")
            )
            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = { Text("Full name") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_fullname_input")
            )
            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                label = { Text("Bio") },
                maxLines = 3,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = website,
                onValueChange = { website = it },
                label = { Text("Website link") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    onSave(username, fullName, bio, website, newAvatarData)
                },
                enabled = username.trim().length >= 2 && fullName.trim().isNotEmpty(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_profile_button")
            ) {
                Text("Save Changes")
            }
        }
    }
}
