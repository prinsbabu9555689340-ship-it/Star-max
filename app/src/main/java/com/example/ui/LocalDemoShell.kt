package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.firebase.AccountSessionManager
import com.example.data.firebase.LocalUserStore
import com.example.data.firebase.ThemePreferences
import com.example.data.model.ChatMessage
import com.example.data.model.Comment
import com.example.data.model.Conversation
import com.example.data.model.FollowRequest
import com.example.data.model.Post
import com.example.data.model.Reel
import com.example.data.model.SampleData
import com.example.data.model.SavedAccountRecord
import com.example.data.model.Story
import com.example.data.model.ThemeMode
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.UserSettings
import com.example.ui.components.CommentsBottomSheet
import com.example.ui.screens.ChatThreadScreen
import com.example.ui.screens.CreateContentMode
import com.example.ui.screens.CreateContentScreen
import com.example.ui.screens.DmInboxScreen
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.HomeFeedScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ReelsScreen
import com.example.ui.screens.SettingsAndActivityScreen
import com.example.ui.screens.StoryViewerDialog
import com.google.firebase.Timestamp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

private enum class LocalTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
    EXPLORE("explore", "Explore", Icons.Filled.Explore, Icons.Outlined.Explore),
    CREATE("create", "Create", Icons.Filled.AddBox, Icons.Outlined.AddBox),
    REELS("reels", "Reels", Icons.Filled.Movie, Icons.Outlined.Movie),
    PROFILE("profile", "Profile", Icons.Filled.Person, Icons.Outlined.Person)
}

private sealed interface LocalOverlay {
    data object None : LocalOverlay
    data object Notifications : LocalOverlay
    data object DmInbox : LocalOverlay
    data object SettingsAndActivity : LocalOverlay
    data class ChatThread(val peer: UserProfile) : LocalOverlay
    data class UserDetail(val userId: String) : LocalOverlay
}

@Composable
fun LocalAccountShell(
    activeAccount: SavedAccountRecord,
    savedAccounts: List<SavedAccountRecord>,
    sessionManager: AccountSessionManager,
    localStore: LocalUserStore,
    themePreferences: ThemePreferences?,
    currentThemeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit = {},
    onSwitchAccount: (SavedAccountRecord) -> Unit,
    onAddAccount: () -> Unit,
    onSignOut: () -> Unit,
    onSignOutAll: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentUserId = activeAccount.uid

    val initialAllPosts = remember(currentUserId) { localStore.loadAllPosts() }
    val initialPublicPosts = remember(initialAllPosts, currentUserId) {
        initialAllPosts.filter { it.visibility != "archived" }
    }
    val initialArchivedPosts = remember(initialAllPosts, currentUserId) {
        initialAllPosts.filter { it.visibility == "archived" && it.userId == currentUserId }
    }
    val initialFollowing = remember(currentUserId) {
        localStore.loadUserSet(currentUserId, "following", setOf("sample_kai", "sample_maya"))
    }
    val initialLiked = remember(currentUserId) {
        localStore.loadUserSet(currentUserId, "liked", setOf("sample_post_1"))
    }
    val initialSaved = remember(currentUserId) {
        localStore.loadUserSet(currentUserId, "saved", setOf("sample_post_2"))
    }

    var posts by remember(currentUserId) { mutableStateOf(initialPublicPosts) }
    var archivedPosts by remember(currentUserId) { mutableStateOf(initialArchivedPosts) }
    var stories by remember(currentUserId) { mutableStateOf(localStore.loadAllStories()) }
    var reels by remember(currentUserId) { mutableStateOf(localStore.loadAllReels()) }
    var likedIds by remember(currentUserId) { mutableStateOf(initialLiked) }
    var savedIds by remember(currentUserId) { mutableStateOf(initialSaved) }
    var followingIds by remember(currentUserId) { mutableStateOf(initialFollowing) }
    var sentFollowRequestIds by remember(currentUserId) { mutableStateOf(emptySet<String>()) }

    var myProfile by remember(currentUserId) {
        val ownPostCount = initialPublicPosts.count { it.userId == currentUserId }.toLong()
        mutableStateOf(
            LocalUserStore.toUserProfile(
                record = activeAccount,
                postsCount = ownPostCount,
                followingCount = initialFollowing.size.toLong()
            )
        )
    }

    var localThemeMode by remember(currentThemeMode) { mutableStateOf(currentThemeMode) }
    var userSettings by remember(currentUserId) {
        mutableStateOf(
            UserSettings(
                userId = currentUserId,
                isPrivate = activeAccount.isPrivate,
                phoneE164 = activeAccount.phoneE164,
                verificationStatus = activeAccount.verificationStatus.ifBlank {
                    if (activeAccount.verified) "verified" else "none"
                },
                verificationFullName = activeAccount.verificationFullName,
                verificationCategory = activeAccount.verificationCategory,
                verificationKnownAs = activeAccount.verificationKnownAs,
                verificationLink = activeAccount.verificationLink,
                verificationIdProofData = activeAccount.verificationIdProofData
            )
        )
    }
    var todayUsageSeconds by remember { mutableLongStateOf(0L) }

    LaunchedEffect(themePreferences) {
        if (themePreferences != null) {
            runCatching {
                todayUsageSeconds = themePreferences.dailyUsageSeconds.first()
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var isForeground by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> isForeground = true
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    isForeground = false
                    if (themePreferences != null) {
                        scope.launch {
                            runCatching {
                                themePreferences.setDailyUsage(
                                    ThemePreferences.todayDateKey(),
                                    todayUsageSeconds
                                )
                            }
                        }
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isForeground) {
        while (isForeground && isActive) {
            delay(1000L)
            todayUsageSeconds += 1L
            if (todayUsageSeconds % 30L == 0L && themePreferences != null) {
                runCatching {
                    themePreferences.setDailyUsage(
                        ThemePreferences.todayDateKey(),
                        todayUsageSeconds
                    )
                }
            }
        }
    }

    var notifications by remember(currentUserId) { mutableStateOf(SampleData.sampleNotifications) }
    var followRequests by remember(currentUserId) { mutableStateOf(emptyList<FollowRequest>()) }

    val commentsMap = remember(currentUserId) {
        mutableStateMapOf<String, List<Comment>>().apply {
            putAll(localStore.loadCommentsMap())
        }
    }
    var userComments by remember(currentUserId) {
        mutableStateOf(
            commentsMap.values.flatten().filter { it.userId == currentUserId }
        )
    }

    var conversations by remember(currentUserId) {
        mutableStateOf(
            listOf(
                Conversation(
                    id = "dm_${currentUserId}_sample_kai",
                    participantUids = listOf(currentUserId, "sample_kai"),
                    userAId = currentUserId,
                    userAUsername = activeAccount.username,
                    userAAvatar = activeAccount.avatarData,
                    userBId = "sample_kai",
                    userBUsername = "kai_tokyo",
                    userBAvatar = "res:img_post_neon_tokyo",
                    lastMessage = "Welcome to Star Max, @${activeAccount.username}!",
                    lastSenderId = "sample_kai",
                    unreadBy = listOf(currentUserId),
                    updatedAt = Timestamp.now()
                )
            )
        )
    }
    val messagesMap = remember(currentUserId) {
        mutableStateMapOf(
            "dm_${currentUserId}_sample_kai" to listOf(
                ChatMessage(
                    id = "msg_welcome_${currentUserId}",
                    convId = "dm_${currentUserId}_sample_kai",
                    senderId = "sample_kai",
                    participantUids = listOf(currentUserId, "sample_kai"),
                    text = "Welcome to Star Max, @${activeAccount.username}!",
                    seenBy = listOf("sample_kai"),
                    createdAt = Timestamp.now()
                )
            )
        )
    }

    var toastMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            snackbarHostState.showSnackbar(it)
            toastMessage = null
        }
    }

    var selectedTab by remember(currentUserId) { mutableStateOf(LocalTab.HOME) }
    var overlay by remember(currentUserId) { mutableStateOf<LocalOverlay>(LocalOverlay.None) }
    var exploreInitialQuery by remember(currentUserId) { mutableStateOf("") }
    var createInitialMode by remember(currentUserId) { mutableStateOf(CreateContentMode.POST) }
    var activeCommentTarget by remember(currentUserId) { mutableStateOf<Triple<String, String, Boolean>?>(null) }
    var activeStoryViewer by remember(currentUserId) { mutableStateOf<Pair<List<Story>, Int>?>(null) }

    fun updateTheme(mode: ThemeMode) {
        localThemeMode = mode
        onThemeModeSelected(mode)
        if (themePreferences != null) {
            scope.launch { runCatching { themePreferences.setThemeMode(mode) } }
        }
    }

    fun cycleTheme() {
        val next = when (localThemeMode) {
            ThemeMode.SYSTEM -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.SYSTEM
        }
        updateTheme(next)
    }

    fun toggleLikePost(post: Post) {
        val wasLiked = post.id in likedIds
        likedIds = if (wasLiked) likedIds - post.id else likedIds + post.id
        localStore.saveUserSet(currentUserId, "liked", likedIds)
        if (!post.id.startsWith("sample_")) {
            posts = posts.map {
                if (it.id == post.id) it.copy(likesCount = (it.likesCount + if (wasLiked) -1L else 1L).coerceAtLeast(0L)) else it
            }
            localStore.saveCustomPosts(posts + archivedPosts)
        }
    }

    fun toggleSavePost(postId: String) {
        savedIds = if (postId in savedIds) savedIds - postId else savedIds + postId
        localStore.saveUserSet(currentUserId, "saved", savedIds)
    }

    fun toggleFollowProfile(target: UserProfile) {
        followingIds = if (target.userId in followingIds) followingIds - target.userId else followingIds + target.userId
        localStore.saveUserSet(currentUserId, "following", followingIds)
        myProfile = myProfile.copy(followingCount = followingIds.size.toLong())
    }

    fun saveEditedProfile(u: String, f: String, b: String, w: String, a: String?) {
        val nextUsername = sessionManager.sanitizeUsername(u).ifBlank { myProfile.username }
        val nextFull = f.trim().ifBlank { myProfile.fullName }
        val nextBio = b.trim()
        val nextWeb = w.trim()
        val nextAvatar = a ?: myProfile.avatarData
        myProfile = myProfile.copy(
            username = nextUsername,
            usernameLower = nextUsername.lowercase(),
            fullName = nextFull,
            bio = nextBio,
            website = nextWeb,
            avatarData = nextAvatar
        )
        sessionManager.updateAccountProfile(
            uid = currentUserId,
            username = nextUsername,
            fullName = nextFull,
            avatarData = nextAvatar,
            verified = myProfile.verified,
            bio = nextBio,
            website = nextWeb
        )
        localStore.syncAuthorMetadataInPosts(currentUserId, nextUsername, nextFull, nextAvatar)
        posts = posts.map {
            if (!it.id.startsWith("sample_") && it.userId == currentUserId) {
                it.copy(authorUsername = nextUsername, authorName = nextFull, authorAvatar = nextAvatar)
            } else it
        }
        toastMessage = "Profile updated"
    }

    fun sendLocalDm(peer: UserProfile, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val convId = if (currentUserId <= peer.userId) "dm_${currentUserId}_${peer.userId}" else "dm_${peer.userId}_${currentUserId}"
        val msg = ChatMessage(
            id = UUID.randomUUID().toString(),
            convId = convId,
            senderId = currentUserId,
            participantUids = listOf(currentUserId, peer.userId),
            text = clean,
            seenBy = listOf(currentUserId),
            createdAt = Timestamp.now()
        )
        messagesMap[convId] = messagesMap[convId].orEmpty() + msg
        val updatedConv = Conversation(
            id = convId,
            participantUids = listOf(currentUserId, peer.userId),
            userAId = myProfile.userId,
            userAUsername = myProfile.username,
            userAAvatar = myProfile.avatarData,
            userBId = peer.userId,
            userBUsername = peer.username,
            userBAvatar = peer.avatarData,
            lastMessage = clean,
            lastSenderId = currentUserId,
            unreadBy = emptyList(),
            updatedAt = Timestamp.now()
        )
        conversations = listOf(updatedConv) + conversations.filterNot { it.id == convId }
    }

    val otherRegisteredProfiles = remember(savedAccounts, myProfile) {
        sessionManager.getAllRegisteredAccounts()
            .filter { it.uid != currentUserId }
            .map { LocalUserStore.toUserProfile(it) }
    }
    val allProfiles = remember(myProfile, otherRegisteredProfiles) {
        (listOf(myProfile) + otherRegisteredProfiles + SampleData.sampleProfiles)
            .distinctBy { it.userId }
    }
    val syncedSavedAccounts = remember(savedAccounts, myProfile) {
        savedAccounts.map { rec ->
            if (rec.uid == myProfile.userId) {
                rec.copy(
                    username = myProfile.username,
                    fullName = myProfile.fullName,
                    avatarData = myProfile.avatarData,
                    verified = myProfile.verified
                )
            } else rec
        }
    }
    val verifiedUserIds = remember(allProfiles) {
        allProfiles.filter { it.verified }.mapTo(HashSet()) { it.userId }
    }
    val unreadNotifications = remember(notifications) { notifications.count { !it.read } }
    val unreadMessages = remember(conversations) { conversations.count { currentUserId in it.unreadBy } }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 680.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (!useRail && overlay is LocalOverlay.None) {
                    val isReels = selectedTab == LocalTab.REELS
                    NavigationBar(
                        containerColor = if (isReels) Color.Black else MaterialTheme.colorScheme.surface,
                        contentColor = if (isReels) Color.White else MaterialTheme.colorScheme.onSurface,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        LocalTab.entries.forEach { tab ->
                            val isSelected = selectedTab == tab
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    if (tab == LocalTab.CREATE) createInitialMode = CreateContentMode.POST
                                    selectedTab = tab
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label,
                                        tint = if (isReels) {
                                            if (isSelected) Color.White else Color.White.copy(alpha = 0.6f)
                                        } else {
                                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isReels) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                modifier = Modifier.testTag("nav_tab_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail && overlay is LocalOverlay.None) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
                    ) {
                        LocalTab.entries.forEach { tab ->
                            val isSelected = selectedTab == tab
                            NavigationRailItem(
                                selected = isSelected,
                                onClick = {
                                    if (tab == LocalTab.CREATE) createInitialMode = CreateContentMode.POST
                                    selectedTab = tab
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("rail_tab_${tab.route}")
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .then(
                            if (selectedTab != LocalTab.REELS || overlay !is LocalOverlay.None) {
                                Modifier.windowInsetsPadding(WindowInsets.statusBars)
                            } else {
                                Modifier
                            }
                        )
                ) {
                    when (val currentOverlay = overlay) {
                        is LocalOverlay.Notifications -> {
                            NotificationsScreen(
                                notificationsState = UiState.Success(notifications),
                                verifiedUserIds = verifiedUserIds,
                                onMarkRead = { nid ->
                                    notifications = notifications.map { if (it.id == nid) it.copy(read = true) else it }
                                },
                                onOpenProfile = { uid ->
                                    overlay = if (uid == currentUserId) {
                                        selectedTab = LocalTab.PROFILE
                                        LocalOverlay.None
                                    } else {
                                        LocalOverlay.UserDetail(uid)
                                    }
                                },
                                onBack = { overlay = LocalOverlay.None }
                            )
                        }

                        is LocalOverlay.DmInbox -> {
                            DmInboxScreen(
                                currentUserId = currentUserId,
                                profiles = allProfiles,
                                conversationsState = UiState.Success(conversations),
                                onOpenChat = { peer -> overlay = LocalOverlay.ChatThread(peer) },
                                onBack = { overlay = LocalOverlay.None }
                            )
                        }

                        is LocalOverlay.ChatThread -> {
                            val convId = if (currentUserId <= currentOverlay.peer.userId) {
                                "dm_${currentUserId}_${currentOverlay.peer.userId}"
                            } else {
                                "dm_${currentOverlay.peer.userId}_${currentUserId}"
                            }
                            LaunchedEffect(convId) {
                                conversations = conversations.map {
                                    if (it.id == convId) it.copy(unreadBy = it.unreadBy - currentUserId) else it
                                }
                            }
                            ChatThreadScreen(
                                currentUserId = currentUserId,
                                peerProfile = currentOverlay.peer,
                                messagesState = UiState.Success(messagesMap[convId].orEmpty()),
                                onSendMessage = { msg -> sendLocalDm(currentOverlay.peer, msg) },
                                onOpenProfile = { uid -> overlay = LocalOverlay.UserDetail(uid) },
                                onBack = { overlay = LocalOverlay.DmInbox }
                            )
                        }

                        is LocalOverlay.SettingsAndActivity -> {
                            SettingsAndActivityScreen(
                                currentUserId = currentUserId,
                                userEmail = "",
                                userPhone = activeAccount.phoneE164,
                                savedAccountsCount = syncedSavedAccounts.size.coerceAtLeast(1),
                                activeAuth = null,
                                profile = myProfile,
                                userSettings = userSettings,
                                themeMode = localThemeMode,
                                todayUsageSeconds = todayUsageSeconds,
                                posts = posts,
                                archivedPosts = archivedPosts,
                                userComments = userComments,
                                followRequests = followRequests,
                                likedIds = likedIds,
                                savedIds = savedIds,
                                followingIds = followingIds,
                                verifiedUserIds = verifiedUserIds,
                                onBack = { overlay = LocalOverlay.None },
                                onSetDarkModeEnabled = { enabled ->
                                    updateTheme(if (enabled) ThemeMode.DARK else ThemeMode.LIGHT)
                                },
                                onSetThemeMode = { mode -> updateTheme(mode) },
                                onSetAccountPrivacy = { isPriv ->
                                    userSettings = userSettings.copy(isPrivate = isPriv)
                                    myProfile = myProfile.copy(isPrivate = isPriv)
                                    sessionManager.updateAccountProfile(
                                        uid = currentUserId,
                                        username = myProfile.username,
                                        fullName = myProfile.fullName,
                                        avatarData = myProfile.avatarData,
                                        verified = myProfile.verified,
                                        isPrivate = isPriv
                                    )
                                    toastMessage = if (isPriv) "Account set to Private" else "Account set to Public"
                                },
                                onUpdateNotificationPrefs = { l, c, f, m ->
                                    userSettings = userSettings.copy(
                                        notifyLikes = l,
                                        notifyComments = c,
                                        notifyFollowers = f,
                                        notifyMessages = m
                                    )
                                },
                                onSaveProfileEdits = { u, f, b, w, a ->
                                    saveEditedProfile(u, f, b, w, a)
                                },
                                onSubmitVerificationRequest = { fullName, knownAs, category, link, idProofData, onDone ->
                                    myProfile = myProfile.copy(verified = true)
                                    userSettings = userSettings.copy(
                                        verificationStatus = "verified",
                                        verificationFullName = fullName,
                                        verificationKnownAs = knownAs,
                                        verificationCategory = category,
                                        verificationLink = link,
                                        verificationIdProofData = idProofData,
                                        verificationSubmittedAtMillis = System.currentTimeMillis()
                                    )
                                    sessionManager.updateAccountVerification(
                                        uid = currentUserId,
                                        verified = true,
                                        verificationStatus = "verified",
                                        fullName = fullName,
                                        category = category,
                                        knownAs = knownAs,
                                        link = link,
                                        idProofData = idProofData
                                    )
                                    toastMessage = "Blue Tick Verified enabled on @${myProfile.username}!"
                                    onDone()
                                },
                                onSetDeveloperVerified = { enabled ->
                                    myProfile = myProfile.copy(verified = enabled)
                                    val status = if (enabled) "verified" else "none"
                                    userSettings = userSettings.copy(verificationStatus = status)
                                    sessionManager.updateAccountVerification(
                                        uid = currentUserId,
                                        verified = enabled,
                                        verificationStatus = status
                                    )
                                    toastMessage = if (enabled) {
                                        "Blue Tick Verified badge enabled on @${myProfile.username}"
                                    } else {
                                        "Blue Tick Verified badge removed from @${myProfile.username}"
                                    }
                                },
                                onUpdateAppPassword = { newPwd, onResult ->
                                    val res = sessionManager.updateLocalPassword(currentUserId, newPwd)
                                    if (res.isSuccess) {
                                        toastMessage = "Password updated"
                                    }
                                    onResult(res)
                                },
                                onLikeToggle = { toggleLikePost(it) },
                                onDoubleTapLike = { if (it.id !in likedIds) toggleLikePost(it) },
                                onOpenComments = { activeCommentTarget = Triple(it.id, it.userId, false) },
                                onSaveToggle = { toggleSavePost(it) },
                                onToggleArchivePost = { post ->
                                    if (post.id.startsWith("sample_")) {
                                        toastMessage = "Sample posts cannot be archived"
                                    } else if (post in archivedPosts) {
                                        archivedPosts = archivedPosts - post
                                        posts = listOf(post.copy(visibility = "public")) + posts
                                        localStore.saveCustomPosts(posts + archivedPosts)
                                        toastMessage = "Post restored to profile"
                                    } else {
                                        posts = posts - post
                                        archivedPosts = listOf(post.copy(visibility = "archived")) + archivedPosts
                                        localStore.saveCustomPosts(posts + archivedPosts)
                                        toastMessage = "Post archived"
                                    }
                                },
                                onFollowToggle = { uid ->
                                    allProfiles.firstOrNull { it.userId == uid }?.let { toggleFollowProfile(it) }
                                },
                                onApproveFollowRequest = { req ->
                                    followRequests = followRequests.filterNot { it.requesterId == req.requesterId }
                                    myProfile = myProfile.copy(followersCount = myProfile.followersCount + 1L)
                                },
                                onDeclineFollowRequest = { reqId ->
                                    followRequests = followRequests.filterNot { it.requesterId == reqId }
                                },
                                onShowMessage = { toastMessage = it },
                                onSignOut = onSignOut,
                                onSignOutAll = onSignOutAll
                            )
                        }

                        is LocalOverlay.UserDetail -> {
                            val target = allProfiles.firstOrNull { it.userId == currentOverlay.userId }
                                ?: UserProfile(userId = currentOverlay.userId, username = "creator", fullName = "Star Max Creator")
                            ProfileScreen(
                                profile = target,
                                isOwnProfile = target.userId == currentUserId,
                                posts = posts,
                                savedIds = savedIds,
                                likedIds = likedIds,
                                followingIds = followingIds,
                                sentFollowRequestIds = sentFollowRequestIds,
                                verifiedUserIds = verifiedUserIds,
                                themeMode = localThemeMode,
                                onCycleTheme = { cycleTheme() },
                                onSaveProfileEdits = { u, f, b, w, a -> saveEditedProfile(u, f, b, w, a) },
                                onFollowToggle = { toggleFollowProfile(it) },
                                onMessageUser = { peer -> overlay = LocalOverlay.ChatThread(peer) },
                                onLikeToggle = { toggleLikePost(it) },
                                onDoubleTapLike = { if (it.id !in likedIds) toggleLikePost(it) },
                                onOpenComments = { activeCommentTarget = Triple(it.id, it.userId, false) },
                                onSaveToggle = { toggleSavePost(it) },
                                onArchiveToggle = {},
                                onOpenSettingsAndActivity = { overlay = LocalOverlay.SettingsAndActivity },
                                savedAccounts = syncedSavedAccounts,
                                onSwitchAccount = onSwitchAccount,
                                onAddAccount = onAddAccount,
                                onBack = { overlay = LocalOverlay.None }
                            )
                        }

                        is LocalOverlay.None -> {
                            when (selectedTab) {
                                LocalTab.HOME -> {
                                    HomeFeedScreen(
                                        currentUserId = currentUserId,
                                        currentUserProfile = myProfile,
                                        postsState = UiState.Success(posts),
                                        storiesState = UiState.Success(stories),
                                        profiles = allProfiles,
                                        likedIds = likedIds,
                                        savedIds = savedIds,
                                        followingIds = followingIds,
                                        unreadNotifications = unreadNotifications,
                                        unreadMessages = unreadMessages,
                                        themeMode = localThemeMode,
                                        onCycleTheme = { cycleTheme() },
                                        onOpenNotifications = { overlay = LocalOverlay.Notifications },
                                        onOpenMessages = { overlay = LocalOverlay.DmInbox },
                                        onOpenCreateStory = {
                                            createInitialMode = CreateContentMode.STORY
                                            selectedTab = LocalTab.CREATE
                                        },
                                        onOpenStory = { list, idx -> activeStoryViewer = list to idx },
                                        onLikeToggle = { toggleLikePost(it) },
                                        onDoubleTapLike = { if (it.id !in likedIds) toggleLikePost(it) },
                                        onOpenComments = { activeCommentTarget = Triple(it.id, it.userId, false) },
                                        onSaveToggle = { toggleSavePost(it) },
                                        onOpenProfile = { uid ->
                                            if (uid == currentUserId) selectedTab = LocalTab.PROFILE
                                            else overlay = LocalOverlay.UserDetail(uid)
                                        },
                                        onFollowToggle = { toggleFollowProfile(it) },
                                        onHashtagClick = { tag ->
                                            exploreInitialQuery = "#$tag"
                                            selectedTab = LocalTab.EXPLORE
                                        },
                                        onArchiveToggle = { post ->
                                            if (!post.id.startsWith("sample_")) {
                                                posts = posts - post
                                                archivedPosts = listOf(post.copy(visibility = "archived")) + archivedPosts
                                                localStore.saveCustomPosts(posts + archivedPosts)
                                                toastMessage = "Post archived"
                                            }
                                        }
                                    )
                                }

                                LocalTab.EXPLORE -> {
                                    ExploreScreen(
                                        currentUserId = currentUserId,
                                        initialQuery = exploreInitialQuery,
                                        profiles = allProfiles,
                                        posts = posts,
                                        likedIds = likedIds,
                                        savedIds = savedIds,
                                        followingIds = followingIds,
                                        onFollowToggle = { toggleFollowProfile(it) },
                                        onOpenProfile = { uid ->
                                            if (uid == currentUserId) selectedTab = LocalTab.PROFILE
                                            else overlay = LocalOverlay.UserDetail(uid)
                                        },
                                        onLikeToggle = { toggleLikePost(it) },
                                        onDoubleTapLike = { if (it.id !in likedIds) toggleLikePost(it) },
                                        onOpenComments = { activeCommentTarget = Triple(it.id, it.userId, false) },
                                        onSaveToggle = { toggleSavePost(it) }
                                    )
                                }

                                LocalTab.CREATE -> {
                                    CreateContentScreen(
                                        initialMode = createInitialMode,
                                        onPublishPost = { caption, location, tags, mediaType, mediaData, videoUri ->
                                            val newPost = Post(
                                                id = "local_post_${UUID.randomUUID()}",
                                                userId = currentUserId,
                                                authorUsername = myProfile.username,
                                                authorName = myProfile.fullName,
                                                authorAvatar = myProfile.avatarData,
                                                caption = caption,
                                                location = location,
                                                hashtags = Regex("#([A-Za-z0-9_]+)").findAll(caption).map { it.groupValues[1].lowercase() }.toList(),
                                                taggedUsers = tags,
                                                mediaType = mediaType,
                                                mediaData = mediaData,
                                                videoUri = videoUri,
                                                visibility = "public",
                                                createdAt = Timestamp.now()
                                            )
                                            posts = listOf(newPost) + posts
                                            localStore.saveCustomPosts(posts + archivedPosts)
                                            myProfile = myProfile.copy(postsCount = myProfile.postsCount + 1L)
                                            toastMessage = "Post published"
                                            selectedTab = LocalTab.HOME
                                        },
                                        onPublishReel = { caption, audioTitle, videoUri, thumb ->
                                            val newReel = Reel(
                                                id = "local_reel_${UUID.randomUUID()}",
                                                userId = currentUserId,
                                                authorUsername = myProfile.username,
                                                authorAvatar = myProfile.avatarData,
                                                caption = caption,
                                                audioTitle = audioTitle.ifBlank { "${myProfile.username} • Original Audio" },
                                                videoUri = videoUri,
                                                thumbnailData = thumb,
                                                visibility = "public",
                                                createdAt = Timestamp.now()
                                            )
                                            reels = listOf(newReel) + reels
                                            localStore.saveCustomReels(reels)
                                            toastMessage = "Reel published"
                                            selectedTab = LocalTab.REELS
                                        },
                                        onPublishStory = { mediaType, mediaData, textContent, bg ->
                                            val newStory = Story(
                                                id = "local_story_${UUID.randomUUID()}",
                                                userId = currentUserId,
                                                authorUsername = myProfile.username,
                                                authorAvatar = myProfile.avatarData,
                                                mediaType = mediaType,
                                                mediaData = mediaData,
                                                textContent = textContent,
                                                bgGradient = bg,
                                                visibility = "public",
                                                expiresAtMillis = System.currentTimeMillis() + 24L * 3_600_000L,
                                                createdAt = Timestamp.now()
                                            )
                                            stories = listOf(newStory) + stories
                                            localStore.saveCustomStories(stories)
                                            toastMessage = "Story shared for 24 hours"
                                            selectedTab = LocalTab.HOME
                                        }
                                    )
                                }

                                LocalTab.REELS -> {
                                    ReelsScreen(
                                        currentUserId = currentUserId,
                                        reels = reels,
                                        profiles = allProfiles,
                                        likedIds = likedIds,
                                        followingIds = followingIds,
                                        isActiveTab = selectedTab == LocalTab.REELS && overlay is LocalOverlay.None,
                                        onLikeToggle = { reel ->
                                            likedIds = if (reel.id in likedIds) likedIds - reel.id else likedIds + reel.id
                                            localStore.saveUserSet(currentUserId, "liked", likedIds)
                                        },
                                        onOpenComments = { reel -> activeCommentTarget = Triple(reel.id, reel.userId, true) },
                                        onFollowToggle = { toggleFollowProfile(it) },
                                        onOpenProfile = { uid ->
                                            if (uid == currentUserId) selectedTab = LocalTab.PROFILE
                                            else overlay = LocalOverlay.UserDetail(uid)
                                        }
                                    )
                                }

                                LocalTab.PROFILE -> {
                                    ProfileScreen(
                                        profile = myProfile,
                                        isOwnProfile = true,
                                        posts = posts,
                                        savedIds = savedIds,
                                        likedIds = likedIds,
                                        followingIds = followingIds,
                                        sentFollowRequestIds = sentFollowRequestIds,
                                        verifiedUserIds = verifiedUserIds,
                                        pendingRequestsCount = followRequests.size,
                                        themeMode = localThemeMode,
                                        onCycleTheme = { cycleTheme() },
                                        onSaveProfileEdits = { u, f, b, w, a -> saveEditedProfile(u, f, b, w, a) },
                                        onFollowToggle = {},
                                        onMessageUser = {},
                                        onLikeToggle = { toggleLikePost(it) },
                                        onDoubleTapLike = { if (it.id !in likedIds) toggleLikePost(it) },
                                        onOpenComments = { activeCommentTarget = Triple(it.id, it.userId, false) },
                                        onSaveToggle = { toggleSavePost(it) },
                                        onArchiveToggle = { post ->
                                            if (!post.id.startsWith("sample_")) {
                                                posts = posts - post
                                                archivedPosts = listOf(post.copy(visibility = "archived")) + archivedPosts
                                                localStore.saveCustomPosts(posts + archivedPosts)
                                                toastMessage = "Post archived"
                                            }
                                        },
                                        onOpenSettingsAndActivity = { overlay = LocalOverlay.SettingsAndActivity },
                                        savedAccounts = syncedSavedAccounts,
                                        onSwitchAccount = onSwitchAccount,
                                        onAddAccount = onAddAccount
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    activeCommentTarget?.let { (targetId, _, _) ->
        val comments = commentsMap[targetId].orEmpty()
        CommentsBottomSheet(
            comments = comments,
            verifiedUserIds = verifiedUserIds,
            onDismiss = { activeCommentTarget = null },
            onSubmitComment = { text ->
                val trimmed = text.trim()
                if (trimmed.isNotEmpty()) {
                    val newComment = Comment(
                        id = UUID.randomUUID().toString(),
                        postId = targetId,
                        userId = currentUserId,
                        authorUsername = myProfile.username,
                        authorAvatar = myProfile.avatarData,
                        text = trimmed,
                        visibility = "public",
                        createdAt = Timestamp.now()
                    )
                    commentsMap[targetId] = commentsMap[targetId].orEmpty() + newComment
                    userComments = listOf(newComment) + userComments
                    localStore.addComment(newComment)
                }
            }
        )
    }

    activeStoryViewer?.let { (storyList, startIndex) ->
        StoryViewerDialog(
            stories = storyList,
            initialIndex = startIndex,
            onDismiss = { activeStoryViewer = null },
            onSendReply = { story, replyText ->
                val peer = allProfiles.firstOrNull { it.userId == story.userId }
                    ?: UserProfile(
                        userId = story.userId,
                        username = story.authorUsername,
                        fullName = story.authorUsername,
                        avatarData = story.authorAvatar
                    )
                sendLocalDm(peer, "Replied to your story: $replyText")
                toastMessage = "Reply sent to @${peer.username}"
                activeStoryViewer = null
            }
        )
    }
}
