package com.clipsaver.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShareState(
    val link: String = "",
    val platform: Platform = Platform.OTHER,
    val infoLoading: Boolean = true,
    val infoFailed: Boolean = false,
    val fileName: String = "",
    val nameEdited: Boolean = false,
    val mode: Mode = Mode.VIDEO,
    val quality: VideoQuality = VideoQuality.Q720,
    val audioFormat: AudioFormat = AudioFormat.MP3,
    /** هل مجلد صوتيات يوتيوب محفوظ من قبل؟ */
    val folderKnown: Boolean = true,
    val folderName: String = ""
) {
    /** أول تنزيل صوت من يوتيوب: نسأل عن اسم المجلد. */
    val askFolder: Boolean
        get() = platform == Platform.YOUTUBE && mode == Mode.AUDIO && !folderKnown

    val canConfirm: Boolean
        get() = !(askFolder && Dest.safeName(folderName, 60).isBlank())
}

class ShareViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx = app
    private val engine = ClipEngine(app)
    private val _state = MutableStateFlow(ShareState())
    val state: StateFlow<ShareState> = _state.asStateFlow()
    private var started = false

    /** يرجع false إذا ما لقينا رابط بالنص المشارك. */
    fun init(sharedText: String?): Boolean {
        if (started) return true
        val link = sharedText
            ?.let { Regex("https?://\\S+").find(it)?.value }
            ?.trimEnd('.', ',', ')', '"', '\'')
            ?: return false
        started = true

        val saved = Dest.youtubeAudioFolder(ctx)
        _state.update {
            ShareState(
                link = link,
                platform = Dest.platformOf(link),
                folderKnown = saved != null,
                folderName = saved.orEmpty()
            )
        }

        // نجلب العنوان بالخلفية حتى تظهر النافذة فوراً
        viewModelScope.launch(Dispatchers.IO) {
            try {
                engine.init()
                val info = engine.fetchInfo(link)
                _state.update {
                    it.copy(
                        infoLoading = false,
                        fileName = if (it.nameEdited) it.fileName else Dest.safeName(info.title, 80)
                    )
                }
            } catch (e: Throwable) {
                _state.update { it.copy(infoLoading = false, infoFailed = true) }
            }
        }
        return true
    }

    fun setName(v: String) = _state.update { it.copy(fileName = v, nameEdited = true) }
    fun setMode(m: Mode) = _state.update { it.copy(mode = m) }
    fun setQuality(q: VideoQuality) = _state.update { it.copy(quality = q) }
    fun setAudioFormat(f: AudioFormat) = _state.update { it.copy(audioFormat = f) }
    fun setFolder(v: String) = _state.update { it.copy(folderName = v) }

    /** يثبّت اسم المجلد (إن لزم) ويرجع طلب التنزيل. */
    fun buildRequest(): DlRequest? {
        val s = _state.value
        if (!s.canConfirm) return null
        if (s.askFolder) Dest.setYoutubeAudioFolder(ctx, s.folderName)
        return DlRequest(
            link = s.link,
            mode = s.mode,
            quality = s.quality,
            audio = s.audioFormat,
            fileName = s.fileName.takeIf { it.isNotBlank() }
        )
    }
}
