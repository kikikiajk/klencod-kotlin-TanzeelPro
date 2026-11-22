package com.clipsaver.app

import android.net.Uri

enum class Mode { VIDEO, AUDIO }

enum class VideoQuality(val label: String, val height: Int?) {
    BEST("أفضل جودة", null),
    Q1080("1080p", 1080),
    Q720("720p", 720),
    Q480("480p", 480),
    Q360("360p", 360)
}

enum class AudioFormat(val label: String, val codec: String) {
    MP3("MP3", "mp3"),
    M4A("M4A", "m4a")
}

enum class Stage { IDLE, LOADING, READY, DOWNLOADING, DONE }

data class ClipInfo(
    val title: String,
    val uploader: String,
    val thumbnail: String?,
    val durationSec: Long
)

data class SavedFile(
    val uri: Uri,
    val name: String,
    val mime: String,
    val place: String = ""
)

data class UiState(
    val engineReady: Boolean = false,
    val engineError: String? = null,
    val url: String = "",
    val link: String = "",
    val stage: Stage = Stage.IDLE,
    val info: ClipInfo? = null,
    val mode: Mode = Mode.VIDEO,
    val quality: VideoQuality = VideoQuality.Q720,
    val audioFormat: AudioFormat = AudioFormat.MP3,
    val progress: Float = 0f,
    val etaSec: Long = 0L,
    val error: String? = null,
    val saved: SavedFile? = null
)
