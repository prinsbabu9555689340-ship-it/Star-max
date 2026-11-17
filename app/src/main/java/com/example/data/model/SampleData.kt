package com.example.data.model

import com.example.R
import com.google.firebase.Timestamp
import java.util.Date

object SampleData {

    val drawableRegistry: Map<String, Int> = mapOf(
        "res:img_post_neon_tokyo" to R.drawable.img_post_neon_tokyo,
        "res:img_post_amalfi_sunset" to R.drawable.img_post_amalfi_sunset,
        "res:img_post_artisan_latte" to R.drawable.img_post_artisan_latte,
        "res:img_post_alpine_aurora" to R.drawable.img_post_alpine_aurora,
        "res:ic_starmax_mark" to R.drawable.ic_starmax_mark
    )

    private fun hoursAgo(hours: Long): Timestamp {
        return Timestamp(Date(System.currentTimeMillis() - hours * 3_600_000L))
    }

    private fun minutesAgo(minutes: Long): Timestamp {
        return Timestamp(Date(System.currentTimeMillis() - minutes * 60_000L))
    }

    val sampleProfiles: List<UserProfile> = listOf(
        UserProfile(
            id = "sample_kai",
            userId = "sample_kai",
            username = "kai_tokyo",
            usernameLower = "kai_tokyo",
            fullName = "Kai Takahashi",
            bio = "35mm night streets & neon rain in Tokyo. Capturing after-hours city light.",
            website = "https://kaitokyo.studio",
            avatarData = "res:img_post_neon_tokyo",
            avatarTint = "#00BCD4",
            visibility = "public",
            postsCount = 42L,
            followersCount = 18420L,
            followingCount = 312L,
            verified = true,
            online = true,
            createdAt = hoursAgo(240),
            updatedAt = minutesAgo(5)
        ),
        UserProfile(
            id = "sample_maya",
            userId = "sample_maya",
            username = "maya.lens",
            usernameLower = "maya.lens",
            fullName = "Maya Valenti",
            bio = "Mediterranean sun, coastal architecture & slow afternoons.",
            website = "https://mayavalenti.co",
            avatarData = "res:img_post_amalfi_sunset",
            avatarTint = "#FF6B6B",
            visibility = "public",
            postsCount = 87L,
            followersCount = 34200L,
            followingCount = 540L,
            verified = true,
            online = true,
            createdAt = hoursAgo(320),
            updatedAt = minutesAgo(12)
        ),
        UserProfile(
            id = "sample_elena",
            userId = "sample_elena",
            username = "elena.studio",
            usernameLower = "elena.studio",
            fullName = "Elena Lindqvist",
            bio = "Nordic design, specialty espresso & morning light journals.",
            website = "https://elenastudio.se",
            avatarData = "res:img_post_artisan_latte",
            avatarTint = "#D4A373",
            visibility = "public",
            postsCount = 64L,
            followersCount = 12890L,
            followingCount = 418L,
            verified = false,
            online = false,
            createdAt = hoursAgo(400),
            updatedAt = hoursAgo(2)
        ),
        UserProfile(
            id = "sample_marcus",
            userId = "sample_marcus",
            username = "marcus_alps",
            usernameLower = "marcus_alps",
            fullName = "Marcus Berg",
            bio = "High-altitude astrophotographer & alpine trail filmmaker.",
            website = "https://marcusberg.io",
            avatarData = "res:img_post_alpine_aurora",
            avatarTint = "#7C3AED",
            visibility = "public",
            postsCount = 53L,
            followersCount = 29150L,
            followingCount = 205L,
            verified = true,
            online = true,
            createdAt = hoursAgo(500),
            updatedAt = minutesAgo(28)
        )
    )

    val samplePosts: List<Post> = listOf(
        Post(
            id = "sample_post_1",
            userId = "sample_kai",
            authorUsername = "kai_tokyo",
            authorName = "Kai Takahashi",
            authorAvatar = "res:img_post_neon_tokyo",
            caption = "Midnight rain turns Shinjuku backstreets into liquid neon. Nothing beats walking out after a downpour with a 35mm prime. #tokyo #streetphotography #cyberpunk #nightlife",
            location = "Omoide Yokocho, Tokyo",
            hashtags = listOf("tokyo", "streetphotography", "cyberpunk", "nightlife"),
            taggedUsers = listOf("maya.lens"),
            mediaType = "photo",
            mediaData = "res:img_post_neon_tokyo",
            visibility = "public",
            likesCount = 1284L,
            commentsCount = 3L,
            createdAt = hoursAgo(2),
            updatedAt = hoursAgo(2)
        ),
        Post(
            id = "sample_post_2",
            userId = "sample_maya",
            authorUsername = "maya.lens",
            authorName = "Maya Valenti",
            authorAvatar = "res:img_post_amalfi_sunset",
            caption = "Golden hour hit the cliffside terrace right as the boats drifted into the cove. Pure Mediterranean magic. #amalfi #goldenhour #travel #summer",
            location = "Positano, Italy",
            hashtags = listOf("amalfi", "goldenhour", "travel", "summer"),
            taggedUsers = listOf("elena.studio"),
            mediaType = "photo",
            mediaData = "res:img_post_amalfi_sunset",
            visibility = "public",
            likesCount = 2491L,
            commentsCount = 2L,
            createdAt = hoursAgo(5),
            updatedAt = hoursAgo(5)
        ),
        Post(
            id = "sample_post_3",
            userId = "sample_elena",
            authorUsername = "elena.studio",
            authorName = "Elena Lindqvist",
            authorAvatar = "res:img_post_artisan_latte",
            caption = "Slow Sunday ritual: oat cortado, warm cardamom croissant, and crisp morning sun on oak. #minimalism #coffee #scandinavian #morning",
            location = "Södermalm, Stockholm",
            hashtags = listOf("minimalism", "coffee", "scandinavian", "morning"),
            taggedUsers = emptyList(),
            mediaType = "photo",
            mediaData = "res:img_post_artisan_latte",
            visibility = "public",
            likesCount = 918L,
            commentsCount = 2L,
            createdAt = hoursAgo(9),
            updatedAt = hoursAgo(9)
        ),
        Post(
            id = "sample_post_4",
            userId = "sample_marcus",
            authorUsername = "marcus_alps",
            authorName = "Marcus Berg",
            authorAvatar = "res:img_post_alpine_aurora",
            caption = "Waited four hours at -12°C by the glacial lake and the sky finally exploded in emerald and violet ribbons. #aurora #alps #astrophotography #nature",
            location = "Zermatt, Switzerland",
            hashtags = listOf("aurora", "alps", "astrophotography", "nature"),
            taggedUsers = listOf("kai_tokyo"),
            mediaType = "photo",
            mediaData = "res:img_post_alpine_aurora",
            visibility = "public",
            likesCount = 3740L,
            commentsCount = 2L,
            createdAt = hoursAgo(14),
            updatedAt = hoursAgo(14)
        )
    )

    val sampleStories: List<Story>
        get() {
            val future = System.currentTimeMillis() + 20 * 3_600_000L
            return listOf(
                Story(
                    id = "sample_story_1",
                    userId = "sample_kai",
                    authorUsername = "kai_tokyo",
                    authorAvatar = "res:img_post_neon_tokyo",
                    mediaType = "photo",
                    mediaData = "res:img_post_neon_tokyo",
                    textContent = "Rainy Shibuya walk tonight 🌧️✨",
                    visibility = "public",
                    expiresAtMillis = future,
                    createdAt = hoursAgo(1)
                ),
                Story(
                    id = "sample_story_2",
                    userId = "sample_maya",
                    authorUsername = "maya.lens",
                    authorAvatar = "res:img_post_amalfi_sunset",
                    mediaType = "photo",
                    mediaData = "res:img_post_amalfi_sunset",
                    textContent = "Sunset espresso overlooking the bay 🌅",
                    visibility = "public",
                    expiresAtMillis = future,
                    createdAt = hoursAgo(3)
                ),
                Story(
                    id = "sample_story_3",
                    userId = "sample_elena",
                    authorUsername = "elena.studio",
                    authorAvatar = "res:img_post_artisan_latte",
                    mediaType = "text",
                    textContent = "New studio presets & autumn flat-lay series dropping tomorrow morning ☕✨",
                    bgGradient = "sunset",
                    visibility = "public",
                    expiresAtMillis = future,
                    createdAt = hoursAgo(4)
                ),
                Story(
                    id = "sample_story_4",
                    userId = "sample_marcus",
                    authorUsername = "marcus_alps",
                    authorAvatar = "res:img_post_alpine_aurora",
                    mediaType = "photo",
                    mediaData = "res:img_post_alpine_aurora",
                    textContent = "KP-7 solar storm right now above Zermatt!",
                    visibility = "public",
                    expiresAtMillis = future,
                    createdAt = hoursAgo(6)
                )
            )
        }

    val sampleReels: List<Reel> = listOf(
        Reel(
            id = "sample_reel_1",
            userId = "sample_kai",
            authorUsername = "kai_tokyo",
            authorAvatar = "res:img_post_neon_tokyo",
            caption = "Midnight rain reflections in Tokyo ✨ Wait for the neon crosswalk glow! #tokyo #reels #nightwalk",
            audioTitle = "kai_tokyo • Midnight Neon Lo-Fi",
            videoUri = "raw:reel_neon_tokyo",
            thumbnailData = "res:img_post_neon_tokyo",
            visibility = "public",
            likesCount = 14200L,
            commentsCount = 342L,
            createdAt = hoursAgo(3),
            updatedAt = hoursAgo(3)
        ),
        Reel(
            id = "sample_reel_2",
            userId = "sample_maya",
            authorUsername = "maya.lens",
            authorAvatar = "res:img_post_amalfi_sunset",
            caption = "Golden hour cliffside breeze on the coast 🌊🍊 #travel #amalfi #sunset",
            audioTitle = "maya.lens • Coastline Acoustic",
            videoUri = "raw:reel_amalfi_sunset",
            thumbnailData = "res:img_post_amalfi_sunset",
            visibility = "public",
            likesCount = 22900L,
            commentsCount = 518L,
            createdAt = hoursAgo(7),
            updatedAt = hoursAgo(7)
        ),
        Reel(
            id = "sample_reel_3",
            userId = "sample_marcus",
            authorUsername = "marcus_alps",
            authorAvatar = "res:img_post_alpine_aurora",
            caption = "Chasing the aurora over frozen alpine peaks 🌌❄️ #aurora #mountains #filmmaking",
            audioTitle = "marcus_alps • Glacial Echoes",
            videoUri = "raw:reel_alpine_aurora",
            thumbnailData = "res:img_post_alpine_aurora",
            visibility = "public",
            likesCount = 38100L,
            commentsCount = 890L,
            createdAt = hoursAgo(12),
            updatedAt = hoursAgo(12)
        )
    )

    val sampleCommentsByPost: Map<String, List<Comment>> = mapOf(
        "sample_post_1" to listOf(
            Comment("sc_1", "sample_post_1", "sample_maya", "maya.lens", "res:img_post_amalfi_sunset", "The pink reflections on the cobblestones are unreal!", "public", minutesAgo(45)),
            Comment("sc_2", "sample_post_1", "sample_marcus", "marcus_alps", "res:img_post_alpine_aurora", "What lens did you shoot this on? Razor sharp.", "public", minutesAgo(30)),
            Comment("sc_3", "sample_post_1", "sample_elena", "elena.studio", "res:img_post_artisan_latte", "Pure cinema frame 🔥", "public", minutesAgo(15))
        ),
        "sample_post_2" to listOf(
            Comment("sc_4", "sample_post_2", "sample_kai", "kai_tokyo", "res:img_post_neon_tokyo", "Need to visit Positano next summer!", "public", hoursAgo(3)),
            Comment("sc_5", "sample_post_2", "sample_elena", "elena.studio", "res:img_post_artisan_latte", "Those bougainvillea colors against the turquoise sea 😍", "public", hoursAgo(2))
        ),
        "sample_post_3" to listOf(
            Comment("sc_6", "sample_post_3", "sample_maya", "maya.lens", "res:img_post_amalfi_sunset", "That ceramic mug is gorgeous!", "public", hoursAgo(6)),
            Comment("sc_7", "sample_post_3", "sample_kai", "kai_tokyo", "res:img_post_neon_tokyo", "Best morning vibe on the feed.", "public", hoursAgo(4))
        ),
        "sample_post_4" to listOf(
            Comment("sc_8", "sample_post_4", "sample_kai", "kai_tokyo", "res:img_post_neon_tokyo", "Worth every minute in the cold!", "public", hoursAgo(10)),
            Comment("sc_9", "sample_post_4", "sample_maya", "maya.lens", "res:img_post_amalfi_sunset", "Mirror reflection in the lake is breathtaking.", "public", hoursAgo(8))
        )
    )

    val sampleNotifications: List<NotificationItem> = listOf(
        NotificationItem(
            id = "sample_notif_1",
            notifType = "follow",
            actorId = "sample_maya",
            actorUsername = "maya.lens",
            actorAvatar = "res:img_post_amalfi_sunset",
            targetId = "",
            previewText = "started following you on Star Max.",
            read = false,
            createdAt = minutesAgo(18)
        ),
        NotificationItem(
            id = "sample_notif_2",
            notifType = "like",
            actorId = "sample_kai",
            actorUsername = "kai_tokyo",
            actorAvatar = "res:img_post_neon_tokyo",
            targetId = "sample_post_1",
            previewText = "liked your photo highlight ✨",
            read = false,
            createdAt = hoursAgo(1)
        ),
        NotificationItem(
            id = "sample_notif_3",
            notifType = "comment",
            actorId = "sample_marcus",
            actorUsername = "marcus_alps",
            actorAvatar = "res:img_post_alpine_aurora",
            targetId = "sample_post_4",
            previewText = "commented: \"Welcome to Star Max!\"",
            read = true,
            createdAt = hoursAgo(4)
        )
    )
}
