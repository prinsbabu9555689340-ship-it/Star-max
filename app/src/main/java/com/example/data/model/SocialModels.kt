package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

enum class ThemeMode(val wireValue: String, val label: String) {
    SYSTEM("system", "System"),
    DARK("dark", "Dark"),
    LIGHT("light", "Light");

    companion object {
        fun fromWire(value: String?): ThemeMode =
            entries.firstOrNull { it.wireValue == value } ?: SYSTEM
    }
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class UserSettings(
    @DocumentId val id: String = "",
    val userId: String = "",
    val themeMode: String = "system",
    @field:PropertyName("isPrivate")
    @get:PropertyName("isPrivate")
    val isPrivate: Boolean = false,
    @field:PropertyName("isVerified")
    @get:PropertyName("isVerified")
    val isVerified: Boolean = false,
    val notifyLikes: Boolean = true,
    val notifyComments: Boolean = true,
    val notifyFollowers: Boolean = true,
    val notifyMessages: Boolean = true,
    val dailyUsageSeconds: Long = 0L,
    val usageDate: String = "",
    val phoneE164: String = "",
    val verificationStatus: String = "none",
    val verificationFullName: String = "",
    val verificationCategory: String = "",
    val verificationKnownAs: String = "",
    val verificationLink: String = "",
    val verificationIdProofData: String = "",
    val verificationSubmittedAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class VerificationRequest(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val fullName: String = "",
    val category: String = "",
    val knownAs: String = "",
    val idOrLink: String = "",
    val idProofData: String = "",
    val status: String = "pending",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
data class SavedAccountRecord(
    val uid: String = "",
    val slotName: String = "",
    val username: String = "",
    val fullName: String = "",
    val bio: String = "",
    val website: String = "",
    val avatarData: String = "",
    val phoneE164: String = "",
    val verified: Boolean = false,
    val isPrivate: Boolean = false,
    val verificationStatus: String = "none",
    val verificationFullName: String = "",
    val verificationCategory: String = "",
    val verificationKnownAs: String = "",
    val verificationLink: String = "",
    val verificationIdProofData: String = "",
    val authProvider: String = "phone"
)

@Keep
@IgnoreExtraProperties
data class AuthLookupRecord(
    val userId: String = "",
    val usernameLower: String = "",
    val phoneE164: String = "",
    val passwordSalt: String = "",
    val passwordHash: String = "",
    val verified: Boolean = false
)

@Keep
@IgnoreExtraProperties
data class FollowRequest(
    @DocumentId val id: String = "",
    val requesterId: String = "",
    val requesterUsername: String = "",
    val requesterName: String = "",
    val requesterAvatar: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val uid: String = "",
    val userId: String = "",
    val username: String = "",
    val usernameLower: String = "",
    val fullName: String = "",
    val mobile: String = "",
    val bio: String = "",
    val website: String = "",
    val profilePic: String = "",
    val avatarData: String = "",
    val avatarTint: String = "#E1306C",
    val visibility: String = "public",
    @field:PropertyName("isPrivate")
    @get:PropertyName("isPrivate")
    val isPrivate: Boolean = false,
    val postsCount: Long = 0L,
    val followersCount: Long = 0L,
    val followingCount: Long = 0L,
    @field:PropertyName("isVerified")
    @get:PropertyName("isVerified")
    val isVerified: Boolean = false,
    val verified: Boolean = false,
    val online: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val effectiveUid: String get() = userId.ifBlank { uid }.ifBlank { id }
    val effectiveAvatar: String get() = avatarData.ifBlank { profilePic }
    val effectiveVerified: Boolean get() = isVerified
}

@Keep
@IgnoreExtraProperties
data class Post(
    @DocumentId val id: String = "",
    val userId: String = "",
    val authorUsername: String = "",
    val authorName: String = "",
    val authorAvatar: String = "",
    val caption: String = "",
    val location: String = "",
    val hashtags: List<String> = emptyList(),
    val taggedUsers: List<String> = emptyList(),
    val mediaType: String = "photo",
    val mediaData: String = "",
    val videoUri: String = "",
    val visibility: String = "public",
    val likesCount: Long = 0L,
    val commentsCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Comment(
    @DocumentId val id: String = "",
    val postId: String = "",
    val userId: String = "",
    val authorUsername: String = "",
    val authorAvatar: String = "",
    val text: String = "",
    val visibility: String = "public",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Story(
    @DocumentId val id: String = "",
    val userId: String = "",
    val authorUsername: String = "",
    val authorAvatar: String = "",
    val mediaType: String = "photo",
    val mediaData: String = "",
    val textContent: String = "",
    val bgGradient: String = "sunset",
    val filterName: String = "Normal",
    val songTitle: String = "",
    val songArtist: String = "",
    val songUrl: String = "",
    val visibility: String = "public",
    val expiresAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Reel(
    @DocumentId val id: String = "",
    val userId: String = "",
    val authorUsername: String = "",
    val authorAvatar: String = "",
    val caption: String = "",
    val audioTitle: String = "",
    val videoUri: String = "",
    val thumbnailData: String = "",
    val visibility: String = "public",
    val likesCount: Long = 0L,
    val commentsCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class NotificationItem(
    @DocumentId val id: String = "",
    val notifType: String = "like",
    val actorId: String = "",
    val actorUsername: String = "",
    val actorAvatar: String = "",
    val targetId: String = "",
    val previewText: String = "",
    val read: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val participantUids: List<String> = emptyList(),
    val userAId: String = "",
    val userAUsername: String = "",
    val userAAvatar: String = "",
    val userBId: String = "",
    val userBUsername: String = "",
    val userBAvatar: String = "",
    val lastMessage: String = "",
    val lastSenderId: String = "",
    val unreadBy: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val convId: String = "",
    val senderId: String = "",
    val participantUids: List<String> = emptyList(),
    val text: String = "",
    val seenBy: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)
