package com.personal.ytsave

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Wraps yt-dlp (via youtubedl-android) and owns the download queue.
 * Lives at process level so downloads survive screen rotation / leaving the app.
 */
object Downloader {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Semaphore(2) // at most 2 simultaneous downloads
    private val jobs = ConcurrentHashMap<String, Job>()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _initError = MutableStateFlow<String?>(null)
    val initError: StateFlow<String?> = _initError.asStateFlow()

    private val _items = MutableStateFlow<List<DownloadItem>>(emptyList())
    val items: StateFlow<List<DownloadItem>> = _items.asStateFlow()

    fun init(app: Application) {
        scope.launch {
            try {
                YoutubeDL.getInstance().init(app)
                FFmpeg.getInstance().init(app)
                _ready.value = true
                // YouTube changes often; refresh yt-dlp in the background.
                try {
                    YoutubeDL.getInstance().updateYoutubeDL(app, YoutubeDL.UpdateChannel.STABLE)
                } catch (_: Throwable) {
                }
            } catch (t: Throwable) {
                _initError.value = t.message ?: "Failed to start download engine"
            }
        }
    }

    private suspend fun awaitReady() {
        while (!_ready.value) {
            _initError.value?.let { throw IllegalStateException(it) }
            delay(150)
        }
    }

    suspend fun fetchInfo(url: String): VideoMeta = withContext(Dispatchers.IO) {
        awaitReady()
        val req = YoutubeDLRequest(url).apply { addOption("--no-playlist") }
        val info = YoutubeDL.getInstance().getInfo(req)
        VideoMeta(
            url = url,
            title = info.title ?: "Untitled",
            uploader = info.uploader ?: "",
            thumbnail = info.thumbnail,
            durationSec = info.duration
        )
    }

    fun enqueue(meta: VideoMeta, quality: Quality) {
        val id = UUID.randomUUID().toString()
        _items.update {
            listOf(DownloadItem(id, meta.title, meta.thumbnail, quality.label)) + it
        }
        DownloadService.start(App.instance)
        jobs[id] = scope.launch { gate.withPermit { run(id, meta, quality) } }
    }

    private fun run(id: String, meta: VideoMeta, quality: Quality) {
        val dir = File(App.instance.cacheDir, "dl/$id").apply { mkdirs() }
        try {
            if (statusOf(id) == Status.Cancelled) return
            update(id) { copy(status = Status.Running) }

            val req = YoutubeDLRequest(meta.url).apply {
                addOption("--no-playlist")
                addOption("--no-mtime")
                addOption("-o", "${dir.absolutePath}/%(title).80s.%(ext)s")
                if (quality.isAudio) {
                    addOption("-f", "bestaudio/best")
                    addOption("-x")
                    addOption("--audio-format", if (quality == Quality.MP3) "mp3" else "m4a")
                    addOption("--audio-quality", "0")
                } else {
                    addOption("-f", "bv*+ba/b")
                    quality.height?.let {
                        // Prefer h264/aac so files play everywhere on the phone.
                        addOption("-S", "res:$it,vcodec:h264,acodec:m4a")
                    }
                    addOption("--merge-output-format", "mp4")
                }
            }

            YoutubeDL.getInstance().execute(req, id) { progress, eta, _ ->
                update(id) {
                    copy(progress = (progress / 100f).coerceIn(0f, 1f), etaSec = eta)
                }
            }

            val result = dir.listFiles()
                ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
                ?.maxByOrNull { it.length() }
                ?: throw IllegalStateException("Download finished but no file was produced")

            update(id) { copy(status = Status.Saving, progress = 1f) }
            val mime = mimeFor(result.extension)
            val uri = saveToDownloads(result, mime)
            update(id) { copy(status = Status.Done, savedUri = uri, mime = mime) }
        } catch (t: Throwable) {
            if (statusOf(id) != Status.Cancelled) {
                update(id) {
                    copy(status = Status.Failed, error = t.message?.lines()?.lastOrNull { it.isNotBlank() } ?: "Download failed")
                }
            }
        } finally {
            dir.deleteRecursively()
            jobs.remove(id)
        }
    }

    fun cancel(id: String) {
        update(id) { copy(status = Status.Cancelled) }
        try {
            YoutubeDL.getInstance().destroyProcessById(id)
        } catch (_: Throwable) {
        }
        jobs[id]?.cancel()
    }

    fun remove(id: String) {
        _items.update { list -> list.filterNot { it.id == id } }
    }

    fun clearFinished() {
        _items.update { list -> list.filter { it.status.isActive } }
    }

    private fun statusOf(id: String) = _items.value.firstOrNull { it.id == id }?.status

    private fun update(id: String, block: DownloadItem.() -> DownloadItem) {
        _items.update { list -> list.map { if (it.id == id) it.block() else it } }
    }

    private fun saveToDownloads(file: File, mime: String): Uri {
        val resolver = App.instance.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/YTSave")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Could not create file in Downloads")
        resolver.openOutputStream(uri)!!.use { out ->
            file.inputStream().use { it.copyTo(out) }
        }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    private fun mimeFor(ext: String): String = when (ext.lowercase()) {
        "mp4" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "opus", "ogg" -> "audio/ogg"
        else -> "application/octet-stream"
    }
}
