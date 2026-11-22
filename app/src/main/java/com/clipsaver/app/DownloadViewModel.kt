package com.clipsaver.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DownloadViewModel(app: Application) : AndroidViewModel(app) {

    private val engine = ClipEngine(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val engineReady = CompletableDeferred<Unit>()
    private var job: Job? = null
    private var currentProcess: String? = null
    private var cancelRequested = false

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                engine.init()
                engineReady.complete(Unit)
                _state.update { it.copy(engineReady = true) }
                // تحديث صامت للمحرك (إن توفر إنترنت)
                runCatching { engine.updateEngine() }
            } catch (e: Throwable) {
                engineReady.completeExceptionally(e)
                _state.update {
                    it.copy(engineError = "تعذر تشغيل محرك التنزيل: ${e.message.orEmpty().take(120)}")
                }
            }
        }
    }

    // ---------- مدخلات الواجهة ----------

    fun onUrlChange(text: String) {
        _state.update { it.copy(url = text) }
    }

    /** يُستدعى عند المشاركة من تيك توك / يوتيوب إلى التطبيق. */
    fun onSharedText(text: String) {
        val link = extractUrl(text) ?: text.trim()
        _state.update { it.copy(url = link) }
        analyze()
    }

    fun setMode(mode: Mode) = _state.update { it.copy(mode = mode) }
    fun setQuality(q: VideoQuality) = _state.update { it.copy(quality = q) }
    fun setAudioFormat(f: AudioFormat) = _state.update { it.copy(audioFormat = f) }

    fun backToOptions() = _state.update { it.copy(stage = Stage.READY, saved = null, error = null) }

    fun reset() {
        job?.cancel()
        _state.update { UiState(engineReady = it.engineReady, engineError = it.engineError) }
    }

    // ---------- تحليل الرابط ----------

    fun analyze() {
        val link = extractUrl(_state.value.url)
        if (link == null) {
            _state.update { it.copy(error = "الرجاء إدخال رابط صحيح يبدأ بـ http أو https") }
            return
        }
        job?.cancel()
        _state.update {
            it.copy(stage = Stage.LOADING, link = link, info = null, error = null, saved = null)
        }
        job = viewModelScope.launch {
            try {
                engineReady.await()
                val info = withContext(Dispatchers.IO) { engine.fetchInfo(link) }
                _state.update { it.copy(stage = Stage.READY, info = info) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update { it.copy(stage = Stage.IDLE, error = friendly(e)) }
            }
        }
    }

    // ---------- التنزيل ----------

    fun startDownload() {
        val st = _state.value
        if (st.link.isBlank() || st.info == null) return

        cancelRequested = false
        val pid = "dl_${System.currentTimeMillis()}"
        currentProcess = pid
        _state.update { it.copy(stage = Stage.DOWNLOADING, progress = 0f, etaSec = 0L, error = null) }

        job = viewModelScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) {
                    engine.download(st.link, st.mode, st.quality, st.audioFormat, pid) { p, eta ->
                        _state.update { it.copy(progress = (p / 100f).coerceIn(0f, 1f), etaSec = eta) }
                    }
                }
                _state.update { it.copy(stage = Stage.DONE, saved = saved, progress = 1f) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update {
                    if (cancelRequested) it.copy(stage = Stage.READY)
                    else it.copy(stage = Stage.READY, error = friendly(e))
                }
            }
        }
    }

    fun cancel() {
        cancelRequested = true
        currentProcess?.let { pid ->
            viewModelScope.launch(Dispatchers.IO) { runCatching { engine.cancel(pid) } }
        }
    }

    // ---------- أدوات ----------

    private fun extractUrl(text: String): String? =
        Regex("https?://\\S+").find(text)?.value?.trimEnd('.', ',', ')', '"', '\'')

    private fun friendly(e: Throwable): String {
        val m = e.message.orEmpty()
        return when {
            "Unable to resolve host" in m || "Network is unreachable" in m || "timed out" in m ->
                "تأكد من اتصال الإنترنت وحاول مرة ثانية."
            "Private" in m || "Sign in" in m || "login" in m ->
                "هذا المقطع خاص أو يحتاج تسجيل دخول."
            "Unsupported URL" in m ->
                "هذا الرابط غير مدعوم."
            else -> {
                val last = m.lineSequence().lastOrNull { it.isNotBlank() }.orEmpty().take(160)
                "تعذر تنفيذ العملية. جرّب مرة ثانية لاحقاً.\n$last"
            }
        }
    }
}
