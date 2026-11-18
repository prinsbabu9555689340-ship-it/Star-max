package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Unarchive
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Comment
import com.example.data.model.FollowRequest
import com.example.data.model.Post
import com.example.ui.components.PostCard
import com.example.ui.components.StarMaxMediaImage
import com.example.ui.components.UserAvatar
import com.example.ui.components.formatRelativeTime
import com.example.ui.theme.LocalStarMaxColors
import com.google.firebase.Firebase
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

@Composable
fun ActivityHistoryDialog(
    likedPosts: List<Post>,
    userComments: List<Comment>,
    allPosts: List<Post>,
    likedIds: Set<String>,
    savedIds: Set<String>,
    followingIds: Set<String>,
    onLikeToggle: (Post) -> Unit,
    onDoubleTapLike: (Post) -> Unit,
    onOpenComments: (Post) -> Unit,
    onSaveToggle: (String) -> Unit,
    onFollowToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var inspectedPost by remember { mutableStateOf<Post?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
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
                        text = "Likes & Comments",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Likes (${likedPosts.size})") },
                        icon = { Icon(Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("activity_likes_tab")
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Comments (${userComments.size})") },
                        icon = { Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("activity_comments_tab")
                    )
                }

                if (selectedTab == 0) {
                    if (likedPosts.isEmpty()) {
                        EmptyActivityState(
                            icon = Icons.Filled.Favorite,
                            title = "No liked posts yet",
                            subtitle = "Double-tap or tap the heart icon on posts in your feed to see them here."
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(likedPosts, key = { it.id }) { post ->
                                MediaGridCell(post = post, onClick = { inspectedPost = post })
                            }
                        }
                    }
                } else {
                    if (userComments.isEmpty()) {
                        EmptyActivityState(
                            icon = Icons.Outlined.ChatBubbleOutline,
                            title = "No comments yet",
                            subtitle = "Comments you leave on posts and Reels will appear here."
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(userComments, key = { it.id.ifBlank { it.hashCode() } }) { comment ->
                                val targetPost = allPosts.firstOrNull { it.id == comment.postId }
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = targetPost != null) {
                                            if (targetPost != null) inspectedPost = targetPost
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (targetPost != null) {
                                            StarMaxMediaImage(
                                                mediaSource = targetPost.mediaData,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(52.dp)
                                                    .clip(RoundedCornerShape(10.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = targetPost?.let { "On @${it.authorUsername}'s post" } ?: "Comment on post",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = comment.text,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = formatRelativeTime(comment.createdAt),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    inspectedPost?.let { selected ->
        val latest = allPosts.firstOrNull { it.id == selected.id } ?: selected
        PostInspectDialog(
            post = latest,
            isLiked = latest.id in likedIds,
            isSaved = latest.id in savedIds,
            isFollowing = latest.userId in followingIds,
            isOwnPost = false,
            onLikeToggle = { onLikeToggle(latest) },
            onDoubleTapLike = { onDoubleTapLike(latest) },
            onOpenComments = { onOpenComments(latest) },
            onSaveToggle = { onSaveToggle(latest.id) },
            onFollowToggle = { onFollowToggle(latest.userId) },
            onDismiss = { inspectedPost = null }
        )
    }
}

@Composable
fun SavedAndArchivedDialog(
    currentUserId: String,
    savedPosts: List<Post>,
    archivedPosts: List<Post>,
    likedIds: Set<String>,
    savedIds: Set<String>,
    followingIds: Set<String>,
    onLikeToggle: (Post) -> Unit,
    onDoubleTapLike: (Post) -> Unit,
    onOpenComments: (Post) -> Unit,
    onSaveToggle: (String) -> Unit,
    onToggleArchivePost: (Post) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var inspectedPost by remember { mutableStateOf<Post?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
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
                        text = "Saved & Archived",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Saved (${savedPosts.size})") },
                        icon = { Icon(Icons.Filled.Bookmark, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("saved_posts_tab")
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Archived (${archivedPosts.size})") },
                        icon = { Icon(Icons.Outlined.Archive, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("archived_posts_tab")
                    )
                }

                if (selectedTab == 0) {
                    if (savedPosts.isEmpty()) {
                        EmptyActivityState(
                            icon = Icons.Filled.Bookmark,
                            title = "No saved posts yet",
                            subtitle = "Tap the bookmark icon on any post to collect favorites here."
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(savedPosts, key = { it.id }) { post ->
                                MediaGridCell(post = post, onClick = { inspectedPost = post })
                            }
                        }
                    }
                } else {
                    if (archivedPosts.isEmpty()) {
                        EmptyActivityState(
                            icon = Icons.Outlined.Archive,
                            title = "No archived posts",
                            subtitle = "Open any of your posts and tap ⋮ -> Archive Post to hide it from your public profile."
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(archivedPosts, key = { it.id }) { post ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        StarMaxMediaImage(
                                            mediaSource = post.mediaData,
                                            contentDescription = "Archived post",
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable { inspectedPost = post },
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = post.caption.ifBlank { "Archived post" },
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Archived • ${formatRelativeTime(post.updatedAt ?: post.createdAt)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        OutlinedButton(
                                            onClick = { onToggleArchivePost(post) },
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(
                                                Icons.Outlined.Unarchive,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Restore", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    inspectedPost?.let { selected ->
        PostInspectDialog(
            post = selected,
            isLiked = selected.id in likedIds,
            isSaved = selected.id in savedIds,
            isFollowing = selected.userId in followingIds,
            isOwnPost = selected.userId == currentUserId,
            onLikeToggle = { onLikeToggle(selected) },
            onDoubleTapLike = { onDoubleTapLike(selected) },
            onOpenComments = { onOpenComments(selected) },
            onSaveToggle = { onSaveToggle(selected.id) },
            onFollowToggle = {},
            onArchiveToggle = {
                onToggleArchivePost(selected)
                inspectedPost = null
            },
            onDismiss = { inspectedPost = null }
        )
    }
}

@Composable
fun FollowRequestsDialog(
    requests: List<FollowRequest>,
    onApprove: (FollowRequest) -> Unit,
    onDecline: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
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
                        text = "Follow Requests",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }
                HorizontalDivider(color = LocalStarMaxColors.current.subtleDivider)

                if (requests.isEmpty()) {
                    EmptyActivityState(
                        icon = Icons.Filled.Lock,
                        title = "No pending requests",
                        subtitle = "When your account is Private, new follow requests will appear here for your approval."
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(requests, key = { it.requesterId }) { req ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    UserAvatar(
                                        avatarData = req.requesterAvatar,
                                        username = req.requesterUsername,
                                        size = 44.dp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = req.requesterUsername,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = req.requesterName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Button(
                                        onClick = { onApprove(req) },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("Approve", style = MaterialTheme.typography.labelMedium)
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    TextButton(onClick = { onDecline(req.requesterId) }) {
                                        Text("Decline", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordSheet(
    userEmail: String,
    userPhone: String = "",
    activeAuth: FirebaseAuth? = null,
    onUpdateAppPassword: ((newPassword: String, onResult: (Result<Unit>) -> Unit) -> Unit)? = null,
    onShowMessage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val resolvedAuth = remember(activeAuth) { activeAuth ?: runCatching { Firebase.auth }.getOrNull() }
    val currentUser = remember(resolvedAuth) { runCatching { resolvedAuth?.currentUser }.getOrNull() }
    val hasEmailPasswordProvider = remember(currentUser) {
        currentUser?.providerData?.any { it.providerId == EmailAuthProvider.PROVIDER_ID } == true
    }
    val hasPhonePasswordMode = userPhone.isNotBlank() && onUpdateAppPassword != null

    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isBusy by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Password & Account Security", style = MaterialTheme.typography.titleLarge)

            if (hasPhonePasswordMode) {
                Text(
                    text = "Update the Star Max login password for $userPhone",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("New password (min 6 characters)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm new password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                statusText?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = {
                        if (newPassword.length < 6) {
                            statusText = "Password must be at least 6 characters."
                            return@Button
                        }
                        if (newPassword != confirmPassword) {
                            statusText = "New passwords do not match."
                            return@Button
                        }
                        isBusy = true
                        onUpdateAppPassword?.invoke(newPassword) { res ->
                            isBusy = false
                            res.onSuccess {
                                onShowMessage("Password updated successfully")
                                onDismiss()
                            }.onFailure { err ->
                                statusText = err.localizedMessage ?: "Failed to update password."
                            }
                        }
                    },
                    enabled = !isBusy && newPassword.isNotBlank() && confirmPassword.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("confirm_change_password_button")
                ) {
                    Text(if (isBusy) "Updating..." else "Update Password")
                }
            } else if (hasEmailPasswordProvider) {
                Text(
                    text = "Update the password for $userEmail",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    label = { Text("Current password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("New password (min 6 characters)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm new password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                statusText?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = {
                        if (newPassword.length < 6) {
                            statusText = "Password must be at least 6 characters."
                            return@Button
                        }
                        if (newPassword != confirmPassword) {
                            statusText = "New passwords do not match."
                            return@Button
                        }
                        val email = currentUser?.email ?: userEmail
                        if (email.isBlank() || currentPassword.isBlank()) {
                            statusText = "Enter your current password to verify."
                            return@Button
                        }
                        isBusy = true
                        val cred = EmailAuthProvider.getCredential(email, currentPassword)
                        currentUser?.reauthenticate(cred)?.addOnSuccessListener {
                            currentUser.updatePassword(newPassword).addOnSuccessListener {
                                isBusy = false
                                onShowMessage("Password updated successfully")
                                onDismiss()
                            }.addOnFailureListener { err ->
                                isBusy = false
                                statusText = err.localizedMessage ?: "Unable to update password."
                            }
                        }?.addOnFailureListener { err ->
                            isBusy = false
                            statusText = err.localizedMessage ?: "Current password is incorrect."
                        }
                    },
                    enabled = !isBusy && currentPassword.isNotBlank() && newPassword.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("confirm_change_password_button")
                ) {
                    Text(if (isBusy) "Updating..." else "Update Password")
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Signed in with Google", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = if (userEmail.isNotBlank()) {
                                "Your Star Max session ($userEmail) is protected by Google Sign-In. You can send a password setup/reset link to your email or manage your Google Account password directly."
                            } else {
                                "Your Star Max session is protected by Google Sign-In."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                statusText?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }

                if (userEmail.isNotBlank()) {
                    Button(
                        onClick = {
                            isBusy = true
                            runCatching {
                                Firebase.auth.sendPasswordResetEmail(userEmail)
                                    .addOnSuccessListener {
                                        isBusy = false
                                        statusText = "Password setup/reset email sent to $userEmail."
                                        onShowMessage("Password email sent to $userEmail")
                                    }
                                    .addOnFailureListener { err ->
                                        isBusy = false
                                        statusText = err.localizedMessage ?: "Could not send password email."
                                    }
                            }.onFailure { err ->
                                isBusy = false
                                statusText = err.localizedMessage ?: "Password reset is unavailable offline."
                            }
                        },
                        enabled = !isBusy,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("send_password_reset_button")
                    ) {
                        Text(if (isBusy) "Sending..." else "Send Password Reset Email")
                    }
                }

                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://myaccount.google.com/security"))
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Google Account Security")
                }
            }
        }
    }
}

@Composable
private fun MediaGridCell(post: Post, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
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

@Composable
private fun EmptyActivityState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PostInspectDialog(
    post: Post,
    isLiked: Boolean,
    isSaved: Boolean,
    isFollowing: Boolean,
    isOwnPost: Boolean,
    onLikeToggle: () -> Unit,
    onDoubleTapLike: () -> Unit,
    onOpenComments: () -> Unit,
    onSaveToggle: () -> Unit,
    onFollowToggle: () -> Unit,
    onArchiveToggle: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
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
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    PostCard(
                        post = post,
                        isLiked = isLiked,
                        isSaved = isSaved,
                        isFollowingAuthor = isFollowing,
                        isOwnPost = isOwnPost,
                        onLikeToggle = onLikeToggle,
                        onDoubleTapLike = onDoubleTapLike,
                        onCommentClick = onOpenComments,
                        onSaveToggle = onSaveToggle,
                        onAuthorClick = onDismiss,
                        onFollowClick = onFollowToggle,
                        onHashtagClick = { onDismiss() },
                        onArchiveToggle = onArchiveToggle
                    )
                }
            }
        }
    }
}
