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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.firebase.AccountSessionManager
import com.example.data.firebase.SocialRepository
import com.example.data.firebase.ThemePreferences
import com.example.data.firebase.authStateFlow
import com.example.data.model.SavedAccountRecord
import com.example.data.model.Story
import com.example.data.model.ThemeMode
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.components.CommentsBottomSheet
import com.example.ui.screens.AuthScreen
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
import com.example.ui.theme.StarMaxTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive

private enum class BottomTab(
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

private sealed interface OverlayRoute {
    data object None : OverlayRoute
    data object Notifications : OverlayRoute
    data object DmInbox : OverlayRoute
    data object SettingsAndActivity : OverlayRoute
    data class ChatThread(val peer: UserProfile) : OverlayRoute
    data class UserDetail(val userId: String) : OverlayRoute
}

@Composable
fun AppNavigation(authOverride: FirebaseAuth? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) {
        runCatching { CredentialManager.create(context) }.getOrNull()
    }
    val themePreferences = remember(context) {
        runCatching { ThemePreferences(context.applicationContext ?: context) }.getOrNull()
    }
    val persistedThemeFlow = remember(themePreferences) {
        themePreferences?.themeMode?.catch { emit(ThemeMode.SYSTEM) } ?: flowOf(ThemeMode.SYSTEM)
    }
    val persistedTheme by persistedThemeFlow.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
    var fallbackThemeMode by remember { mutableStateOf(ThemeMode.SYSTEM) }

    val sessionManager = remember(context) {
        AccountSessionManager(context.applicationContext ?: context)
    }
    val isFirebaseReady = remember(sessionManager, authOverride) {
        authOverride != null || sessionManager.isFirebaseConfigured()
    }
    val savedAccounts by sessionManager.savedAccounts.collectAsStateWithLifecycle()
    val activeUid by sessionManager.activeUid.collectAsStateWithLifecycle()

    var demoModeActive by remember(isFirebaseReady) {
        mutableStateOf(!isFirebaseReady)
    }
    var showAddAccountScreen by remember { mutableStateOf(false) }

    val activeTheme = if (themePreferences != null) persistedTheme else fallbackThemeMode

    StarMaxTheme(themeMode = activeTheme) {
        if (!isFirebaseReady || demoModeActive) {
            LocalDemoShell(
                themePreferences = themePreferences,
                currentThemeMode = activeTheme,
                canReturnToSignIn = isFirebaseReady,
                onThemeModeSelected = { fallbackThemeMode = it },
                onExitDemo = { demoModeActive = false }
            )
        } else if (showAddAccountScreen) {
            AuthScreen(
                accountSessionManager = sessionManager,
                isAddAccountMode = true,
                onAccountAuthenticated = { record ->
                    showAddAccountScreen = false
                    sessionManager.saveAccount(record, makeActive = true)
                },
                onCancelAddAccount = {
                    showAddAccountScreen = false
                }
            )
        } else {
            val activeRecord = remember(activeUid, savedAccounts) {
                activeUid?.let { uid -> savedAccounts.firstOrNull { it.uid == uid } }
            }
            val slotAuth = remember(activeRecord, authOverride) {
                authOverride ?: activeRecord?.let { sessionManager.resolveAuth(it.slotName) }
            }
            val slotDb = remember(activeRecord) {
                activeRecord?.let { sessionManager.resolveFirestore(it.slotName) }
            }

            if (activeRecord == null || slotAuth == null || slotDb == null) {
                AuthScreen(
                    accountSessionManager = sessionManager,
                    isAddAccountMode = false,
                    onAccountAuthenticated = { record ->
                        sessionManager.saveAccount(record, makeActive = true)
                    },
                    onEnterDemoMode = { demoModeActive = true }
                )
            } else {
                val userFlow = remember(slotAuth) {
                    slotAuth.authStateFlow().catch { emit(null) }
                }
                val currentUser by userFlow.collectAsStateWithLifecycle(
                    initialValue = runCatching { slotAuth.currentUser }.getOrNull()
                )
                val user = currentUser
                if (user == null || user.uid != activeRecord.uid) {
                    AuthScreen(
                        accountSessionManager = sessionManager,
                        isAddAccountMode = false,
                        onAccountAuthenticated = { record ->
                            sessionManager.saveAccount(record, makeActive = true)
                        },
                        onEnterDemoMode = { demoModeActive = true }
                    )
                } else {
                    key(user.uid) {
                        StarMaxAuthenticatedShell(
                            currentUserId = user.uid,
                            firebaseUser = user,
                            db = slotDb,
                            auth = slotAuth,
                            sessionManager = sessionManager,
                            savedAccounts = savedAccounts,
                            themePreferences = themePreferences ?: ThemePreferences(context.applicationContext ?: context),
                            onSwitchAccount = { targetRecord ->
                                val switched = sessionManager.switchToSavedAccount(targetRecord.uid)
                                if (!switched) {
                                    showAddAccountScreen = true
                                }
                            },
                            onAddAccount = {
                                showAddAccountScreen = true
                            },
                            onSignOut = {
                                sessionManager.logoutActiveAccount(
                                    credentialManager = credentialManager,
                                    scope = scope
                                )
                            },
                            onSignOutAll = {
                                sessionManager.logoutAllAccounts(
                                    credentialManager = credentialManager,
                                    scope = scope
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StarMaxAuthenticatedShell(
    currentUserId: String,
    firebaseUser: FirebaseUser,
    db: FirebaseFirestore,
    auth: FirebaseAuth,
    sessionManager: AccountSessionManager,
    savedAccounts: List<SavedAccountRecord>,
    themePreferences: ThemePreferences,
    onSwitchAccount: (SavedAccountRecord) -> Unit,
    onAddAccount: () -> Unit,
    onSignOut: () -> Unit,
    onSignOutAll: () -> Unit
) {
    val context = LocalContext.current

    val viewModel: StarMaxViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                StarMaxViewModel(
                    repository = SocialRepository(db = db, auth = auth),
                    themePreferences = themePreferences,
                    currentUserId = currentUserId,
                    firebaseUser = firebaseUser
                )
            }
        }
    )

    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val userSettings by viewModel.userSettings.collectAsStateWithLifecycle()
    val todayUsageSeconds by viewModel.todayUsageSeconds.collectAsStateWithLifecycle()
    val profilesState by viewModel.profilesState.collectAsStateWithLifecycle()
    val currentUserProfile by viewModel.currentUserProfile.collectAsStateWithLifecycle()
    val postsState by viewModel.postsState.collectAsStateWithLifecycle()
    val archivedPostsState by viewModel.archivedPostsState.collectAsStateWithLifecycle()
    val userCommentsState by viewModel.userCommentsState.collectAsStateWithLifecycle()
    val followRequestsState by viewModel.followRequestsState.collectAsStateWithLifecycle()
    val sentFollowRequestIds by viewModel.sentFollowRequestIds.collectAsStateWithLifecycle()
    val storiesState by viewModel.storiesState.collectAsStateWithLifecycle()
    val reelsState by viewModel.reelsState.collectAsStateWithLifecycle()
    val likedIds by viewModel.likedIds.collectAsStateWithLifecycle()
    val savedIds by viewModel.savedIds.collectAsStateWithLifecycle()
    val followingIds by viewModel.followingIds.collectAsStateWithLifecycle()
    val notificationsState by viewModel.notificationsState.collectAsStateWithLifecycle()
    val unreadNotifications by viewModel.unreadNotificationsCount.collectAsStateWithLifecycle()
    val conversationsState by viewModel.conversationsState.collectAsStateWithLifecycle()
    val unreadMessages by viewModel.unreadMessagesCount.collectAsStateWithLifecycle()
    val commentsState by viewModel.commentsState.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    var isForeground by remember { mutableStateOf(true) }
    var localToastMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> isForeground = true
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    isForeground = false
                    viewModel.flushDailyUsage()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.flushDailyUsage()
        }
    }

    LaunchedEffect(isForeground) {
        while (isForeground && isActive) {
            delay(1000L)
            viewModel.recordActiveSecond()
        }
    }

    LaunchedEffect(actionError, actionMessage, localToastMessage) {
        val msg = actionError ?: actionMessage ?: localToastMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
            localToastMessage = null
        }
    }

    LaunchedEffect(currentUserProfile) {
        val p = currentUserProfile
        if (p != null && p.userId == currentUserId) {
            sessionManager.updateAccountProfile(
                uid = currentUserId,
                username = p.username,
                fullName = p.fullName,
                avatarData = p.avatarData,
                verified = p.verified
            )
        }
    }

    var selectedTab by remember { mutableStateOf(BottomTab.HOME) }
    var overlay by remember { mutableStateOf<OverlayRoute>(OverlayRoute.None) }
    var exploreInitialQuery by remember { mutableStateOf("") }
    var createInitialMode by remember { mutableStateOf(CreateContentMode.POST) }
    var activeCommentTarget by remember { mutableStateOf<Triple<String, String, Boolean>?>(null) } // id, ownerId, isReel
    var activeStoryViewer by remember { mutableStateOf<Pair<List<Story>, Int>?>(null) }

    val profiles = (profilesState as? UiState.Success)?.data.orEmpty()
    val posts = (postsState as? UiState.Success)?.data.orEmpty()
    val archivedPosts = (archivedPostsState as? UiState.Success)?.data.orEmpty()
    val userComments = (userCommentsState as? UiState.Success)?.data.orEmpty()
    val followRequests = (followRequestsState as? UiState.Success)?.data.orEmpty()
    val reels = (reelsState as? UiState.Success)?.data.orEmpty()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 680.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (!useRail && overlay is OverlayRoute.None) {
                    val isReels = selectedTab == BottomTab.REELS
                    NavigationBar(
                        containerColor = if (isReels) Color.Black else MaterialTheme.colorScheme.surface,
                        contentColor = if (isReels) Color.White else MaterialTheme.colorScheme.onSurface,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        BottomTab.entries.forEach { tab ->
                            val isSelected = selectedTab == tab
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    if (tab == BottomTab.CREATE) {
                                        createInitialMode = CreateContentMode.POST
                                    }
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
                if (useRail && overlay is OverlayRoute.None) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
                    ) {
                        BottomTab.entries.forEach { tab ->
                            val isSelected = selectedTab == tab
                            NavigationRailItem(
                                selected = isSelected,
                                onClick = {
                                    if (tab == BottomTab.CREATE) {
                                        createInitialMode = CreateContentMode.POST
                                    }
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
                            if (selectedTab != BottomTab.REELS || overlay !is OverlayRoute.None) {
                                Modifier.windowInsetsPadding(WindowInsets.statusBars)
                            } else {
                                Modifier
                            }
                        )
                ) {
                    when (val currentOverlay = overlay) {
                        is OverlayRoute.Notifications -> {
                            NotificationsScreen(
                                notificationsState = notificationsState,
                                onMarkRead = { viewModel.markNotificationRead(it) },
                                onOpenProfile = { uid ->
                                    overlay = if (uid == currentUserId) {
                                        selectedTab = BottomTab.PROFILE
                                        OverlayRoute.None
                                    } else {
                                        OverlayRoute.UserDetail(uid)
                                    }
                                },
                                onBack = { overlay = OverlayRoute.None }
                            )
                        }

                        is OverlayRoute.DmInbox -> {
                            DmInboxScreen(
                                currentUserId = currentUserId,
                                profiles = profiles,
                                conversationsState = conversationsState,
                                onOpenChat = { peer ->
                                    viewModel.openConversation(peer.userId)
                                    overlay = OverlayRoute.ChatThread(peer)
                                },
                                onBack = { overlay = OverlayRoute.None }
                            )
                        }

                        is OverlayRoute.ChatThread -> {
                            ChatThreadScreen(
                                currentUserId = currentUserId,
                                peerProfile = currentOverlay.peer,
                                messagesState = messagesState,
                                onSendMessage = { msgText ->
                                    viewModel.sendMessage(currentOverlay.peer, msgText)
                                },
                                onOpenProfile = { uid ->
                                    overlay = OverlayRoute.UserDetail(uid)
                                },
                                onBack = {
                                    viewModel.closeConversation()
                                    overlay = OverlayRoute.DmInbox
                                }
                            )
                        }

                        is OverlayRoute.SettingsAndActivity -> {
                            val myProfile = currentUserProfile ?: UserProfile(
                                userId = currentUserId,
                                username = firebaseUser.displayName?.lowercase()?.replace(" ", "_") ?: "star_user",
                                fullName = firebaseUser.displayName ?: "Star Creator",
                                avatarData = firebaseUser.photoUrl?.toString().orEmpty(),
                                isPrivate = userSettings.isPrivate
                            )
                            SettingsAndActivityScreen(
                                currentUserId = currentUserId,
                                userEmail = firebaseUser.email.orEmpty(),
                                userPhone = firebaseUser.phoneNumber.orEmpty().ifBlank { userSettings.phoneE164 },
                                savedAccountsCount = savedAccounts.size.coerceAtLeast(1),
                                activeAuth = auth,
                                profile = myProfile,
                                userSettings = userSettings,
                                themeMode = themeMode,
                                todayUsageSeconds = todayUsageSeconds,
                                posts = posts,
                                archivedPosts = archivedPosts,
                                userComments = userComments,
                                followRequests = followRequests,
                                likedIds = likedIds,
                                savedIds = savedIds,
                                followingIds = followingIds,
                                onBack = { overlay = OverlayRoute.None },
                                onSetDarkModeEnabled = { viewModel.setDarkModeEnabled(it) },
                                onSetThemeMode = { viewModel.setThemeMode(it) },
                                onSetAccountPrivacy = { viewModel.setAccountPrivacy(it) },
                                onUpdateNotificationPrefs = { likes, comments, followers, messages ->
                                    viewModel.updateNotificationPreferences(likes, comments, followers, messages)
                                },
                                onSaveProfileEdits = { u, f, b, w, a ->
                                    viewModel.updateProfile(u, f, b, w, a) {}
                                },
                                onUpdateAppPassword = { newPwd, onResult ->
                                    viewModel.updateRegisteredPassword(newPwd, onResult)
                                },
                                onLikeToggle = { viewModel.toggleLikePost(it) },
                                onDoubleTapLike = { viewModel.likePostIfNotLiked(it) },
                                onOpenComments = { post ->
                                    viewModel.openComments(post.id, isReel = false)
                                    activeCommentTarget = Triple(post.id, post.userId, false)
                                },
                                onSaveToggle = { viewModel.toggleSavePost(it) },
                                onToggleArchivePost = { viewModel.toggleArchivePost(it) },
                                onFollowToggle = { uid ->
                                    val target = profiles.firstOrNull { it.userId == uid }
                                    if (target != null) viewModel.toggleFollow(target)
                                },
                                onApproveFollowRequest = { viewModel.approveFollowRequest(it) },
                                onDeclineFollowRequest = { viewModel.declineFollowRequest(it) },
                                onShowMessage = { localToastMessage = it },
                                onSignOut = {
                                    viewModel.flushDailyUsage()
                                    onSignOut()
                                },
                                onSignOutAll = {
                                    viewModel.flushDailyUsage()
                                    onSignOutAll()
                                }
                            )
                        }

                        is OverlayRoute.UserDetail -> {
                            val targetProfile = profiles.firstOrNull { it.userId == currentOverlay.userId }
                                ?: UserProfile(
                                    userId = currentOverlay.userId,
                                    username = "creator",
                                    fullName = "Star Max Creator"
                                )
                            ProfileScreen(
                                profile = targetProfile,
                                isOwnProfile = currentOverlay.userId == currentUserId,
                                posts = posts,
                                savedIds = savedIds,
                                likedIds = likedIds,
                                followingIds = followingIds,
                                sentFollowRequestIds = sentFollowRequestIds,
                                themeMode = themeMode,
                                onCycleTheme = { viewModel.cycleThemeMode() },
                                onSaveProfileEdits = { u, f, b, w, a ->
                                    viewModel.updateProfile(u, f, b, w, a) {}
                                },
                                onFollowToggle = { viewModel.toggleFollow(it) },
                                onMessageUser = { peer ->
                                    viewModel.openConversation(peer.userId)
                                    overlay = OverlayRoute.ChatThread(peer)
                                },
                                onLikeToggle = { viewModel.toggleLikePost(it) },
                                onDoubleTapLike = { viewModel.likePostIfNotLiked(it) },
                                onOpenComments = { post ->
                                    viewModel.openComments(post.id, isReel = false)
                                    activeCommentTarget = Triple(post.id, post.userId, false)
                                },
                                onSaveToggle = { viewModel.toggleSavePost(it) },
                                onArchiveToggle = { viewModel.toggleArchivePost(it) },
                                onOpenSettingsAndActivity = { overlay = OverlayRoute.SettingsAndActivity },
                                onBack = { overlay = OverlayRoute.None }
                            )
                        }

                        is OverlayRoute.None -> {
                            when (selectedTab) {
                                BottomTab.HOME -> {
                                    HomeFeedScreen(
                                        currentUserId = currentUserId,
                                        currentUserProfile = currentUserProfile,
                                        postsState = postsState,
                                        storiesState = storiesState,
                                        profiles = profiles,
                                        likedIds = likedIds,
                                        savedIds = savedIds,
                                        followingIds = followingIds,
                                        unreadNotifications = unreadNotifications,
                                        unreadMessages = unreadMessages,
                                        themeMode = themeMode,
                                        onCycleTheme = { viewModel.cycleThemeMode() },
                                        onOpenNotifications = { overlay = OverlayRoute.Notifications },
                                        onOpenMessages = { overlay = OverlayRoute.DmInbox },
                                        onOpenCreateStory = {
                                            createInitialMode = CreateContentMode.STORY
                                            selectedTab = BottomTab.CREATE
                                        },
                                        onOpenStory = { list, idx ->
                                            activeStoryViewer = list to idx
                                        },
                                        onLikeToggle = { viewModel.toggleLikePost(it) },
                                        onDoubleTapLike = { viewModel.likePostIfNotLiked(it) },
                                        onOpenComments = { post ->
                                            viewModel.openComments(post.id, isReel = false)
                                            activeCommentTarget = Triple(post.id, post.userId, false)
                                        },
                                        onSaveToggle = { viewModel.toggleSavePost(it) },
                                        onOpenProfile = { uid ->
                                            if (uid == currentUserId) {
                                                selectedTab = BottomTab.PROFILE
                                            } else {
                                                overlay = OverlayRoute.UserDetail(uid)
                                            }
                                        },
                                        onFollowToggle = { viewModel.toggleFollow(it) },
                                        onHashtagClick = { tag ->
                                            exploreInitialQuery = "#$tag"
                                            selectedTab = BottomTab.EXPLORE
                                        },
                                        onArchiveToggle = { viewModel.toggleArchivePost(it) }
                                    )
                                }

                                BottomTab.EXPLORE -> {
                                    ExploreScreen(
                                        currentUserId = currentUserId,
                                        initialQuery = exploreInitialQuery,
                                        profiles = profiles,
                                        posts = posts,
                                        likedIds = likedIds,
                                        savedIds = savedIds,
                                        followingIds = followingIds,
                                        onFollowToggle = { viewModel.toggleFollow(it) },
                                        onOpenProfile = { uid ->
                                            if (uid == currentUserId) {
                                                selectedTab = BottomTab.PROFILE
                                            } else {
                                                overlay = OverlayRoute.UserDetail(uid)
                                            }
                                        },
                                        onLikeToggle = { viewModel.toggleLikePost(it) },
                                        onDoubleTapLike = { viewModel.likePostIfNotLiked(it) },
                                        onOpenComments = { post ->
                                            viewModel.openComments(post.id, isReel = false)
                                            activeCommentTarget = Triple(post.id, post.userId, false)
                                        },
                                        onSaveToggle = { viewModel.toggleSavePost(it) }
                                    )
                                }

                                BottomTab.CREATE -> {
                                    CreateContentScreen(
                                        initialMode = createInitialMode,
                                        onPublishPost = { caption, location, tags, mediaType, mediaData, videoUri ->
                                            viewModel.publishPost(caption, location, tags, mediaType, mediaData, videoUri) {
                                                selectedTab = BottomTab.HOME
                                            }
                                        },
                                        onPublishReel = { caption, audioTitle, videoUri, thumb ->
                                            viewModel.publishReel(caption, audioTitle, videoUri, thumb) {
                                                selectedTab = BottomTab.REELS
                                            }
                                        },
                                        onPublishStory = { mediaType, mediaData, textContent, bg ->
                                            viewModel.publishStory(mediaType, mediaData, textContent, bg) {
                                                selectedTab = BottomTab.HOME
                                            }
                                        }
                                    )
                                }

                                BottomTab.REELS -> {
                                    ReelsScreen(
                                        currentUserId = currentUserId,
                                        reels = reels,
                                        profiles = profiles,
                                        likedIds = likedIds,
                                        followingIds = followingIds,
                                        isActiveTab = selectedTab == BottomTab.REELS && overlay is OverlayRoute.None,
                                        onLikeToggle = { viewModel.toggleLikeReel(it) },
                                        onOpenComments = { reel ->
                                            viewModel.openComments(reel.id, isReel = true)
                                            activeCommentTarget = Triple(reel.id, reel.userId, true)
                                        },
                                        onFollowToggle = { viewModel.toggleFollow(it) },
                                        onOpenProfile = { uid ->
                                            if (uid == currentUserId) {
                                                selectedTab = BottomTab.PROFILE
                                            } else {
                                                overlay = OverlayRoute.UserDetail(uid)
                                            }
                                        }
                                    )
                                }

                                BottomTab.PROFILE -> {
                                    val myProfile = currentUserProfile ?: UserProfile(
                                        userId = currentUserId,
                                        username = firebaseUser.displayName?.lowercase()?.replace(" ", "_") ?: "star_user",
                                        fullName = firebaseUser.displayName ?: "Star Creator",
                                        avatarData = firebaseUser.photoUrl?.toString().orEmpty(),
                                        isPrivate = userSettings.isPrivate
                                    )
                                    ProfileScreen(
                                        profile = myProfile,
                                        isOwnProfile = true,
                                        posts = posts,
                                        savedIds = savedIds,
                                        likedIds = likedIds,
                                        followingIds = followingIds,
                                        sentFollowRequestIds = sentFollowRequestIds,
                                        pendingRequestsCount = followRequests.size,
                                        themeMode = themeMode,
                                        onCycleTheme = { viewModel.cycleThemeMode() },
                                        onSaveProfileEdits = { u, f, b, w, a ->
                                            viewModel.updateProfile(u, f, b, w, a) {}
                                        },
                                        onFollowToggle = {},
                                        onMessageUser = {},
                                        onLikeToggle = { viewModel.toggleLikePost(it) },
                                        onDoubleTapLike = { viewModel.likePostIfNotLiked(it) },
                                        onOpenComments = { post ->
                                            viewModel.openComments(post.id, isReel = false)
                                            activeCommentTarget = Triple(post.id, post.userId, false)
                                        },
                                        onSaveToggle = { viewModel.toggleSavePost(it) },
                                        onArchiveToggle = { viewModel.toggleArchivePost(it) },
                                        onOpenSettingsAndActivity = { overlay = OverlayRoute.SettingsAndActivity },
                                        savedAccounts = savedAccounts,
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

    // Comments Bottom Sheet
    activeCommentTarget?.let { (targetId, ownerId, isReel) ->
        val comments = (commentsState as? UiState.Success)?.data.orEmpty()
        CommentsBottomSheet(
            comments = comments,
            onDismiss = {
                activeCommentTarget = null
                viewModel.closeComments()
            },
            onSubmitComment = { commentText ->
                viewModel.postComment(targetId, ownerId, isReel, commentText)
            }
        )
    }

    // Full-Screen 24h Story Viewer
    activeStoryViewer?.let { (stories, startIndex) ->
        StoryViewerDialog(
            stories = stories,
            initialIndex = startIndex,
            onDismiss = { activeStoryViewer = null },
            onSendReply = { story, replyText ->
                val peer = profiles.firstOrNull { it.userId == story.userId }
                    ?: UserProfile(
                        userId = story.userId,
                        username = story.authorUsername,
                        fullName = story.authorUsername,
                        avatarData = story.authorAvatar
                    )
                viewModel.sendMessage(peer, "Replied to your story: $replyText")
                activeStoryViewer = null
            }
        )
    }
}
