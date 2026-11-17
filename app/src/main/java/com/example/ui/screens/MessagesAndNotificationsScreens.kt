package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.NotificationItem
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.components.UserAvatar
import com.example.ui.components.formatRelativeTime
import com.example.ui.theme.LocalStarMaxColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    notificationsState: UiState<List<NotificationItem>>,
    onMarkRead: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val extended = LocalStarMaxColors.current
    val items = (notificationsState as? UiState.Success)?.data.orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activity", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No notifications yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(items, key = { it.id }) { notif ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (!notif.read) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                                else Color.Transparent
                            )
                            .clickable {
                                onMarkRead(notif.id)
                                onOpenProfile(notif.actorId)
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            UserAvatar(
                                avatarData = notif.actorAvatar,
                                username = notif.actorUsername,
                                size = 44.dp
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (notif.notifType) {
                                            "like" -> extended.heartRed
                                            "comment" -> MaterialTheme.colorScheme.secondary
                                            else -> MaterialTheme.colorScheme.primary
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (notif.notifType) {
                                        "like" -> Icons.Filled.Favorite
                                        "comment" -> Icons.Filled.ChatBubble
                                        else -> Icons.Filled.PersonAdd
                                    },
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${notif.actorUsername} ${notif.previewText}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (!notif.read) FontWeight.SemiBold else FontWeight.Normal
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatRelativeTime(notif.createdAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!notif.read) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                    HorizontalDivider(color = extended.subtleDivider, thickness = 0.5.dp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DmInboxScreen(
    currentUserId: String,
    profiles: List<UserProfile>,
    conversationsState: UiState<List<Conversation>>,
    onOpenChat: (UserProfile) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    var searchQuery by remember { mutableStateOf("") }
    val conversations = (conversationsState as? UiState.Success)?.data.orEmpty()
    val otherProfiles = remember(profiles, currentUserId, searchQuery) {
        val q = searchQuery.trim().lowercase()
        profiles.filter {
            it.userId != currentUserId &&
                (q.isBlank() || it.usernameLower.contains(q) || it.fullName.lowercase().contains(q))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Messages", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search friends to message...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("dm_search_input")
            )

            // Active Friends Row
            if (otherProfiles.isNotEmpty()) {
                Text(
                    text = "Active & Creators",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(otherProfiles, key = { it.userId }) { friend ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(68.dp)
                                .clickable { onOpenChat(friend) }
                                .testTag("dm_friend_${friend.userId}")
                        ) {
                            UserAvatar(
                                avatarData = friend.avatarData,
                                username = friend.username,
                                size = 54.dp,
                                showOnlineBadge = friend.online
                            )
                            Spacer(modifier = Modifier.height(5.dp))
                            Text(
                                text = friend.username,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = LocalStarMaxColors.current.subtleDivider, thickness = 0.5.dp)
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                if (conversations.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Start a real-time chat",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tap any creator above to send text & emojis instantly.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(conversations, key = { it.id }) { conv ->
                        val isUserA = conv.userAId == currentUserId
                        val peerId = if (isUserA) conv.userBId else conv.userAId
                        val peerUsername = if (isUserA) conv.userBUsername else conv.userAUsername
                        val peerAvatar = if (isUserA) conv.userBAvatar else conv.userAAvatar
                        val peerProfile = profiles.firstOrNull { it.userId == peerId }
                            ?: UserProfile(userId = peerId, username = peerUsername, fullName = peerUsername, avatarData = peerAvatar)
                        val isUnread = currentUserId in conv.unreadBy

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenChat(peerProfile) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatar(
                                avatarData = peerAvatar,
                                username = peerUsername,
                                size = 50.dp,
                                showOnlineBadge = peerProfile.online
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = peerUsername,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (isUnread) FontWeight.Bold else FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = conv.lastMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isUnread) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = formatRelativeTime(conv.updatedAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isUnread) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(9.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatThreadScreen(
    currentUserId: String,
    peerProfile: UserProfile,
    messagesState: UiState<List<ChatMessage>>,
    onSendMessage: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val extended = LocalStarMaxColors.current
    val messages = (messagesState as? UiState.Success)?.data.orEmpty()
    val listState = rememberLazyListState()
    var text by remember { mutableStateOf("") }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onOpenProfile(peerProfile.userId) }
                    ) {
                        UserAvatar(
                            avatarData = peerProfile.avatarData,
                            username = peerProfile.username,
                            size = 36.dp,
                            showOnlineBadge = peerProfile.online
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = peerProfile.username,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (peerProfile.online) "Active now" else "Offline",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (peerProfile.online) extended.onlineGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            HorizontalDivider(color = extended.subtleDivider, thickness = 0.5.dp)

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            UserAvatar(
                                avatarData = peerProfile.avatarData,
                                username = peerProfile.username,
                                size = 68.dp,
                                showOnlineBadge = peerProfile.online
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(peerProfile.fullName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "@${peerProfile.username} • Star Max",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(messages, key = { it.id.ifBlank { it.hashCode() } }) { msg ->
                        val isMine = msg.senderId == currentUserId
                        val isSeenByPeer = peerProfile.userId in msg.seenBy
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
                        ) {
                            Surface(
                                color = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(
                                    topStart = 18.dp,
                                    topEnd = 18.dp,
                                    bottomStart = if (isMine) 18.dp else 4.dp,
                                    bottomEnd = if (isMine) 4.dp else 18.dp
                                ),
                                modifier = Modifier.widthIn(max = 280.dp)
                            ) {
                                Text(
                                    text = msg.text,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                )
                            }
                            if (isMine) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 2.dp, end = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DoneAll,
                                        contentDescription = null,
                                        tint = if (isSeenByPeer) extended.verifiedBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = if (isSeenByPeer) "Seen" else "Sent",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Emoji quick strip
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(listOf("❤️", "🔥", "😂", "✨", "🙌", "😍", "🎉", "📸")) { emoji ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.clickable { text += emoji }
                    ) {
                        Text(
                            text = emoji,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Message...") },
                    singleLine = true,
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_message_input")
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val trimmed = text.trim()
                        if (trimmed.isNotEmpty()) {
                            onSendMessage(trimmed)
                            text = ""
                        }
                    },
                    enabled = text.isNotBlank(),
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (text.isNotBlank()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .testTag("send_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send message",
                        tint = if (text.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
