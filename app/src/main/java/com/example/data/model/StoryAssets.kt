package com.example.data.model

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

enum class StoryFilter(
    val id: String,
    val label: String,
    private val matrixValues: FloatArray?
) {
    NORMAL(
        id = "Normal",
        label = "Normal",
        matrixValues = null
    ),
    WARM(
        id = "Warm",
        label = "Warm",
        matrixValues = floatArrayOf(
            1.14f, 0f, 0f, 0f, 12f,
            0f, 1.03f, 0f, 0f, 4f,
            0f, 0f, 0.86f, 0f, -8f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    COOL(
        id = "Cool",
        label = "Cool",
        matrixValues = floatArrayOf(
            0.88f, 0f, 0f, 0f, -8f,
            0f, 1.02f, 0f, 0f, 4f,
            0f, 0f, 1.18f, 0f, 16f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    VINTAGE(
        id = "Vintage",
        label = "Vintage",
        matrixValues = floatArrayOf(
            0.84f, 0.25f, 0.08f, 0f, 12f,
            0.12f, 0.78f, 0.08f, 0f, 8f,
            0.05f, 0.12f, 0.60f, 0f, 4f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    BW(
        id = "B&W",
        label = "B&W",
        matrixValues = floatArrayOf(
            0.33f, 0.59f, 0.11f, 0f, 0f,
            0.33f, 0.59f, 0.11f, 0f, 0f,
            0.33f, 0.59f, 0.11f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    VIVID(
        id = "Vivid",
        label = "Vivid",
        matrixValues = floatArrayOf(
            1.26f, -0.08f, -0.08f, 0f, 0f,
            -0.08f, 1.26f, -0.08f, 0f, 0f,
            -0.08f, -0.08f, 1.26f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    );

    fun toColorFilter(): ColorFilter? = matrixValues?.let {
        ColorFilter.colorMatrix(ColorMatrix(it))
    }

    companion object {
        fun fromId(raw: String?): StoryFilter {
            val cleaned = raw?.trim().orEmpty()
            return entries.firstOrNull {
                it.id.equals(cleaned, ignoreCase = true) || it.name.equals(cleaned, ignoreCase = true)
            } ?: NORMAL
        }
    }
}

data class StoryAudioTrack(
    val id: String,
    val title: String,
    val artist: String,
    val durationLabel: String,
    val streamUrl: String
)

object StoryMusicCatalog {
    val trendingTracks: List<StoryAudioTrack> = listOf(
        StoryAudioTrack(
            id = "track_neon_skyline",
            title = "Neon Skyline",
            artist = "Luna Wave",
            durationLabel = "0:30",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
        ),
        StoryAudioTrack(
            id = "track_golden_hour",
            title = "Golden Hour Lo-Fi",
            artist = "Kai Beats",
            durationLabel = "0:28",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3"
        ),
        StoryAudioTrack(
            id = "track_midnight_drive",
            title = "Midnight Drive",
            artist = "Synth Aura",
            durationLabel = "0:30",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3"
        ),
        StoryAudioTrack(
            id = "track_velvet_sunset",
            title = "Velvet Sunset",
            artist = "Maya Keys",
            durationLabel = "0:25",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3"
        ),
        StoryAudioTrack(
            id = "track_starlight_pulse",
            title = "Starlight Pulse",
            artist = "Nova Echo",
            durationLabel = "0:30",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3"
        ),
        StoryAudioTrack(
            id = "track_tokyo_chill",
            title = "Tokyo Rain Chill",
            artist = "Sora Vibes",
            durationLabel = "0:30",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-6.mp3"
        )
    )
}
