package com.clipsaver.app

import android.content.Context

enum class Platform { TIKTOK, YOUTUBE, OTHER }

/** أماكن الحفظ وأسماء المجلدات. */
object Dest {
    /** اسم ألبوم الفيديوهات في المعرض. */
    const val VIDEO_ALBUM = "خزنة"
    const val TIKTOK_AUDIO = "صوتيات التيكتوك"
    const val YOUTUBE_AUDIO_DEFAULT = "صوتيات اليوتيوب"
    const val OTHER_AUDIO = "صوتيات خزنة"

    private const val PREFS = "khazna_prefs"
    private const val KEY_YT_FOLDER = "yt_audio_folder"

    fun platformOf(url: String): Platform {
        val u = url.lowercase()
        return when {
            "tiktok.com" in u -> Platform.TIKTOK
            "youtube.com" in u || "youtu.be" in u -> Platform.YOUTUBE
            else -> Platform.OTHER
        }
    }

    /** المجلد اللي اختاره المستخدم لصوتيات يوتيوب (null إذا ما اختار بعد). */
    fun youtubeAudioFolder(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_YT_FOLDER, null)
            ?.takeIf { it.isNotBlank() }

    fun setYoutubeAudioFolder(ctx: Context, name: String) {
        val clean = safeName(name, 60)
        if (clean.isBlank()) return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_YT_FOLDER, clean).apply()
    }

    /** مجلد الصوتيات (داخل Music) حسب المنصة. */
    fun audioFolderFor(ctx: Context, url: String): String = when (platformOf(url)) {
        Platform.TIKTOK -> TIKTOK_AUDIO
        Platform.YOUTUBE -> youtubeAudioFolder(ctx) ?: YOUTUBE_AUDIO_DEFAULT
        Platform.OTHER -> OTHER_AUDIO
    }

    /** ينظّف الاسم من الرموز الممنوعة في أسماء الملفات والمجلدات. */
    fun safeName(raw: String, max: Int): String =
        raw.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(max)
            .trim()
            .trim('.')
}
