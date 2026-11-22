package com.clipsaver.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File

/**
 * كل الشغل المتعلق بمكتبة yt-dlp مجمّع هنا.
 * كل الدوال هنا "حاجزة" (blocking) فلازم تُستدعى من خيط خلفي (Dispatchers.IO).
 */
class ClipEngine(private val app: Context) {

    fun init() {
        synchronized(LOCK) {
            if (ready) return
            YoutubeDL.getInstance().init(app)
            FFmpeg.getInstance().init(app)
            ready = true
        }
    }

    /** تحديث yt-dlp (المواقع تغيّر أنظمتها كثيراً فالتحديث مهم). */
    fun updateEngine() {
        YoutubeDL.getInstance().updateYoutubeDL(app, YoutubeDL.UpdateChannel.STABLE)
    }

    fun fetchInfo(url: String): ClipInfo {
        val i = YoutubeDL.getInstance().getInfo(url)
        return ClipInfo(
            title = i.title ?: "بدون عنوان",
            uploader = i.uploader ?: "",
            thumbnail = i.thumbnail,
            durationSec = (i.duration as Number?)?.toLong() ?: 0L
        )
    }

    fun cancel(processId: String) {
        YoutubeDL.getInstance().destroyProcessById(processId)
    }

    fun download(
        url: String,
        mode: Mode,
        quality: VideoQuality,
        audio: AudioFormat,
        processId: String,
        fileName: String? = null,
        onProgress: (Float, Long) -> Unit
    ): SavedFile {
        val dir = File(app.cacheDir, "dl/$processId").apply { mkdirs() }
        try {
            val req = YoutubeDLRequest(url)
            req.addOption("--no-playlist")
            req.addOption("--no-mtime")
            val custom = fileName?.let { Dest.safeName(it, 100) }?.takeIf { it.isNotBlank() }
            val template = if (custom != null) custom.replace("%", "%%") + ".%(ext)s"
            else "%(title).80s.%(ext)s"
            req.addOption("-o", "${dir.absolutePath}/$template")

            if (mode == Mode.VIDEO) {
                val h = quality.height
                val format = if (h == null) {
                    "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/bv*+ba/b"
                } else {
                    "bv*[height<=$h][ext=mp4]+ba[ext=m4a]/b[height<=$h][ext=mp4]/" +
                        "bv*[height<=$h]+ba/b[height<=$h]/b"
                }
                req.addOption("-f", format)
                req.addOption("--merge-output-format", "mp4")
            } else {
                req.addOption("-f", "ba/b")
                req.addOption("-x")
                req.addOption("--audio-format", audio.codec)
                req.addOption("--audio-quality", "0")
            }

            YoutubeDL.getInstance().execute(req, processId) { progress, eta, _ ->
                onProgress(progress, eta)
            }

            val file = dir.listFiles()
                ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
                ?.maxByOrNull { it.length() }
                ?: error("لم يتم العثور على الملف بعد التنزيل")

            return saveToStore(file, mode, url)
        } finally {
            dir.deleteRecursively()
        }
    }

    /**
     * الفيديو  → المعرض (Movies/خزنة)
     * الصوت    → Music/<مجلد المنصة>
     * وغير ذلك → التنزيلات (احتياط).
     * كلها بدون طلب صلاحيات تخزين.
     */
    private fun saveToStore(file: File, mode: Mode, url: String): SavedFile {
        val ext = file.extension.lowercase()
        var mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: "application/octet-stream"

        val collection: Uri
        val relPath: String
        val place: String
        val isAudio: Boolean

        if (mode == Mode.VIDEO && mime.startsWith("video/")) {
            collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            relPath = "${Environment.DIRECTORY_MOVIES}/${Dest.VIDEO_ALBUM}"
            place = "المعرض ‹ ${Dest.VIDEO_ALBUM}"
            isAudio = false
        } else if (mode == Mode.AUDIO) {
            if (!mime.startsWith("audio/")) {
                mime = when (ext) {
                    "m4a" -> "audio/mp4"
                    "opus", "ogg" -> "audio/ogg"
                    "webm" -> "audio/webm"
                    else -> "audio/mpeg"
                }
            }
            val folder = Dest.audioFolderFor(app, url)
            collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            relPath = "${Environment.DIRECTORY_MUSIC}/$folder"
            place = "الموسيقى ‹ $folder"
            isAudio = true
        } else {
            collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            relPath = "${Environment.DIRECTORY_DOWNLOADS}/ClipSaver"
            place = "التنزيلات ‹ ClipSaver"
            isAudio = false
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
            if (isAudio) put(MediaStore.Audio.Media.IS_MUSIC, 1)
        }

        val resolver = app.contentResolver
        val uri = resolver.insert(collection, values)
            ?: error("تعذر إنشاء الملف في مكان الحفظ")

        resolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { input -> input.copyTo(out) }
        } ?: error("تعذر كتابة الملف")

        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)

        return SavedFile(uri = uri, name = file.name, mime = mime, place = place)
    }

    private companion object {
        val LOCK = Any()
        @Volatile var ready = false
    }
}
