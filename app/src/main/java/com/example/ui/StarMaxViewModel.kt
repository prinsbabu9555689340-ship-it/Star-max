package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.SocialRepository
import com.example.data.firebase.ThemePreferences
import com.example.data.model.ChatMessage
import com.example.data.model.Comment
import com.example.data.model.Conversation
import com.example.data.model.FollowRequest
import com.example.data.model.NotificationItem
import com.example.data.model.Post
import com.example.data.model.Reel
import com.example.data.model.Story
import com.example.data.model.ThemeMode
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.UserSettings
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class StarMaxViewModel(
    private val repository: SocialRepository,
    private val themePreferences: ThemePreferences,
    val currentUserId: String,
    firebaseUser: FirebaseUser?
) : ViewModel() {

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _activeCommentTarget = MutableStateFlow<Pair<String, Boolean>?>(null) // id to isReel
    private val _activeChatConvId = MutableStateFlow<String?>(null)
    private val _localReadNotifIds = MutableStateFlow<Set<String>>(emptySet())
    private val _todayUsageSeconds = MutableStateFlow(0L)
    val todayUsageSeconds: StateFlow<Long> = _todayUsageSeconds.asStateFlow()
    private var syncedThemeFromDb = false
    private var syncedUsageFromDb = false

    val themeMode: StateFlow<ThemeMode> = themePreferences.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ThemeMode.SYSTEM
        )

    val userSettings: StateFlow<UserSettings> =
        repository.observeUserSettings(currentUserId)
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                emit(UserSettings(userId = currentUserId))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UserSettings(userId = currentUserId)
            )

    init {
        if (firebaseUser != null) {
            viewModelScope.launch {
                repository.ensureUserProfile(firebaseUser)
                    .onFailure { _actionError.value = it.localizedMessage }
            }
        }
        viewModelScope.launch {
            themePreferences.dailyUsageSeconds.collect { localSec ->
                if (localSec > _todayUsageSeconds.value) {
                    _todayUsageSeconds.value = localSec
                }
            }
        }
        viewModelScope.launch {
            userSettings.collect { settings ->
                if (!syncedThemeFromDb && settings.createdAt != null) {
                    syncedThemeFromDb = true
                    val remoteTheme = ThemeMode.fromWire(settings.themeMode)
                    if (remoteTheme != themeMode.value) {
                        themePreferences.setThemeMode(remoteTheme)
                    }
                }
                val today = ThemePreferences.todayDateKey()
                if (!syncedUsageFromDb && settings.usageDate == today && settings.dailyUsageSeconds > 0L) {
                    syncedUsageFromDb = true
                    if (settings.dailyUsageSeconds > _todayUsageSeconds.value) {
                        _todayUsageSeconds.value = settings.dailyUsageSeconds
                    }
                }
            }
        }
    }

    private fun <T> Flow<T>.toUiState(label: String): StateFlow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing $label", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load $label"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val profilesState: StateFlow<UiState<List<UserProfile>>> =
        repository.observePublicProfiles(currentUserId).toUiState("profiles")

    val currentUserProfile: StateFlow<UserProfile?> = profilesState
        .map { state ->
            (state as? UiState.Success)?.data?.firstOrNull { it.userId == currentUserId }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    val postsState: StateFlow<UiState<List<Post>>> =
        repository.observePosts(currentUserId).toUiState("posts")

    val storiesState: StateFlow<UiState<List<Story>>> =
        repository.observeStories(currentUserId).toUiState("stories")

    val reelsState: StateFlow<UiState<List<Reel>>> =
        repository.observeReels(currentUserId).toUiState("reels")

    val likedIds: StateFlow<Set<String>> =
        repository.observeLikedIds(currentUserId)
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                emit(emptySet())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptySet())

    val savedIds: StateFlow<Set<String>> =
        repository.observeSavedIds(currentUserId)
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                emit(emptySet())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptySet())

    val followingIds: StateFlow<Set<String>> =
        repository.observeFollowingIds(currentUserId)
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                emit(emptySet())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptySet())

    val notificationsState: StateFlow<UiState<List<NotificationItem>>> =
        combine(
            repository.observeNotifications(currentUserId),
            _localReadNotifIds
        ) { list, localRead ->
            list.map { item -> if (item.id in localRead) item.copy(read = true) else item }
        }.toUiState("notifications")

    val archivedPostsState: StateFlow<UiState<List<Post>>> =
        repository.observeArchivedPosts(currentUserId).toUiState("archived posts")

    val userCommentsState: StateFlow<UiState<List<Comment>>> =
        repository.observeUserComments(currentUserId).toUiState("comments history")

    val followRequestsState: StateFlow<UiState<List<FollowRequest>>> =
        repository.observeFollowRequests(currentUserId).toUiState("follow requests")

    val sentFollowRequestIds: StateFlow<Set<String>> =
        repository.observeSentFollowRequestIds(currentUserId)
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                emit(emptySet())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptySet())

    val unreadNotificationsCount: StateFlow<Int> = combine(
        notificationsState,
        userSettings
    ) { state, settings ->
        val list = (state as? UiState.Success)?.data.orEmpty()
        list.count { item ->
            !item.read && when (item.notifType) {
                "like" -> settings.notifyLikes
                "comment" -> settings.notifyComments
                "follow", "follow_request" -> settings.notifyFollowers
                else -> true
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0)

    val conversationsState: StateFlow<UiState<List<Conversation>>> =
        repository.observeConversations(currentUserId).toUiState("conversations")

    val unreadMessagesCount: StateFlow<Int> = combine(
        conversationsState,
        userSettings
    ) { state, settings ->
        if (!settings.notifyMessages) 0
        else (state as? UiState.Success)?.data?.count { currentUserId in it.unreadBy } ?: 0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0)

    val commentsState: StateFlow<UiState<List<Comment>>> = _activeCommentTarget
        .flatMapLatest { target ->
            if (target == null) flowOf(emptyList())
            else repository.observeComments(target.first, currentUserId)
        }
        .toUiState("comments")

    val messagesState: StateFlow<UiState<List<ChatMessage>>> = _activeChatConvId
        .flatMapLatest { convId ->
            if (convId == null) flowOf(emptyList())
            else repository.observeMessages(convId, currentUserId)
        }
        .toUiState("messages")

    fun setThemeMode(mode: ThemeMode) {
        syncedThemeFromDb = true
        viewModelScope.launch {
            themePreferences.setThemeMode(mode)
            repository.updateThemePreference(mode)
        }
    }

    fun setDarkModeEnabled(enabled: Boolean) {
        setThemeMode(if (enabled) ThemeMode.DARK else ThemeMode.LIGHT)
    }

    fun cycleThemeMode() {
        val next = when (themeMode.value) {
            ThemeMode.SYSTEM -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.SYSTEM
        }
        setThemeMode(next)
    }

    fun setAccountPrivacy(isPrivate: Boolean) {
        viewModelScope.launch {
            repository.updateAccountPrivacy(isPrivate)
                .onSuccess {
                    _actionMessage.value = if (isPrivate) {
                        "Account set to Private (new followers require approval)"
                    } else {
                        "Account set to Public"
                    }
                }
                .onFailure { _actionError.value = it.localizedMessage ?: "Failed to update privacy" }
        }
    }

    fun updateNotificationPreferences(
        notifyLikes: Boolean,
        notifyComments: Boolean,
        notifyFollowers: Boolean,
        notifyMessages: Boolean
    ) {
        viewModelScope.launch {
            repository.updateNotificationPreferences(
                notifyLikes = notifyLikes,
                notifyComments = notifyComments,
                notifyFollowers = notifyFollowers,
                notifyMessages = notifyMessages
            ).onFailure {
                _actionError.value = it.localizedMessage ?: "Failed to save notification settings"
            }
        }
    }

    fun toggleArchivePost(post: Post) {
        val currentlyArchived = post.visibility == "archived"
        viewModelScope.launch {
            repository.toggleArchivePost(post.id, currentlyArchived)
                .onSuccess {
                    _actionMessage.value = if (currentlyArchived) {
                        "Post restored to your profile"
                    } else {
                        "Post moved to Archive"
                    }
                }
                .onFailure { _actionError.value = it.localizedMessage ?: "Unable to update post archive" }
        }
    }

    fun approveFollowRequest(request: FollowRequest) {
        viewModelScope.launch {
            repository.approveFollowRequest(request, currentUserProfile.value)
                .onSuccess {
                    _actionMessage.value = "Approved @${request.requesterUsername}"
                }
                .onFailure { _actionError.value = it.localizedMessage }
        }
    }

    fun declineFollowRequest(requesterId: String) {
        viewModelScope.launch {
            repository.declineFollowRequest(requesterId)
                .onFailure { _actionError.value = it.localizedMessage }
        }
    }

    fun recordActiveSecond() {
        val next = _todayUsageSeconds.value + 1L
        _todayUsageSeconds.value = next
        if (next % 15L == 0L) {
            flushDailyUsage()
        }
    }

    fun flushDailyUsage() {
        val seconds = _todayUsageSeconds.value
        val today = ThemePreferences.todayDateKey()
        viewModelScope.launch {
            themePreferences.setDailyUsage(today, seconds)
            repository.saveDailyUsage(today, seconds)
        }
    }

    fun openComments(targetId: String, isReel: Boolean) {
        _activeCommentTarget.value = targetId to isReel
    }

    fun closeComments() {
        _activeCommentTarget.value = null
    }

    fun openConversation(peerId: String): String {
        val convId = repository.conversationIdFor(currentUserId, peerId)
        _activeChatConvId.value = convId
        viewModelScope.launch {
            repository.markConversationRead(convId)
        }
        return convId
    }

    fun closeConversation() {
        _activeChatConvId.value = null
    }

    fun toggleLikePost(post: Post) {
        val isLiked = post.id in likedIds.value
        viewModelScope.launch {
            repository.toggleLike(
                targetId = post.id,
                targetType = "post",
                targetOwnerId = post.userId,
                currentlyLiked = isLiked,
                actor = currentUserProfile.value
            ).onFailure { _actionError.value = it.localizedMessage }
        }
    }

    fun likePostIfNotLiked(post: Post) {
        if (post.id !in likedIds.value) {
            toggleLikePost(post)
        }
    }

    fun toggleLikeReel(reel: Reel) {
        val isLiked = reel.id in likedIds.value
        viewModelScope.launch {
            repository.toggleLike(
                targetId = reel.id,
                targetType = "reel",
                targetOwnerId = reel.userId,
                currentlyLiked = isLiked,
                actor = currentUserProfile.value
            ).onFailure { _actionError.value = it.localizedMessage }
        }
    }

    fun toggleSavePost(postId: String) {
        val isSaved = postId in savedIds.value
        viewModelScope.launch {
            repository.toggleSave(postId, isSaved)
                .onSuccess {
                    _actionMessage.value = if (isSaved) "Removed from Saved" else "Saved to your collection"
                }
                .onFailure { _actionError.value = it.localizedMessage }
        }
    }

    fun toggleFollow(target: UserProfile) {
        val isFollowing = target.userId in followingIds.value
        val isRequested = target.userId in sentFollowRequestIds.value
        viewModelScope.launch {
            repository.toggleFollow(target, isFollowing, isRequested, currentUserProfile.value)
                .onSuccess {
                    if (!isFollowing && !isRequested && target.isPrivate && !target.userId.startsWith("sample_")) {
                        _actionMessage.value = "Follow request sent to @${target.username}"
                    }
                }
                .onFailure { _actionError.value = it.localizedMessage }
        }
    }

    fun postComment(targetId: String, ownerId: String, isReel: Boolean, text: String) {
        val actor = currentUserProfile.value ?: UserProfile(
            userId = currentUserId,
            username = "star_user",
            fullName = "Star Creator"
        )
        viewModelScope.launch {
            repository.addComment(targetId, ownerId, isReel, text, actor)
                .onFailure { _actionError.value = it.localizedMessage }
        }
    }

    fun publishPost(
        caption: String,
        location: String,
        taggedUsers: List<String>,
        mediaType: String,
        mediaData: String,
        videoUri: String = "",
        onDone: () -> Unit
    ) {
        val author = currentUserProfile.value ?: UserProfile(
            userId = currentUserId,
            username = "star_user",
            fullName = "Star Creator"
        )
        viewModelScope.launch {
            repository.createPost(author, caption, location, taggedUsers, mediaType, mediaData, videoUri)
                .onSuccess {
                    _actionMessage.value = "Post shared to Star Max!"
                    onDone()
                }
                .onFailure { _actionError.value = it.localizedMessage ?: "Failed to publish post" }
        }
    }

    fun publishReel(
        caption: String,
        audioTitle: String,
        videoUri: String,
        thumbnailData: String,
        onDone: () -> Unit
    ) {
        val author = currentUserProfile.value ?: UserProfile(
            userId = currentUserId,
            username = "star_user",
            fullName = "Star Creator"
        )
        viewModelScope.launch {
            repository.createReel(author, caption, audioTitle, videoUri, thumbnailData)
                .onSuccess {
                    _actionMessage.value = "Reel published!"
                    onDone()
                }
                .onFailure { _actionError.value = it.localizedMessage ?: "Failed to publish reel" }
        }
    }

    fun publishStory(
        mediaType: String,
        mediaData: String,
        textContent: String,
        bgGradient: String,
        onDone: () -> Unit
    ) {
        val author = currentUserProfile.value ?: UserProfile(
            userId = currentUserId,
            username = "star_user",
            fullName = "Star Creator"
        )
        viewModelScope.launch {
            repository.createStory(author, mediaType, mediaData, textContent, bgGradient)
                .onSuccess {
                    _actionMessage.value = "Story live for 24 hours!"
                    onDone()
                }
                .onFailure { _actionError.value = it.localizedMessage ?: "Failed to post story" }
        }
    }

    fun updateProfile(
        username: String,
        fullName: String,
        bio: String,
        website: String,
        avatarData: String?,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            repository.updateProfile(username, fullName, bio, website, avatarData)
                .onSuccess {
                    _actionMessage.value = "Profile updated"
                    onDone()
                }
                .onFailure { _actionError.value = it.localizedMessage ?: "Failed to update profile" }
        }
    }

    fun markNotificationRead(notifId: String) {
        if (notifId.startsWith("sample_")) {
            _localReadNotifIds.value = _localReadNotifIds.value + notifId
            return
        }
        viewModelScope.launch {
            repository.markNotificationRead(notifId)
        }
    }

    fun sendMessage(peerProfile: UserProfile, text: String) {
        val myProfile = currentUserProfile.value ?: UserProfile(
            userId = currentUserId,
            username = "star_user",
            fullName = "Star Creator"
        )
        viewModelScope.launch {
            repository.sendMessage(myProfile, peerProfile, text)
                .onSuccess { convId ->
                    _activeChatConvId.value = convId
                }
                .onFailure { _actionError.value = it.localizedMessage ?: "Failed to send message" }
        }
    }

    fun clearFeedback() {
        _actionError.value = null
        _actionMessage.value = null
    }

    private companion object {
        const val TAG = "StarMaxViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
