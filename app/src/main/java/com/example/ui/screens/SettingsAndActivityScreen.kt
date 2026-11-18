package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Verified
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Comment
import com.example.data.model.FollowRequest
import com.example.data.model.Post
import com.example.data.model.ThemeMode
import com.example.data.model.UserProfile
import com.example.data.model.UserSettings
import com.example.ui.theme.LocalStarMaxColors
import com.google.firebase.auth.FirebaseAuth

@Composable
fun SettingsAndActivityScreen(
    currentUserId: String,
    userEmail: String,
    userPhone: String = "",
    savedAccountsCount: Int = 1,
    activeAuth: FirebaseAuth? = null,
    profile: UserProfile,
    userSettings: UserSettings,
    themeMode: ThemeMode,
    todayUsageSeconds: Long,
    posts: List<Post>,
    archivedPosts: List<Post>,
    userComments: List<Comment>,
    followRequests: List<FollowRequest>,
    likedIds: Set<String>,
    savedIds: Set<String>,
    followingIds: Set<String>,
    verifiedUserIds: Set<String> = emptySet(),
    onBack: () -> Unit,
    onSetDarkModeEnabled: (Boolean) -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetAccountPrivacy: (Boolean) -> Unit,
    onUpdateNotificationPrefs: (likes: Boolean, comments: Boolean, followers: Boolean, messages: Boolean) -> Unit,
    onSaveProfileEdits: (username: String, fullName: String, bio: String, website: String, avatarData: String?) -> Unit,
    onSubmitVerificationRequest: (
        fullName: String,
        knownAs: String,
        category: String,
        link: String,
        idProofData: String,
        onDone: () -> Unit
    ) -> Unit = { _, _, _, _, _, onDone -> onDone() },
    onSetDeveloperVerified: (Boolean) -> Unit = {},
    onUpdateAppPassword: ((newPassword: String, onResult: (Result<Unit>) -> Unit) -> Unit)? = null,
    onLikeToggle: (Post) -> Unit,
    onDoubleTapLike: (Post) -> Unit,
    onOpenComments: (Post) -> Unit,
    onSaveToggle: (String) -> Unit,
    onToggleArchivePost: (Post) -> Unit,
    onFollowToggle: (String) -> Unit,
    onApproveFollowRequest: (FollowRequest) -> Unit,
    onDeclineFollowRequest: (String) -> Unit,
    onShowMessage: (String) -> Unit,
    onSignOut: () -> Unit,
    onSignOutAll: () -> Unit = {}
) {
    BackHandler(onBack = onBack)

    val extended = LocalStarMaxColors.current
    val systemDark = isSystemInDarkTheme()
    val isDarkSwitchChecked = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }

    var showActivityHistory by remember { mutableStateOf(false) }
    var showSavedArchived by remember { mutableStateOf(false) }
    var showEditProfile by remember { mutableStateOf(false) }
    var showRequestVerification by remember { mutableStateOf(false) }
    var showFollowRequests by remember { mutableStateOf(false) }
    var showChangePassword by remember { mutableStateOf(false) }

    val likedPosts = remember(posts, likedIds) { posts.filter { it.id in likedIds } }
    val savedPosts = remember(posts, savedIds) { posts.filter { it.id in savedIds } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("settings_back_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Settings and activity",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
        }
        HorizontalDivider(color = extended.subtleDivider, thickness = 0.5.dp)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. YOUR ACTIVITY SECTION
            item {
                SectionHeading("Your activity")
            }

            // Time Spent / Daily App Usage Card
            item {
                val dailyGoalSeconds = 3600f // 60 min mindful daily target
                val progress = (todayUsageSeconds / dailyGoalSeconds).coerceIn(0.02f, 1f)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("time_spent_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AccessTime,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Time spent today",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Daily Star Max activity tracker",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = formatUsageTime(todayUsageSeconds),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Synced automatically to your account • 60m mindful daily guide",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Activity Links Card (Likes & Comments, Saved & Archived)
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        SettingsActionRow(
                            icon = Icons.Filled.Favorite,
                            title = "Likes & Comments History",
                            subtitle = "${likedPosts.size} liked • ${userComments.size} comments",
                            testTag = "settings_likes_comments_row",
                            onClick = { showActivityHistory = true }
                        )
                        HorizontalDivider(
                            color = extended.subtleDivider,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        SettingsActionRow(
                            icon = Icons.Filled.Archive,
                            title = "Archived & Saved Posts",
                            subtitle = "${savedPosts.size} saved • ${archivedPosts.size} archived",
                            testTag = "settings_saved_archived_row",
                            onClick = { showSavedArchived = true }
                        )
                    }
                }
            }

            // 2. ACCOUNTS CENTER (Edit Profile & Star Max Verified)
            item {
                SectionHeading("Accounts Center")
            }

            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        // Edit Profile Shortcut
                        SettingsActionRow(
                            icon = Icons.Filled.Edit,
                            title = "Edit Profile",
                            subtitle = "Update name, @${profile.username}, bio & photo",
                            testTag = "settings_edit_profile_row",
                            onClick = { showEditProfile = true }
                        )
                        HorizontalDivider(
                            color = extended.subtleDivider,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        // Star Max Verified (Blue Tick Request Form)
                        val verificationStatusLabel = if (profile.verified) "Verified" else "Apply"
                        val verificationSubtitle = if (profile.verified) {
                            "Blue Tick active across Profile, Feed & Comments"
                        } else {
                            "Submit verification form or toggle badge below"
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showRequestVerification = true }
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                .testTag("settings_request_verification_row"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Verified,
                                contentDescription = "Star Max Verified",
                                tint = extended.verifiedBlue
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Star Max Verified",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = if (profile.verified) {
                                            extended.verifiedBlue.copy(alpha = 0.16f)
                                        } else {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = verificationStatusLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (profile.verified) extended.verifiedBlue else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = verificationSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        HorizontalDivider(
                            color = extended.subtleDivider,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        // Instant Star Max Verified Toggle inside Accounts Center
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSetDeveloperVerified(!profile.verified) }
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                .testTag("settings_dev_verified_row"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Verified,
                                contentDescription = "Toggle Star Max Verified",
                                tint = if (profile.verified) extended.verifiedBlue else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Blue Tick Verification",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = if (profile.verified) {
                                        "ON • Verified badge shown next to @${profile.username}"
                                    } else {
                                        "Turn ON to verify @${profile.username} instantly"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = profile.verified,
                                onCheckedChange = onSetDeveloperVerified,
                                modifier = Modifier.testTag("settings_dev_verified_switch")
                            )
                        }
                    }
                }
            }

            // 3. PRIVACY & APPEARANCE SETTINGS SECTION
            item {
                SectionHeading("Privacy & appearance")
            }

            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {

                        // Account Privacy Toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSetAccountPrivacy(!userSettings.isPrivate) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (userSettings.isPrivate) Icons.Filled.Lock else Icons.Filled.Public,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Account Privacy",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = if (userSettings.isPrivate) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                        } else {
                                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = if (userSettings.isPrivate) "Private" else "Public",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (userSettings.isPrivate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (userSettings.isPrivate) {
                                        "New followers require your approval"
                                    } else {
                                        "Anyone on Star Max can follow you"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = userSettings.isPrivate,
                                onCheckedChange = onSetAccountPrivacy,
                                modifier = Modifier.testTag("privacy_switch")
                            )
                        }

                        if (userSettings.isPrivate || followRequests.isNotEmpty()) {
                            HorizontalDivider(
                                color = extended.subtleDivider,
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            SettingsActionRow(
                                icon = Icons.Filled.GroupAdd,
                                title = "Follow Requests",
                                subtitle = if (followRequests.isEmpty()) "No pending requests" else "${followRequests.size} waiting for approval",
                                badgeCount = followRequests.size,
                                testTag = "settings_follow_requests_row",
                                onClick = { showFollowRequests = true }
                            )
                        }

                        HorizontalDivider(
                            color = extended.subtleDivider,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        // Dark / Light Mode Toggle + System selector
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isDarkSwitchChecked) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Dark Mode",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "Current theme: ${themeMode.label}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = isDarkSwitchChecked,
                                    onCheckedChange = onSetDarkModeEnabled,
                                    modifier = Modifier.testTag("dark_mode_switch")
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ThemeMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = themeMode == mode,
                                        onClick = { onSetThemeMode(mode) },
                                        label = { Text(mode.label, style = MaterialTheme.typography.labelMedium) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Notification Preferences Card
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Notifications, contentDescription = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Push Notifications",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        NotificationToggleRow(
                            label = "Likes",
                            subtitle = "When someone likes your posts or Reels",
                            checked = userSettings.notifyLikes,
                            testTag = "notify_likes_switch",
                            onCheckedChange = {
                                onUpdateNotificationPrefs(
                                    it,
                                    userSettings.notifyComments,
                                    userSettings.notifyFollowers,
                                    userSettings.notifyMessages
                                )
                            }
                        )
                        NotificationToggleRow(
                            label = "Comments",
                            subtitle = "When someone comments on your content",
                            checked = userSettings.notifyComments,
                            testTag = "notify_comments_switch",
                            onCheckedChange = {
                                onUpdateNotificationPrefs(
                                    userSettings.notifyLikes,
                                    it,
                                    userSettings.notifyFollowers,
                                    userSettings.notifyMessages
                                )
                            }
                        )
                        NotificationToggleRow(
                            label = "New Followers",
                            subtitle = "Follows and private follow requests",
                            checked = userSettings.notifyFollowers,
                            testTag = "notify_followers_switch",
                            onCheckedChange = {
                                onUpdateNotificationPrefs(
                                    userSettings.notifyLikes,
                                    userSettings.notifyComments,
                                    it,
                                    userSettings.notifyMessages
                                )
                            }
                        )
                        NotificationToggleRow(
                            label = "Direct Messages",
                            subtitle = "New real-time chat messages",
                            checked = userSettings.notifyMessages,
                            testTag = "notify_messages_switch",
                            onCheckedChange = {
                                onUpdateNotificationPrefs(
                                    userSettings.notifyLikes,
                                    userSettings.notifyComments,
                                    userSettings.notifyFollowers,
                                    it
                                )
                            }
                        )
                    }
                }
            }

            // 3. SECURITY & SESSION SECTION
            item {
                SectionHeading("Security & session")
            }

            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        val securitySubtitle = when {
                            userPhone.isNotBlank() -> "Verified mobile: $userPhone"
                            userEmail.isNotBlank() -> "Manage password for $userEmail"
                            else -> "Password & account security"
                        }
                        SettingsActionRow(
                            icon = Icons.Filled.Key,
                            title = "Change Password",
                            subtitle = securitySubtitle,
                            testTag = "settings_change_password_row",
                            onClick = { showChangePassword = true }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onSignOut,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("settings_logout_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (profile.username.isNotBlank()) "Log Out @${profile.username}" else "Log Out",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onSignOutAll,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("settings_logout_all_button")
                ) {
                    Text(
                        text = if (savedAccountsCount > 1) {
                            "Log out of all accounts ($savedAccountsCount)"
                        } else {
                            "Log out of all accounts"
                        },
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showActivityHistory) {
        ActivityHistoryDialog(
            likedPosts = likedPosts,
            userComments = userComments,
            allPosts = posts,
            likedIds = likedIds,
            savedIds = savedIds,
            followingIds = followingIds,
            verifiedUserIds = verifiedUserIds,
            onLikeToggle = onLikeToggle,
            onDoubleTapLike = onDoubleTapLike,
            onOpenComments = onOpenComments,
            onSaveToggle = onSaveToggle,
            onFollowToggle = onFollowToggle,
            onDismiss = { showActivityHistory = false }
        )
    }

    if (showSavedArchived) {
        SavedAndArchivedDialog(
            currentUserId = currentUserId,
            savedPosts = savedPosts,
            archivedPosts = archivedPosts,
            likedIds = likedIds,
            savedIds = savedIds,
            followingIds = followingIds,
            verifiedUserIds = verifiedUserIds,
            onLikeToggle = onLikeToggle,
            onDoubleTapLike = onDoubleTapLike,
            onOpenComments = onOpenComments,
            onSaveToggle = onSaveToggle,
            onToggleArchivePost = onToggleArchivePost,
            onDismiss = { showSavedArchived = false }
        )
    }

    if (showRequestVerification) {
        RequestVerificationDialog(
            profile = profile,
            userSettings = userSettings,
            userPhone = userPhone,
            onSubmitRequest = onSubmitVerificationRequest,
            onToggleDeveloperVerified = onSetDeveloperVerified,
            onDismiss = { showRequestVerification = false }
        )
    }

    if (showEditProfile) {
        EditProfileSheet(
            profile = profile,
            onDismiss = { showEditProfile = false },
            onSave = { username, fullName, bio, website, avatarData ->
                onSaveProfileEdits(username, fullName, bio, website, avatarData)
                showEditProfile = false
            }
        )
    }

    if (showFollowRequests) {
        FollowRequestsDialog(
            requests = followRequests,
            onApprove = onApproveFollowRequest,
            onDecline = onDeclineFollowRequest,
            onDismiss = { showFollowRequests = false }
        )
    }

    if (showChangePassword) {
        ChangePasswordSheet(
            userEmail = userEmail,
            userPhone = userPhone,
            activeAuth = activeAuth,
            onUpdateAppPassword = onUpdateAppPassword,
            onShowMessage = onShowMessage,
            onDismiss = { showChangePassword = false }
        )
    }
}

@Composable
private fun SectionHeading(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badgeCount: Int = 0,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (badgeCount > 0) {
            Badge { Text(badgeCount.toString()) }
            Spacer(modifier = Modifier.width(8.dp))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NotificationToggleRow(
    label: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

private fun formatUsageTime(totalSeconds: Long): String {
    val safe = totalSeconds.coerceAtLeast(0L)
    val hours = safe / 3600L
    val minutes = (safe % 3600L) / 60L
    val seconds = safe % 60L
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}
