package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.model.ChatMessage
import com.example.data.model.Comment
import com.example.data.model.Conversation
import com.example.data.model.FollowRequest
import com.example.data.model.NotificationItem
import com.example.data.model.Post
import com.example.data.model.Reel
import com.example.data.model.SampleData
import com.example.data.model.Story
import com.example.data.model.ThemeMode
import com.example.data.model.UserProfile
import com.example.data.model.UserSettings
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

private const val TAG = "SocialRepository"

class SocialRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth? = null
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        null
    )

    private fun resolveAuth(): FirebaseAuth? = auth ?: runCatching { Firebase.auth }.getOrNull()

    private fun requireUserId(): String {
        return resolveAuth()?.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun <T> Flow<T>.handleStreamErrors(op: OperationType, path: String, uid: String): Flow<T> =
        this.catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val exceptionToLog = firestoreError ?: (error as? Exception)
            if (exceptionToLog != null && resolveAuth()?.currentUser?.uid == uid) {
                handleFirestoreError(exceptionToLog, op, path)
            }
            throw error
        }

    suspend fun ensureUserProfile(user: FirebaseUser): Result<Unit> {
        val uid = user.uid
        var opType = OperationType.GET
        var opPath = "users/$uid"
        return try {
            val userDocRef = db.collection("users").document(uid)
            val userSnap = userDocRef.get().await()
            if (!userSnap.exists()) {
                opType = OperationType.CREATE
                opPath = userDocRef.path
                userDocRef.set(
                    mapOf(
                        "userId" to uid,
                        "themeMode" to ThemeMode.SYSTEM.wireValue,
                        "isPrivate" to false,
                        "notifyLikes" to true,
                        "notifyComments" to true,
                        "notifyFollowers" to true,
                        "notifyMessages" to true,
                        "dailyUsageSeconds" to 0L,
                        "usageDate" to ThemePreferences.todayDateKey(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val profileDocRef = db.collection("profiles").document(uid)
            opType = OperationType.GET
            opPath = profileDocRef.path
            val profileSnap = profileDocRef.get().await()
            if (!profileSnap.exists()) {
                val rawName = user.displayName?.takeIf { it.isNotBlank() } ?: "Star Creator"
                val baseHandle = rawName.lowercase()
                    .replace(Regex("[^a-z0-9._]"), "")
                    .ifBlank { "star_${uid.take(6).lowercase()}" }
                    .take(24)
                    .let { if (it.length < 3) "${it}_star" else it }
                val avatarUrl = user.photoUrl?.toString().orEmpty()

                val profilePayload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "username" to baseHandle,
                    "usernameLower" to baseHandle.lowercase(),
                    "fullName" to rawName.take(60),
                    "bio" to "Capturing moments on Star Max ✨",
                    "website" to "",
                    "avatarTint" to "#E1306C",
                    "visibility" to "public",
                    "isPrivate" to false,
                    "postsCount" to 0L,
                    "followersCount" to 128L,
                    "followingCount" to 2L,
                    "verified" to false,
                    "online" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (avatarUrl.isNotBlank() && avatarUrl.length <= 350000) {
                    profilePayload["avatarData"] = avatarUrl
                }
                opType = OperationType.CREATE
                opPath = profileDocRef.path
                profileDocRef.set(profilePayload).await()

                // Auto-follow 2 featured starter creators so the Home Feed is lively on first launch
                val followingCol = db.collection("users").document(uid).collection("following")
                opPath = "users/$uid/following/sample_kai"
                followingCol.document("sample_kai").set(
                    mapOf(
                        "targetUserId" to "sample_kai",
                        "targetUsername" to "kai_tokyo",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                opPath = "users/$uid/following/sample_maya"
                followingCol.document("sample_maya").set(
                    mapOf(
                        "targetUserId" to "sample_maya",
                        "targetUsername" to "maya.lens",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                opType = OperationType.UPDATE
                opPath = profileDocRef.path
                profileDocRef.update(
                    mapOf(
                        "online" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "ensureUserProfile failed at $opPath", e)
            handleFirestoreError(e, opType, opPath)
            Result.failure(e)
        }
    }

    fun observePublicProfiles(uid: String): Flow<List<UserProfile>> =
        db.collection("profiles")
            .whereEqualTo("visibility", "public")
            .snapshots()
            .map { snapshot ->
                val remote = snapshot.toObjects(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                val remoteIds = remote.mapTo(HashSet()) { it.userId }
                remote + SampleData.sampleProfiles.filter { it.userId !in remoteIds }
            }
            .handleStreamErrors(OperationType.LIST, "profiles", uid)

    fun observePosts(uid: String): Flow<List<Post>> =
        db.collection("posts")
            .whereEqualTo("visibility", "public")
            .snapshots()
            .map { snapshot ->
                val remote = snapshot.toObjects(
                    Post::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                (remote + SampleData.samplePosts)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .handleStreamErrors(OperationType.LIST, "posts", uid)

    fun observeStories(uid: String): Flow<List<Story>> =
        db.collection("stories")
            .whereEqualTo("visibility", "public")
            .snapshots()
            .map { snapshot ->
                val now = System.currentTimeMillis()
                val remote = snapshot.toObjects(
                    Story::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).filter { it.expiresAtMillis > now }
                (remote + SampleData.sampleStories)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .handleStreamErrors(OperationType.LIST, "stories", uid)

    fun observeReels(uid: String): Flow<List<Reel>> =
        db.collection("reels")
            .whereEqualTo("visibility", "public")
            .snapshots()
            .map { snapshot ->
                val remote = snapshot.toObjects(
                    Reel::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                (remote + SampleData.sampleReels)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .handleStreamErrors(OperationType.LIST, "reels", uid)

    fun observeComments(postId: String, uid: String): Flow<List<Comment>> =
        db.collection("comments")
            .whereEqualTo("visibility", "public")
            .whereEqualTo("postId", postId)
            .snapshots()
            .map { snapshot ->
                val remote = snapshot.toObjects(
                    Comment::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                val starter = SampleData.sampleCommentsByPost[postId].orEmpty()
                (starter + remote).sortedBy { it.createdAt ?: Timestamp.now() }
            }
            .handleStreamErrors(OperationType.LIST, "comments", uid)

    fun observeLikedIds(uid: String): Flow<Set<String>> =
        db.collection("users").document(uid).collection("likes")
            .snapshots()
            .map { snapshot -> snapshot.documents.mapTo(HashSet()) { it.id } }
            .handleStreamErrors(OperationType.LIST, "users/$uid/likes", uid)

    fun observeSavedIds(uid: String): Flow<Set<String>> =
        db.collection("users").document(uid).collection("saved")
            .snapshots()
            .map { snapshot -> snapshot.documents.mapTo(HashSet()) { it.id } }
            .handleStreamErrors(OperationType.LIST, "users/$uid/saved", uid)

    fun observeFollowingIds(uid: String): Flow<Set<String>> =
        db.collection("users").document(uid).collection("following")
            .snapshots()
            .map { snapshot -> snapshot.documents.mapTo(HashSet()) { it.id } }
            .handleStreamErrors(OperationType.LIST, "users/$uid/following", uid)

    fun observeNotifications(uid: String): Flow<List<NotificationItem>> =
        db.collection("users").document(uid).collection("notifications")
            .snapshots()
            .map { snapshot ->
                val remote = snapshot.toObjects(
                    NotificationItem::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                (remote + SampleData.sampleNotifications)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .handleStreamErrors(OperationType.LIST, "users/$uid/notifications", uid)

    fun observeConversations(uid: String): Flow<List<Conversation>> =
        db.collection("conversations")
            .whereArrayContains("participantUids", uid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    Conversation::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.updatedAt ?: Timestamp(0, 0) }
            }
            .handleStreamErrors(OperationType.LIST, "conversations", uid)

    fun observeMessages(convId: String, uid: String): Flow<List<ChatMessage>> =
        db.collection("conversations").document(convId).collection("messages")
            .whereArrayContains("participantUids", uid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    ChatMessage::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedBy { it.createdAt ?: Timestamp.now() }
            }
            .handleStreamErrors(OperationType.LIST, "conversations/$convId/messages", uid)

    fun observeUserSettings(uid: String): Flow<UserSettings> =
        db.collection("users").document(uid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObject(UserSettings::class.java) ?: UserSettings(userId = uid)
            }
            .handleStreamErrors(OperationType.GET, "users/$uid", uid)

    fun observeArchivedPosts(uid: String): Flow<List<Post>> =
        db.collection("posts")
            .whereEqualTo("userId", uid)
            .whereEqualTo("visibility", "archived")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    Post::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
            }
            .handleStreamErrors(OperationType.LIST, "posts", uid)

    fun observeUserComments(uid: String): Flow<List<Comment>> =
        db.collection("comments")
            .whereEqualTo("visibility", "public")
            .whereEqualTo("userId", uid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    Comment::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .handleStreamErrors(OperationType.LIST, "comments", uid)

    fun observeFollowRequests(uid: String): Flow<List<FollowRequest>> =
        db.collection("users").document(uid).collection("followRequests")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    FollowRequest::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .handleStreamErrors(OperationType.LIST, "users/$uid/followRequests", uid)

    fun observeSentFollowRequestIds(uid: String): Flow<Set<String>> =
        db.collection("users").document(uid).collection("sentFollowRequests")
            .snapshots()
            .map { snapshot -> snapshot.documents.mapTo(HashSet()) { it.id } }
            .handleStreamErrors(OperationType.LIST, "users/$uid/sentFollowRequests", uid)

    suspend fun updateThemePreference(mode: ThemeMode): Result<Unit> {
        val uid = resolveAuth()?.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid"
        return try {
            val currentUid = requireUserId()
            db.collection("users").document(currentUid).update(
                mapOf(
                    "themeMode" to mode.wireValue,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateAccountPrivacy(isPrivate: Boolean): Result<Unit> {
        val uid = resolveAuth()?.currentUser?.uid ?: "unauthenticated"
        var path = "users/$uid"
        return try {
            val currentUid = requireUserId()
            db.collection("users").document(currentUid).update(
                mapOf(
                    "isPrivate" to isPrivate,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            path = "profiles/$currentUid"
            db.collection("profiles").document(currentUid).update(
                mapOf(
                    "isPrivate" to isPrivate,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateNotificationPreferences(
        notifyLikes: Boolean,
        notifyComments: Boolean,
        notifyFollowers: Boolean,
        notifyMessages: Boolean
    ): Result<Unit> {
        val uid = resolveAuth()?.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid"
        return try {
            val currentUid = requireUserId()
            db.collection("users").document(currentUid).update(
                mapOf(
                    "notifyLikes" to notifyLikes,
                    "notifyComments" to notifyComments,
                    "notifyFollowers" to notifyFollowers,
                    "notifyMessages" to notifyMessages,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun saveDailyUsage(usageDate: String, seconds: Long): Result<Unit> {
        val uid = resolveAuth()?.currentUser?.uid ?: return Result.success(Unit)
        val path = "users/$uid"
        return try {
            db.collection("users").document(uid).update(
                mapOf(
                    "usageDate" to usageDate.take(16),
                    "dailyUsageSeconds" to seconds.coerceIn(0L, 864000L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleArchivePost(postId: String, currentlyArchived: Boolean): Result<Unit> {
        if (postId.startsWith("sample_")) {
            return Result.failure(IllegalArgumentException("Sample posts cannot be archived"))
        }
        val path = "posts/$postId"
        return try {
            requireUserId()
            val nextVisibility = if (currentlyArchived) "public" else "archived"
            db.collection("posts").document(postId).update(
                mapOf(
                    "visibility" to nextVisibility,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateProfile(
        username: String,
        fullName: String,
        bio: String,
        website: String,
        avatarData: String?
    ): Result<Unit> {
        val uid = resolveAuth()?.currentUser?.uid ?: "unauthenticated"
        val path = "profiles/$uid"
        return try {
            val currentUid = requireUserId()
            val cleanUsername = username.trim().replace(" ", "_").take(30)
            require(cleanUsername.length >= 2) { "Username must be at least 2 characters" }
            val cleanFullName = fullName.trim().take(60)
            require(cleanFullName.isNotEmpty()) { "Full name cannot be empty" }

            val updates = mutableMapOf<String, Any>(
                "username" to cleanUsername,
                "usernameLower" to cleanUsername.lowercase(),
                "fullName" to cleanFullName,
                "bio" to bio.trim().take(200),
                "website" to website.trim().take(150),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!avatarData.isNullOrBlank() && avatarData.length <= 350000) {
                updates["avatarData"] = avatarData
            }
            db.collection("profiles").document(currentUid).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun createPost(
        author: UserProfile,
        caption: String,
        location: String,
        taggedUsers: List<String>,
        mediaType: String,
        mediaData: String,
        videoUri: String = ""
    ): Result<Unit> {
        var path = "posts"
        return try {
            val uid = requireUserId()
            require(mediaData.isNotBlank()) { "Please select an image or video" }
            val hashtags = Regex("#([A-Za-z0-9_]+)")
                .findAll(caption)
                .map { it.groupValues[1].lowercase() }
                .distinct()
                .take(15)
                .toList()

            val docRef = db.collection("posts").document()
            path = docRef.path
            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "authorUsername" to author.username.ifBlank { "star_user" }.take(30),
                "authorName" to author.fullName.ifBlank { "Star Creator" }.take(60),
                "caption" to caption.trim().take(1000),
                "hashtags" to hashtags,
                "taggedUsers" to taggedUsers.map { it.trim().removePrefix("@") }.filter { it.isNotBlank() }.take(15),
                "mediaType" to mediaType,
                "mediaData" to mediaData.take(700000),
                "visibility" to "public",
                "likesCount" to 0L,
                "commentsCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (author.avatarData.isNotBlank() && author.avatarData.length <= 350000) {
                payload["authorAvatar"] = author.avatarData
            }
            if (location.isNotBlank()) {
                payload["location"] = location.trim().take(100)
            }
            if (videoUri.isNotBlank()) {
                payload["videoUri"] = videoUri.take(500)
            }
            docRef.set(payload).await()

            db.collection("profiles").document(uid).update(
                mapOf(
                    "postsCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun createReel(
        author: UserProfile,
        caption: String,
        audioTitle: String,
        videoUri: String,
        thumbnailData: String
    ): Result<Unit> {
        var path = "reels"
        return try {
            val uid = requireUserId()
            require(videoUri.isNotBlank()) { "Please pick a short video" }
            val docRef = db.collection("reels").document()
            path = docRef.path
            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "authorUsername" to author.username.ifBlank { "star_user" }.take(30),
                "caption" to caption.trim().take(500),
                "audioTitle" to audioTitle.trim().ifBlank { "${author.username} • Original Audio" }.take(100),
                "videoUri" to videoUri.take(500),
                "visibility" to "public",
                "likesCount" to 0L,
                "commentsCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (author.avatarData.isNotBlank() && author.avatarData.length <= 350000) {
                payload["authorAvatar"] = author.avatarData
            }
            if (thumbnailData.isNotBlank() && thumbnailData.length <= 450000) {
                payload["thumbnailData"] = thumbnailData
            }
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun createStory(
        author: UserProfile,
        mediaType: String,
        mediaData: String,
        textContent: String,
        bgGradient: String
    ): Result<Unit> {
        var path = "stories"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("stories").document()
            path = docRef.path
            val expiresAtMillis = System.currentTimeMillis() + 24L * 3_600_000L
            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "authorUsername" to author.username.ifBlank { "star_user" }.take(30),
                "mediaType" to mediaType,
                "visibility" to "public",
                "expiresAtMillis" to expiresAtMillis,
                "createdAt" to FieldValue.serverTimestamp()
            )
            if (author.avatarData.isNotBlank() && author.avatarData.length <= 350000) {
                payload["authorAvatar"] = author.avatarData
            }
            if (mediaData.isNotBlank()) {
                payload["mediaData"] = mediaData.take(700000)
            }
            if (textContent.isNotBlank()) {
                payload["textContent"] = textContent.trim().take(280)
            }
            if (bgGradient.isNotBlank()) {
                payload["bgGradient"] = bgGradient.take(40)
            }
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleLike(
        targetId: String,
        targetType: String,
        targetOwnerId: String,
        currentlyLiked: Boolean,
        actor: UserProfile?
    ): Result<Unit> {
        val uid = resolveAuth()?.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid/likes/$targetId"
        return try {
            val currentUid = requireUserId()
            val likeRef = db.collection("users").document(currentUid).collection("likes").document(targetId)
            if (currentlyLiked) {
                likeRef.delete().await()
            } else {
                likeRef.set(
                    mapOf(
                        "targetId" to targetId,
                        "targetType" to targetType,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            if (!targetId.startsWith("sample_")) {
                val collectionName = if (targetType == "reel") "reels" else "posts"
                val delta = if (currentlyLiked) -1L else 1L
                db.collection(collectionName).document(targetId).update(
                    mapOf(
                        "likesCount" to FieldValue.increment(delta),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            if (!currentlyLiked && targetOwnerId.isNotBlank() && targetOwnerId != currentUid && !targetOwnerId.startsWith("sample_") && actor != null) {
                sendNotification(
                    recipientId = targetOwnerId,
                    notifType = "like",
                    actor = actor,
                    targetId = targetId,
                    previewText = if (targetType == "reel") "liked your Reel." else "liked your post."
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleSave(postId: String, currentlySaved: Boolean): Result<Unit> {
        val uid = resolveAuth()?.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid/saved/$postId"
        return try {
            val currentUid = requireUserId()
            val savedRef = db.collection("users").document(currentUid).collection("saved").document(postId)
            if (currentlySaved) {
                savedRef.delete().await()
            } else {
                savedRef.set(
                    mapOf(
                        "postId" to postId,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleFollow(
        targetProfile: UserProfile,
        currentlyFollowing: Boolean,
        currentlyRequested: Boolean,
        actor: UserProfile?
    ): Result<Unit> {
        val uid = resolveAuth()?.currentUser?.uid ?: "unauthenticated"
        var path = "users/$uid/following/${targetProfile.userId}"
        return try {
            val currentUid = requireUserId()
            if (targetProfile.userId == currentUid) return Result.success(Unit)

            // Cancel pending follow request if already requested
            if (currentlyRequested && !currentlyFollowing) {
                path = "users/${targetProfile.userId}/followRequests/$currentUid"
                if (!targetProfile.userId.startsWith("sample_")) {
                    db.collection("users").document(targetProfile.userId)
                        .collection("followRequests").document(currentUid)
                        .delete().await()
                }
                path = "users/$currentUid/sentFollowRequests/${targetProfile.userId}"
                db.collection("users").document(currentUid)
                    .collection("sentFollowRequests").document(targetProfile.userId)
                    .delete().await()
                return Result.success(Unit)
            }

            // Private real account -> send follow request instead of immediate follow
            if (!currentlyFollowing && targetProfile.isPrivate && !targetProfile.userId.startsWith("sample_")) {
                path = "users/${targetProfile.userId}/followRequests/$currentUid"
                val reqPayload = mutableMapOf<String, Any>(
                    "requesterId" to currentUid,
                    "requesterUsername" to (actor?.username?.ifBlank { "star_user" } ?: "star_user").take(30),
                    "requesterName" to (actor?.fullName?.ifBlank { "Star Creator" } ?: "Star Creator").take(60),
                    "createdAt" to FieldValue.serverTimestamp()
                )
                if (actor != null && actor.avatarData.isNotBlank() && actor.avatarData.length <= 350000) {
                    reqPayload["requesterAvatar"] = actor.avatarData
                }
                db.collection("users").document(targetProfile.userId)
                    .collection("followRequests").document(currentUid)
                    .set(reqPayload).await()

                path = "users/$currentUid/sentFollowRequests/${targetProfile.userId}"
                db.collection("users").document(currentUid)
                    .collection("sentFollowRequests").document(targetProfile.userId)
                    .set(
                        mapOf(
                            "targetUserId" to targetProfile.userId,
                            "targetUsername" to targetProfile.username.ifBlank { "creator" }.take(30),
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    ).await()

                if (actor != null) {
                    sendNotification(
                        recipientId = targetProfile.userId,
                        notifType = "follow_request",
                        actor = actor,
                        targetId = currentUid,
                        previewText = "requested to follow you."
                    )
                }
                return Result.success(Unit)
            }

            val followRef = db.collection("users").document(currentUid)
                .collection("following").document(targetProfile.userId)
            val delta = if (currentlyFollowing) -1L else 1L

            if (currentlyFollowing) {
                followRef.delete().await()
            } else {
                followRef.set(
                    mapOf(
                        "targetUserId" to targetProfile.userId,
                        "targetUsername" to targetProfile.username.take(30),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            db.collection("profiles").document(currentUid).update(
                mapOf(
                    "followingCount" to FieldValue.increment(delta),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            if (!targetProfile.userId.startsWith("sample_")) {
                db.collection("profiles").document(targetProfile.userId).update(
                    mapOf(
                        "followersCount" to FieldValue.increment(delta),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()

                if (!currentlyFollowing && actor != null) {
                    sendNotification(
                        recipientId = targetProfile.userId,
                        notifType = "follow",
                        actor = actor,
                        targetId = currentUid,
                        previewText = "started following you."
                    )
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun approveFollowRequest(request: FollowRequest, actor: UserProfile?): Result<Unit> {
        val uid = resolveAuth()?.currentUser?.uid ?: "unauthenticated"
        var path = "users/$uid/followRequests/${request.requesterId}"
        return try {
            val currentUid = requireUserId()
            db.collection("users").document(currentUid)
                .collection("followRequests").document(request.requesterId)
                .delete().await()

            path = "profiles/$currentUid"
            db.collection("profiles").document(currentUid).update(
                mapOf(
                    "followersCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            if (actor != null && request.requesterId != currentUid) {
                sendNotification(
                    recipientId = request.requesterId,
                    notifType = "follow",
                    actor = actor,
                    targetId = currentUid,
                    previewText = "approved your follow request."
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun declineFollowRequest(requesterId: String): Result<Unit> {
        val uid = resolveAuth()?.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid/followRequests/$requesterId"
        return try {
            val currentUid = requireUserId()
            db.collection("users").document(currentUid)
                .collection("followRequests").document(requesterId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun addComment(
        postId: String,
        postOwnerId: String,
        isReel: Boolean,
        text: String,
        actor: UserProfile
    ): Result<Unit> {
        var path = "comments"
        return try {
            val uid = requireUserId()
            val trimmed = text.trim().take(500)
            require(trimmed.isNotEmpty()) { "Comment cannot be empty" }

            val docRef = db.collection("comments").document()
            path = docRef.path
            val payload = mutableMapOf<String, Any>(
                "postId" to postId,
                "userId" to uid,
                "authorUsername" to actor.username.ifBlank { "star_user" }.take(30),
                "text" to trimmed,
                "visibility" to "public",
                "createdAt" to FieldValue.serverTimestamp()
            )
            if (actor.avatarData.isNotBlank() && actor.avatarData.length <= 350000) {
                payload["authorAvatar"] = actor.avatarData
            }
            docRef.set(payload).await()

            if (!postId.startsWith("sample_")) {
                val col = if (isReel) "reels" else "posts"
                db.collection(col).document(postId).update(
                    mapOf(
                        "commentsCount" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            if (postOwnerId.isNotBlank() && postOwnerId != uid && !postOwnerId.startsWith("sample_")) {
                sendNotification(
                    recipientId = postOwnerId,
                    notifType = "comment",
                    actor = actor,
                    targetId = postId,
                    previewText = "commented: \"${trimmed.take(80)}\""
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    private suspend fun sendNotification(
        recipientId: String,
        notifType: String,
        actor: UserProfile,
        targetId: String,
        previewText: String
    ) {
        try {
            val docRef = db.collection("users").document(recipientId)
                .collection("notifications").document()
            val payload = mutableMapOf<String, Any>(
                "notifType" to notifType,
                "actorId" to actor.userId,
                "actorUsername" to actor.username.ifBlank { "star_user" }.take(30),
                "previewText" to previewText.take(200),
                "read" to false,
                "createdAt" to FieldValue.serverTimestamp()
            )
            if (actor.avatarData.isNotBlank() && actor.avatarData.length <= 350000) {
                payload["actorAvatar"] = actor.avatarData
            }
            if (targetId.isNotBlank()) {
                payload["targetId"] = targetId.take(128)
            }
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "users/$recipientId/notifications")
        }
    }

    suspend fun markNotificationRead(notifId: String): Result<Unit> {
        if (notifId.startsWith("sample_")) return Result.success(Unit)
        val uid = resolveAuth()?.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid/notifications/$notifId"
        return try {
            val currentUid = requireUserId()
            db.collection("users").document(currentUid)
                .collection("notifications").document(notifId)
                .update(mapOf("read" to true))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun conversationIdFor(uidA: String, uidB: String): String {
        val (first, second) = if (uidA <= uidB) uidA to uidB else uidB to uidA
        return "dm_${first}_${second}"
    }

    suspend fun sendMessage(
        myProfile: UserProfile,
        peerProfile: UserProfile,
        text: String
    ): Result<String> {
        val uid = myProfile.userId
        val convId = conversationIdFor(uid, peerProfile.userId)
        val convPath = "conversations/$convId"
        return try {
            val currentUid = requireUserId()
            val trimmed = text.trim().take(1000)
            require(trimmed.isNotEmpty()) { "Message cannot be empty" }

            val convRef = db.collection("conversations").document(convId)
            val snap = convRef.get().await()
            val participants = listOf(currentUid, peerProfile.userId).sorted()

            if (!snap.exists()) {
                val userA = if (participants[0] == currentUid) myProfile else peerProfile
                val userB = if (participants[1] == currentUid) myProfile else peerProfile
                val payload = mutableMapOf<String, Any>(
                    "participantUids" to participants,
                    "userAId" to userA.userId,
                    "userAUsername" to userA.username.ifBlank { "star_user" }.take(30),
                    "userBId" to userB.userId,
                    "userBUsername" to userB.username.ifBlank { "creator" }.take(30),
                    "lastMessage" to trimmed.take(500),
                    "lastSenderId" to currentUid,
                    "unreadBy" to listOf(peerProfile.userId),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (userA.avatarData.isNotBlank() && userA.avatarData.length <= 350000) {
                    payload["userAAvatar"] = userA.avatarData
                }
                if (userB.avatarData.isNotBlank() && userB.avatarData.length <= 350000) {
                    payload["userBAvatar"] = userB.avatarData
                }
                convRef.set(payload).await()
            } else {
                convRef.update(
                    mapOf(
                        "lastMessage" to trimmed.take(500),
                        "lastSenderId" to currentUid,
                        "unreadBy" to listOf(peerProfile.userId),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val msgRef = convRef.collection("messages").document()
            msgRef.set(
                mapOf(
                    "convId" to convId,
                    "senderId" to currentUid,
                    "participantUids" to participants,
                    "text" to trimmed,
                    "seenBy" to listOf(currentUid),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(convId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, convPath)
            Result.failure(e)
        }
    }

    suspend fun markConversationRead(convId: String): Result<Unit> {
        val uid = resolveAuth()?.currentUser?.uid ?: "unauthenticated"
        val path = "conversations/$convId"
        return try {
            val currentUid = requireUserId()
            val convRef = db.collection("conversations").document(convId)
            val snap = convRef.get().await()
            if (snap.exists()) {
                convRef.update(
                    mapOf(
                        "unreadBy" to FieldValue.arrayRemove(currentUid),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                val msgs = convRef.collection("messages")
                    .whereArrayContains("participantUids", currentUid)
                    .get()
                    .await()
                for (doc in msgs.documents) {
                    val seenBy = (doc.get("seenBy") as? List<*>)?.filterIsInstance<String>().orEmpty()
                    if (currentUid !in seenBy) {
                        doc.reference.update("seenBy", FieldValue.arrayUnion(currentUid)).await()
                    }
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }
}
