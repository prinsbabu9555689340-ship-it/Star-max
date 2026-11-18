package com.example.data.firebase

import android.content.Context
import com.example.data.model.Comment
import com.example.data.model.Conversation
import com.example.data.model.Post
import com.example.data.model.Reel
import com.example.data.model.SampleData
import com.example.data.model.SavedAccountRecord
import com.example.data.model.Story
import com.example.data.model.UserProfile
import com.google.firebase.Timestamp
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

/**
 * Persistent local store (SharedPreferences + JSON) for user-authored posts, stories, reels,
 * comments, likes, saved posts, and follows when using mobile-OTP / local-authenticated sessions.
 */
class LocalUserStore(context: Context) {
    private val prefs = (context.applicationContext ?: context)
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadAllPosts(): List<Post> {
        val raw = prefs.getString(KEY_CUSTOM_POSTS, null) ?: return SampleData.samplePosts
        val custom = runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        Post(
                            id = o.optString("id"),
                            userId = o.optString("userId"),
                            authorUsername = o.optString("authorUsername"),
                            authorName = o.optString("authorName"),
                            authorAvatar = o.optString("authorAvatar"),
                            caption = o.optString("caption"),
                            location = o.optString("location"),
                            hashtags = jsonArrayToList(o.optJSONArray("hashtags")),
                            taggedUsers = jsonArrayToList(o.optJSONArray("taggedUsers")),
                            mediaType = o.optString("mediaType", "photo"),
                            mediaData = o.optString("mediaData"),
                            videoUri = o.optString("videoUri"),
                            visibility = o.optString("visibility", "public"),
                            likesCount = o.optLong("likesCount", 0L),
                            commentsCount = o.optLong("commentsCount", 0L),
                            createdAt = Timestamp(Date(o.optLong("createdAtMillis", System.currentTimeMillis())))
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
        return (custom + SampleData.samplePosts)
            .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
    }

    fun saveCustomPosts(allPosts: List<Post>) {
        val custom = allPosts.filter { !it.id.startsWith("sample_") }
        val arr = JSONArray()
        custom.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("userId", p.userId)
                    put("authorUsername", p.authorUsername)
                    put("authorName", p.authorName)
                    put("authorAvatar", p.authorAvatar.take(350_000))
                    put("caption", p.caption)
                    put("location", p.location)
                    put("hashtags", JSONArray(p.hashtags))
                    put("taggedUsers", JSONArray(p.taggedUsers))
                    put("mediaType", p.mediaType)
                    put("mediaData", p.mediaData.take(700_000))
                    put("videoUri", p.videoUri)
                    put("visibility", p.visibility)
                    put("likesCount", p.likesCount)
                    put("commentsCount", p.commentsCount)
                    put("createdAtMillis", p.createdAt?.toDate()?.time ?: System.currentTimeMillis())
                }
            )
        }
        prefs.edit().putString(KEY_CUSTOM_POSTS, arr.toString()).apply()
    }

    fun syncAuthorMetadataInPosts(userId: String, username: String, fullName: String, avatarData: String) {
        val all = loadAllPosts()
        var changed = false
        val updated = all.map { post ->
            if (!post.id.startsWith("sample_") && post.userId == userId) {
                changed = true
                post.copy(
                    authorUsername = username,
                    authorName = fullName,
                    authorAvatar = avatarData
                )
            } else post
        }
        if (changed) saveCustomPosts(updated)
    }

    fun loadAllStories(): List<Story> {
        val now = System.currentTimeMillis()
        val raw = prefs.getString(KEY_CUSTOM_STORIES, null)
        val custom = if (raw == null) emptyList() else runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val exp = o.optLong("expiresAtMillis", 0L)
                    if (exp <= now) continue
                    add(
                        Story(
                            id = o.optString("id"),
                            userId = o.optString("userId"),
                            authorUsername = o.optString("authorUsername"),
                            authorAvatar = o.optString("authorAvatar"),
                            mediaType = o.optString("mediaType", "photo"),
                            mediaData = o.optString("mediaData"),
                            textContent = o.optString("textContent"),
                            bgGradient = o.optString("bgGradient", "sunset"),
                            visibility = o.optString("visibility", "public"),
                            expiresAtMillis = exp,
                            createdAt = Timestamp(Date(o.optLong("createdAtMillis", now)))
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
        return (custom + SampleData.sampleStories)
            .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
    }

    fun saveCustomStories(allStories: List<Story>) {
        val custom = allStories.filter { !it.id.startsWith("sample_") }
        val arr = JSONArray()
        custom.forEach { s ->
            arr.put(
                JSONObject().apply {
                    put("id", s.id)
                    put("userId", s.userId)
                    put("authorUsername", s.authorUsername)
                    put("authorAvatar", s.authorAvatar.take(350_000))
                    put("mediaType", s.mediaType)
                    put("mediaData", s.mediaData.take(700_000))
                    put("textContent", s.textContent)
                    put("bgGradient", s.bgGradient)
                    put("visibility", s.visibility)
                    put("expiresAtMillis", s.expiresAtMillis)
                    put("createdAtMillis", s.createdAt?.toDate()?.time ?: System.currentTimeMillis())
                }
            )
        }
        prefs.edit().putString(KEY_CUSTOM_STORIES, arr.toString()).apply()
    }

    fun loadAllReels(): List<Reel> {
        val raw = prefs.getString(KEY_CUSTOM_REELS, null) ?: return SampleData.sampleReels
        val custom = runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        Reel(
                            id = o.optString("id"),
                            userId = o.optString("userId"),
                            authorUsername = o.optString("authorUsername"),
                            authorAvatar = o.optString("authorAvatar"),
                            caption = o.optString("caption"),
                            audioTitle = o.optString("audioTitle"),
                            videoUri = o.optString("videoUri"),
                            thumbnailData = o.optString("thumbnailData"),
                            visibility = o.optString("visibility", "public"),
                            likesCount = o.optLong("likesCount", 0L),
                            commentsCount = o.optLong("commentsCount", 0L),
                            createdAt = Timestamp(Date(o.optLong("createdAtMillis", System.currentTimeMillis())))
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
        return (custom + SampleData.sampleReels)
            .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
    }

    fun saveCustomReels(allReels: List<Reel>) {
        val custom = allReels.filter { !it.id.startsWith("sample_") }
        val arr = JSONArray()
        custom.forEach { r ->
            arr.put(
                JSONObject().apply {
                    put("id", r.id)
                    put("userId", r.userId)
                    put("authorUsername", r.authorUsername)
                    put("authorAvatar", r.authorAvatar.take(350_000))
                    put("caption", r.caption)
                    put("audioTitle", r.audioTitle)
                    put("videoUri", r.videoUri)
                    put("thumbnailData", r.thumbnailData.take(450_000))
                    put("visibility", r.visibility)
                    put("likesCount", r.likesCount)
                    put("commentsCount", r.commentsCount)
                    put("createdAtMillis", r.createdAt?.toDate()?.time ?: System.currentTimeMillis())
                }
            )
        }
        prefs.edit().putString(KEY_CUSTOM_REELS, arr.toString()).apply()
    }

    fun loadCommentsMap(): MutableMap<String, List<Comment>> {
        val map = SampleData.sampleCommentsByPost.toMutableMap()
        val raw = prefs.getString(KEY_CUSTOM_COMMENTS, null) ?: return map
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val c = Comment(
                    id = o.optString("id"),
                    postId = o.optString("postId"),
                    userId = o.optString("userId"),
                    authorUsername = o.optString("authorUsername"),
                    authorAvatar = o.optString("authorAvatar"),
                    text = o.optString("text"),
                    visibility = "public",
                    createdAt = Timestamp(Date(o.optLong("createdAtMillis", System.currentTimeMillis())))
                )
                map[c.postId] = map[c.postId].orEmpty() + c
            }
        }
        return map
    }

    fun addComment(comment: Comment) {
        val raw = prefs.getString(KEY_CUSTOM_COMMENTS, null)
        val arr = if (raw != null) runCatching { JSONArray(raw) }.getOrElse { JSONArray() } else JSONArray()
        arr.put(
            JSONObject().apply {
                put("id", comment.id)
                put("postId", comment.postId)
                put("userId", comment.userId)
                put("authorUsername", comment.authorUsername)
                put("authorAvatar", comment.authorAvatar.take(350_000))
                put("text", comment.text)
                put("createdAtMillis", comment.createdAt?.toDate()?.time ?: System.currentTimeMillis())
            }
        )
        prefs.edit().putString(KEY_CUSTOM_COMMENTS, arr.toString()).apply()
    }

    fun loadUserSet(uid: String, key: String, defaults: Set<String> = emptySet()): Set<String> {
        val stored = prefs.getStringSet("set_${uid}_$key", null) ?: return defaults
        return stored.toSet()
    }

    fun saveUserSet(uid: String, key: String, values: Set<String>) {
        prefs.edit().putStringSet("set_${uid}_$key", values.toSet()).apply()
    }

    private fun jsonArrayToList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val s = arr.optString(i)
                if (s.isNotBlank()) add(s)
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "starmax_local_user_content"
        private const val KEY_CUSTOM_POSTS = "custom_posts_json"
        private const val KEY_CUSTOM_STORIES = "custom_stories_json"
        private const val KEY_CUSTOM_REELS = "custom_reels_json"
        private const val KEY_CUSTOM_COMMENTS = "custom_comments_json"

        fun toUserProfile(record: SavedAccountRecord, postsCount: Long = 0L, followingCount: Long = 2L): UserProfile {
            return UserProfile(
                id = record.uid,
                userId = record.uid,
                username = record.username.ifBlank { "star_user" },
                usernameLower = record.username.lowercase().ifBlank { "star_user" },
                fullName = record.fullName.ifBlank { record.username.ifBlank { "Star Creator" } },
                bio = record.bio.ifBlank { "Capturing moments on Star Max ✨" },
                website = record.website,
                avatarData = record.avatarData,
                avatarTint = "#E1306C",
                visibility = "public",
                isPrivate = record.isPrivate,
                postsCount = postsCount,
                followersCount = 128L,
                followingCount = followingCount,
                verified = record.verified,
                online = true
            )
        }
    }
}
