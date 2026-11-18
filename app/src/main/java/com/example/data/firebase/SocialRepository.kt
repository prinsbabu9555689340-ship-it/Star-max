package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.model.AuthLookupRecord
import com.example.data.model.ChatMessage
import com.example.data.model.Comment
import com.example.data.model.Conversation
import com.example.data.model.FollowRequest
import com.example.data.model.NotificationItem
import com.example.data.model.Post
import com.example.data.model.Reel
import com.example.data.model.Story
import com.example.data.model.StoryFilter
import com.example.data.model.ThemeMode
import com.example.data.model.UserProfile
import com.example.data.model.UserSettings
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
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

    fun sanitizeUsername(raw: String): String {
        val cleaned = raw.trim()
            .removePrefix("@")
            .lowercase()
            .replace(" ", "_")
            .replace(Regex("[^a-z0-9._-]"), "")
            .replace(Regex("\\.{2,}"), ".")
            .trim('.')
            .take(30)
        return cleaned
    }

    suspend fun checkUsernameAvailable(username: String): Result<Boolean> {
        val cleanLower = sanitizeUsername(username)
        if (cleanLower.length < 3) {
            return Result.failure(IllegalArgumentException("Username must be at least 3 characters (letters, numbers, . or _)."))
        }
        val path = "usernames/$cleanLower"
        return try {
            val snap = db.collection("usernames").document(cleanLower).get().await()
            Result.success(!snap.exists())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun checkPhoneAvailable(phoneDigits10: String): Result<Boolean> {
        val digits = PhoneFormatUtils.normalizeIndianPhoneDigits(phoneDigits10)
            ?: return Result.failure(IllegalArgumentException("Enter a valid 10-digit Indian mobile number."))
        val path = "phoneRegistry/$digits"
        return try {
            val snap = db.collection("phoneRegistry").document(digits).get().await()
            Result.success(!snap.exists())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun lookupAccountForLogin(identifier: String): Result<AuthLookupRecord?> {
        val trimmed = identifier.trim()
        if (trimmed.isBlank()) return Result.success(null)
        val digits10 = PhoneFormatUtils.normalizeIndianPhoneDigits(trimmed)
        var opPath = "usernames/$trimmed"
        return try {
            val usernameLower = if (digits10 != null) {
                opPath = "phoneRegistry/$digits10"
                val phoneSnap = db.collection("phoneRegistry").document(digits10).get().await()
                if (!phoneSnap.exists()) return Result.success(null)
                phoneSnap.getString("usernameLower").orEmpty()
            } else {
                sanitizeUsername(trimmed)
            }
            if (usernameLower.length < 2) return Result.success(null)
            opPath = "usernames/$usernameLower"
            val userSnap = db.collection("usernames").document(usernameLower).get().await()
            if (!userSnap.exists()) return Result.success(null)
            val record = AuthLookupRecord(
                userId = userSnap.getString("userId").orEmpty(),
                usernameLower = userSnap.getString("usernameLower").orEmpty(),
                phoneE164 = userSnap.getString("phoneE164").orEmpty(),
                passwordSalt = userSnap.getString("passwordSalt").orEmpty(),
                passwordHash = userSnap.getString("passwordHash").orEmpty(),
                verified = userSnap.getBoolean("verified") ?: false
            )
            Result.success(record.takeIf { it.userId.isNotBlank() && it.phoneE164.isNotBlank() })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, opPath)
            Result.failure(e)
        }
    }

    suspend fun completePhoneRegistration(
        user: FirebaseUser,
        fullName: String,
        username: String,
        phoneDigits10: String,
        password: String
    ): Result<UserProfile> {
        val uid = user.uid
        var opType = OperationType.GET
        var opPath = "phoneRegistry/$phoneDigits10"
        return try {
            val cleanUsername = sanitizeUsername(username)
            require(cleanUsername.length >= 3) { "Username must be at least 3 valid characters." }
            val cleanFullName = fullName.trim().take(60)
            require(cleanFullName.isNotEmpty()) { "Full name is required." }
            val digits10 = PhoneFormatUtils.normalizeIndianPhoneDigits(phoneDigits10)
                ?: throw IllegalArgumentException("Valid 10-digit +91 mobile number required.")
            require(password.length >= 6) { "Password must be at least 6 characters." }

            val phoneE164 = user.phoneNumber?.takeIf { it.isNotBlank() }
                ?: PhoneFormatUtils.toIndianE164(digits10)

            val phoneDocRef = db.collection("phoneRegistry").document(digits10)
            val existingPhone = phoneDocRef.get().await()
            if (existingPhone.exists() && existingPhone.getString("userId") != uid) {
                return Result.failure(
                    IllegalStateException("This mobile number is already registered. Please log in instead.")
                )
            }

            opPath = "usernames/$cleanUsername"
            val unameDocRef = db.collection("usernames").document(cleanUsername)
            val existingUname = unameDocRef.get().await()
            if (existingUname.exists() && existingUname.getString("userId") != uid) {
                return Result.failure(
                    IllegalStateException("That username is already taken. Please pick another username.")
                )
            }

            val userDocRef = db.collection("users").document(uid)
            opPath = userDocRef.path
            val userSnap = userDocRef.get().await()
            if (!userSnap.exists()) {
                opType = OperationType.CREATE
                userDocRef.set(
                    mapOf(
                        "uid" to uid,
                        "userId" to uid,
                        "fullName" to cleanFullName,
                        "username" to cleanUsername,
                        "usernameLower" to cleanUsername,
                        "mobile" to phoneE164,
                        "phoneE164" to phoneE164,
                        "isVerified" to false,
                        "verified" to false,
                        "profilePic" to "",
                        "avatarData" to "",
                        "avatarTint" to "#E1306C",
                        "bio" to "Capturing moments on Star Max ✨",
                        "website" to "",
                        "visibility" to "public",
                        "isPrivate" to false,
                        "postsCount" to 0L,
                        "followersCount" to 0L,
                        "followingCount" to 0L,
                        "online" to true,
                        "themeMode" to ThemeMode.SYSTEM.wireValue,
                        "notifyLikes" to true,
                        "notifyComments" to true,
                        "notifyFollowers" to true,
                        "notifyMessages" to true,
                        "dailyUsageSeconds" to 0L,
                        "usageDate" to ThemePreferences.todayDateKey(),
                        "verificationStatus" to "none",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                opType = OperationType.UPDATE
                userDocRef.update(
                    mapOf(
                        "uid" to uid,
                        "fullName" to cleanFullName,
                        "username" to cleanUsername,
                        "usernameLower" to cleanUsername,
                        "mobile" to phoneE164,
                        "phoneE164" to phoneE164,
                        "online" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val profileDocRef = db.collection("profiles").document(uid)
            opType = OperationType.GET
            opPath = profileDocRef.path
            val profileSnap = profileDocRef.get().await()
            if (!profileSnap.exists()) {
                opType = OperationType.CREATE
                profileDocRef.set(
                    mapOf(
                        "userId" to uid,
                        "username" to cleanUsername,
                        "usernameLower" to cleanUsername,
                        "fullName" to cleanFullName,
                        "mobile" to phoneE164,
                        "bio" to "Capturing moments on Star Max ✨",
                        "website" to "",
                        "avatarData" to "",
                        "profilePic" to "",
                        "avatarTint" to "#E1306C",
                        "visibility" to "public",
                        "isPrivate" to false,
                        "postsCount" to 0L,
                        "followersCount" to 0L,
                        "followingCount" to 0L,
                        "isVerified" to false,
                        "verified" to false,
                        "online" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                opType = OperationType.UPDATE
                profileDocRef.update(
                    mapOf(
                        "username" to cleanUsername,
                        "usernameLower" to cleanUsername,
                        "fullName" to cleanFullName,
                        "mobile" to phoneE164,
                        "online" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val salt = PasswordHasher.generateSalt()
            val hash = PasswordHasher.hashPassword(password, salt)

            opPath = unameDocRef.path
            if (!existingUname.exists()) {
                opType = OperationType.CREATE
                unameDocRef.set(
                    mapOf(
                        "userId" to uid,
                        "usernameLower" to cleanUsername,
                        "phoneE164" to phoneE164,
                        "passwordSalt" to salt,
                        "passwordHash" to hash,
                        "verified" to false,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                opType = OperationType.UPDATE
                unameDocRef.update(
                    mapOf(
                        "phoneE164" to phoneE164,
                        "passwordSalt" to salt,
                        "passwordHash" to hash,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            opPath = phoneDocRef.path
            if (!existingPhone.exists()) {
                opType = OperationType.CREATE
                phoneDocRef.set(
                    mapOf(
                        "userId" to uid,
                        "phoneDigits" to digits10,
                        "phoneE164" to phoneE164,
                        "usernameLower" to cleanUsername,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                opType = OperationType.UPDATE
                phoneDocRef.update(mapOf("usernameLower" to cleanUsername)).await()
            }

            runCatching {
                user.updateProfile(
                    UserProfileChangeRequest.Builder().setDisplayName(cleanFullName).build()
                ).await()
            }

            Result.success(
                UserProfile(
                    id = uid,
                    uid = uid,
                    userId = uid,
                    username = cleanUsername,
                    usernameLower = cleanUsername,
                    fullName = cleanFullName,
                    mobile = phoneE164,
                    bio = "Capturing moments on Star Max ✨",
                    visibility = "public",
                    isVerified = false,
                    verified = false,
                    online = true
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "completePhoneRegistration failed at $opPath", e)
            handleFirestoreError(e, opType, opPath)
            Result.failure(e)
        }
    }

    suspend fun updateRegisteredPassword(
        usernameLower: String,
        newPassword: String
    ): Result<Unit> {
        val clean = sanitizeUsername(usernameLower)
        val path = "usernames/$clean"
        return try {
            val uid = requireUserId()
            require(newPassword.length >= 6) { "Password must be at least 6 characters." }
            val ref = db.collection("usernames").document(clean)
            val snap = ref.get().await()
            if (!snap.exists() || snap.getString("userId") != uid) {
                return Result.failure(IllegalStateException("Username password record not found for this account."))
            }
            val salt = PasswordHasher.generateSalt()
            val hash = PasswordHasher.hashPassword(newPassword, salt)
            ref.update(
                mapOf(
                    "passwordSalt" to salt,
                    "passwordHash" to hash,
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

    suspend fun ensureUserProfile(user: FirebaseUser): Result<Unit> {
        val uid = user.uid
        var opType = OperationType.GET
        var opPath = "users/$uid"
        return try {
            val rawName = user.displayName?.takeIf { it.isNotBlank() } ?: "Star Creator"
            val baseHandle = sanitizeUsername(rawName)
                .ifBlank { "star_${uid.take(6).lowercase()}" }
                .let { if (it.length < 3) "${it}_star" else it }
            val avatarUrl = user.photoUrl?.toString()?.takeIf { it.length <= 350000 }.orEmpty()
            val phone = user.phoneNumber.orEmpty()

            val userDocRef = db.collection("users").document(uid)
            val userSnap = userDocRef.get().await()
            if (!userSnap.exists()) {
                opType = OperationType.CREATE
                opPath = userDocRef.path
                val payload = mutableMapOf<String, Any>(
                    "uid" to uid,
                    "userId" to uid,
                    "fullName" to rawName.take(60),
                    "username" to baseHandle,
                    "usernameLower" to baseHandle.lowercase(),
                    "mobile" to phone,
                    "isVerified" to false,
                    "verified" to false,
                    "profilePic" to avatarUrl,
                    "avatarData" to avatarUrl,
                    "avatarTint" to "#E1306C",
                    "bio" to "Capturing moments on Star Max ✨",
                    "website" to "",
                    "visibility" to "public",
                    "isPrivate" to false,
                    "postsCount" to 0L,
                    "followersCount" to 0L,
                    "followingCount" to 0L,
                    "online" to true,
                    "themeMode" to ThemeMode.SYSTEM.wireValue,
                    "notifyLikes" to true,
                    "notifyComments" to true,
                    "notifyFollowers" to true,
                    "notifyMessages" to true,
                    "dailyUsageSeconds" to 0L,
                    "usageDate" to ThemePreferences.todayDateKey(),
                    "verificationStatus" to "none",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (phone.isNotBlank()) {
                    payload["phoneE164"] = phone
                }
                userDocRef.set(payload).await()
            } else {
                opType = OperationType.UPDATE
                opPath = userDocRef.path
                val userUpdates = mutableMapOf<String, Any>(
                    "online" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (userSnap.getString("uid").isNullOrBlank()) userUpdates["uid"] = uid
                if (userSnap.getString("fullName").isNullOrBlank()) userUpdates["fullName"] = rawName.take(60)
                if (userSnap.getString("username").isNullOrBlank()) userUpdates["username"] = baseHandle
                if (userSnap.getString("usernameLower").isNullOrBlank()) userUpdates["usernameLower"] = baseHandle.lowercase()
                if (userSnap.get("profilePic") == null) userUpdates["profilePic"] = avatarUrl
                if (userSnap.get("mobile") == null) userUpdates["mobile"] = phone
                if (phone.isNotBlank() && userSnap.getString("phoneE164").isNullOrBlank()) {
                    userUpdates["phoneE164"] = phone
                }
                userDocRef.update(userUpdates).await()
            }

            val profileDocRef = db.collection("profiles").document(uid)
            opType = OperationType.GET
            opPath = profileDocRef.path
            val profileSnap = profileDocRef.get().await()
            if (!profileSnap.exists()) {
                val effectiveUsername = userSnap.getString("username")?.takeIf { it.isNotBlank() } ?: baseHandle
                val effectiveFullName = userSnap.getString("fullName")?.takeIf { it.isNotBlank() } ?: rawName.take(60)
                val profilePayload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "username" to effectiveUsername,
                    "usernameLower" to effectiveUsername.lowercase(),
                    "fullName" to effectiveFullName,
                    "mobile" to phone,
                    "bio" to "Capturing moments on Star Max ✨",
                    "website" to "",
                    "avatarData" to avatarUrl,
                    "profilePic" to avatarUrl,
                    "avatarTint" to "#E1306C",
                    "visibility" to "public",
                    "isPrivate" to false,
                    "postsCount" to 0L,
                    "followersCount" to 0L,
                    "followingCount" to 0L,
                    "isVerified" to false,
                    "verified" to false,
                    "online" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                opType = OperationType.CREATE
                opPath = profileDocRef.path
                profileDocRef.set(profilePayload).await()
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

    private fun normalizeProfile(raw: UserProfile): UserProfile {
        val uid = raw.effectiveUid
        val uname = raw.username.ifBlank { raw.usernameLower }
        val unameLower = raw.usernameLower.ifBlank { uname.lowercase() }
        val avatar = raw.effectiveAvatar
        return raw.copy(
            uid = uid,
            userId = uid,
            username = uname,
            usernameLower = unameLower,
            avatarData = avatar,
            profilePic = avatar
        )
    }

    fun observePublicProfiles(uid: String): Flow<List<UserProfile>> =
        db.collection("users")
            .whereEqualTo("visibility", "public")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).map(::normalizeProfile)
                    .filter { it.effectiveUid.isNotBlank() && it.username.isNotBlank() }
            }
            .handleStreamErrors(OperationType.LIST, "users", uid)

    fun searchUsersRealtime(query: String, currentUid: String): Flow<List<UserProfile>> {
        val clean = query.trim().removePrefix("@").lowercase()
        return db.collection("users")
            .whereEqualTo("visibility", "public")
            .snapshots()
            .map { snapshot ->
                val allUsers = snapshot.toObjects(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).map(::normalizeProfile)
                    .filter { it.effectiveUid.isNotBlank() && it.effectiveUid != currentUid }

                if (clean.isBlank()) {
                    emptyList()
                } else {
                    allUsers.filter { user ->
                        user.usernameLower.contains(clean) ||
                            user.username.lowercase().contains(clean) ||
                            user.fullName.lowercase().contains(clean)
                    }.sortedBy { user ->
                        when {
                            user.usernameLower == clean -> 0
                            user.usernameLower.startsWith(clean) -> 1
                            user.fullName.lowercase().startsWith(clean) -> 2
                            else -> 3
                        }
                    }
                }
            }
            .handleStreamErrors(OperationType.LIST, "users", currentUid)
    }

    fun observeHasPendingVerificationRequest(uid: String): Flow<Boolean> =
        db.collection("verification_requests")
            .whereEqualTo("userId", uid)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.any { it.getString("status") == "pending" }
            }
            .handleStreamErrors(OperationType.LIST, "verification_requests", uid)

    fun observePosts(uid: String): Flow<List<Post>> =
        db.collection("posts")
            .whereEqualTo("visibility", "public")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    Post::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .handleStreamErrors(OperationType.LIST, "posts", uid)

    fun observeStories(uid: String): Flow<List<Story>> =
        db.collection("stories")
            .whereEqualTo("visibility", "public")
            .snapshots()
            .map { snapshot ->
                val now = System.currentTimeMillis()
                snapshot.toObjects(
                    Story::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).filter { it.expiresAtMillis > now }
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .handleStreamErrors(OperationType.LIST, "stories", uid)

    fun observeReels(uid: String): Flow<List<Reel>> =
        db.collection("reels")
            .whereEqualTo("visibility", "public")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    Reel::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .handleStreamErrors(OperationType.LIST, "reels", uid)

    fun observeComments(postId: String, uid: String): Flow<List<Comment>> =
        db.collection("comments")
            .whereEqualTo("visibility", "public")
            .whereEqualTo("postId", postId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    Comment::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedBy { it.createdAt ?: Timestamp.now() }
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
                snapshot.toObjects(
                    NotificationItem::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
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
            runCatching {
                db.collection("profiles").document(currentUid).update(
                    mapOf(
                        "isPrivate" to isPrivate,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
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

    suspend fun submitVerificationRequest(
        actorUsername: String,
        fullName: String,
        knownAs: String,
        category: String,
        link: String,
        idProofData: String
    ): Result<String> {
        val uid = resolveAuth()?.currentUser?.uid ?: "unauthenticated"
        var path = "verification_requests"
        var opType = OperationType.CREATE
        return try {
            val currentUid = requireUserId()
            val cleanFullName = fullName.trim().take(80)
            require(cleanFullName.isNotEmpty()) { "Full name is required." }
            val cleanCategory = category.trim().ifBlank { "Creator" }.take(40)
            val cleanKnownAs = knownAs.trim().take(80)
            val cleanLink = link.trim().take(200)
            val cleanProof = idProofData.trim().take(350000)
            require(cleanProof.isNotBlank() || cleanLink.isNotBlank()) {
                "Please select an ID proof photo or enter your mobile / social link."
            }
            val cleanHandle = sanitizeUsername(actorUsername).ifBlank { "star_user" }

            val requestDoc = db.collection("verification_requests").document()
            path = requestDoc.path
            opType = OperationType.CREATE
            val requestPayload = mutableMapOf<String, Any>(
                "userId" to currentUid,
                "username" to cleanHandle,
                "fullName" to cleanFullName,
                "category" to cleanCategory,
                "knownAs" to cleanKnownAs,
                "idOrLink" to cleanLink,
                "status" to "pending",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (cleanProof.isNotBlank()) {
                requestPayload["idProofData"] = cleanProof
            }
            requestDoc.set(requestPayload).await()

            path = "users/$currentUid"
            opType = OperationType.UPDATE
            val userUpdates = mutableMapOf<String, Any>(
                "verificationFullName" to cleanFullName,
                "verificationCategory" to cleanCategory,
                "verificationKnownAs" to cleanKnownAs,
                "verificationLink" to cleanLink,
                "verificationIdProofData" to cleanProof,
                "verificationSubmittedAtMillis" to System.currentTimeMillis(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val existingUserSnap = db.collection("users").document(currentUid).get().await()
            if (existingUserSnap.getBoolean("isVerified") != true) {
                userUpdates["verificationStatus"] = "pending"
            }
            db.collection("users").document(currentUid).update(userUpdates).await()
            Result.success("pending")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, opType, path)
            Result.failure(e)
        }
    }

    suspend fun toggleArchivePost(postId: String, currentlyArchived: Boolean): Result<Unit> {
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
        var path = "users/$uid"
        return try {
            val currentUid = requireUserId()
            val cleanUsername = sanitizeUsername(username)
            require(cleanUsername.length >= 2) { "Username must be at least 2 valid characters" }
            val cleanFullName = fullName.trim().take(60)
            require(cleanFullName.isNotEmpty()) { "Full name cannot be empty" }

            val userSnap = db.collection("users").document(currentUid).get().await()
            val oldUsernameLower = userSnap.getString("usernameLower")
                ?: db.collection("profiles").document(currentUid).get().await().getString("usernameLower").orEmpty()
            if (oldUsernameLower != cleanUsername) {
                path = "usernames/$cleanUsername"
                val targetUnameSnap = db.collection("usernames").document(cleanUsername).get().await()
                if (targetUnameSnap.exists() && targetUnameSnap.getString("userId") != currentUid) {
                    return Result.failure(IllegalStateException("Username @$cleanUsername is already taken."))
                }
                if (oldUsernameLower.isNotBlank()) {
                    val oldUnameRef = db.collection("usernames").document(oldUsernameLower)
                    val oldUnameSnap = oldUnameRef.get().await()
                    if (oldUnameSnap.exists() && oldUnameSnap.getString("userId") == currentUid) {
                        val phoneE164 = oldUnameSnap.getString("phoneE164").orEmpty()
                        val salt = oldUnameSnap.getString("passwordSalt").orEmpty()
                        val hash = oldUnameSnap.getString("passwordHash").orEmpty()
                        if (phoneE164.isNotBlank() && salt.isNotBlank() && hash.isNotBlank()) {
                            db.collection("usernames").document(cleanUsername).set(
                                mapOf(
                                    "userId" to currentUid,
                                    "usernameLower" to cleanUsername,
                                    "phoneE164" to phoneE164,
                                    "passwordSalt" to salt,
                                    "passwordHash" to hash,
                                    "verified" to false,
                                    "createdAt" to FieldValue.serverTimestamp(),
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            ).await()
                            oldUnameRef.delete().await()
                            val digits10 = PhoneFormatUtils.normalizeIndianPhoneDigits(phoneE164)
                            if (digits10 != null) {
                                db.collection("phoneRegistry").document(digits10)
                                    .update(mapOf("usernameLower" to cleanUsername))
                                    .await()
                            }
                        }
                    }
                }
            }

            path = "users/$currentUid"
            val updates = mutableMapOf<String, Any>(
                "username" to cleanUsername,
                "usernameLower" to cleanUsername,
                "fullName" to cleanFullName,
                "bio" to bio.trim().take(200),
                "website" to website.trim().take(150),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!avatarData.isNullOrBlank() && avatarData.length <= 350000) {
                updates["avatarData"] = avatarData
                updates["profilePic"] = avatarData
            }
            db.collection("users").document(currentUid).update(updates).await()
            runCatching {
                db.collection("profiles").document(currentUid).update(updates).await()
            }
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

            db.collection("users").document(uid).update(
                mapOf(
                    "postsCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            runCatching {
                db.collection("profiles").document(uid).update(
                    mapOf(
                        "postsCount" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

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
        bgGradient: String,
        filterName: String = StoryFilter.NORMAL.id,
        songTitle: String = "",
        songArtist: String = "",
        songUrl: String = ""
    ): Result<Unit> {
        var path = "stories"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("stories").document()
            path = docRef.path
            val expiresAtMillis = System.currentTimeMillis() + 24L * 3_600_000L
            val canonicalFilter = StoryFilter.fromId(filterName).id
            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "authorUsername" to author.username.ifBlank { "star_user" }.take(30),
                "mediaType" to mediaType,
                "filterName" to canonicalFilter,
                "visibility" to "public",
                "expiresAtMillis" to expiresAtMillis,
                "createdAt" to FieldValue.serverTimestamp()
            )
            val avatar = author.effectiveAvatar
            if (avatar.isNotBlank() && avatar.length <= 350000) {
                payload["authorAvatar"] = avatar
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
            if (songTitle.isNotBlank()) {
                payload["songTitle"] = songTitle.trim().take(100)
            }
            if (songArtist.isNotBlank()) {
                payload["songArtist"] = songArtist.trim().take(80)
            }
            if (songUrl.isNotBlank()) {
                payload["songUrl"] = songUrl.trim().take(500)
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

            db.collection("users").document(currentUid).update(
                mapOf(
                    "followingCount" to FieldValue.increment(delta),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            runCatching {
                db.collection("profiles").document(currentUid).update(
                    mapOf(
                        "followingCount" to FieldValue.increment(delta),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            if (!targetProfile.userId.startsWith("sample_")) {
                db.collection("users").document(targetProfile.userId).update(
                    mapOf(
                        "followersCount" to FieldValue.increment(delta),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                runCatching {
                    db.collection("profiles").document(targetProfile.userId).update(
                        mapOf(
                            "followersCount" to FieldValue.increment(delta),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }

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

            path = "users/$currentUid"
            db.collection("users").document(currentUid).update(
                mapOf(
                    "followersCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            runCatching {
                db.collection("profiles").document(currentUid).update(
                    mapOf(
                        "followersCount" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

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
