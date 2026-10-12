package com.personal.ytsave

import android.net.Uri

data class VideoMeta(
    val url: String,
    val title: String,
    val uploader: String,
    val thumbnail: String?,
    val durationSec: Int
)

enum class Quality(val label: String, val isAudio: Boolean, val height: Int? = null) {
    V1080("1080p", false, 1080),
    V720("720p", false, 720),
    V480("480p", false, 480),
    BEST("Best", false),
    MP3("MP3", true),
    M4A("M4A", true)
}

enum class Status(val isActive: Boolean) {
    Queued(true),
    Running(true),
    Saving(true),
    Done(false),
    Failed(false),
    Cancelled(false)
}

data class DownloadItem(
    val id: String,
    val title: String,
    val thumbnail: String?,
    val quality: String,
    val status: Status = Status.Queued,
    val progress: Float = 0f,
    val etaSec: Long = 0,
    val error: String? = null,
    val savedUri: Uri? = null,
    val mime: String? = null
)
