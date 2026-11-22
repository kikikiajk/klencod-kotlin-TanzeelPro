package com.clipsaver.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** طلب تنزيل جاي من نافذة المشاركة. */
data class DlRequest(
    val link: String,
    val mode: Mode,
    val quality: VideoQuality,
    val audio: AudioFormat,
    val fileName: String?
)

/**
 * خدمة تنزيل بالخلفية: تظهر إشعار "جار التحميل" وبعدها "تم التحميل".
 * تستخدم نفس ClipEngine (نفس منطق التنزيل) بدون أي تغيير بالمكتبات.
 */
class DownloadService : Service() {

    private class JobInfo(val name: String, val pid: String) {
        @Volatile var progress: Int = 0
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val engine by lazy { ClipEngine(applicationContext) }
    private val jobs = ConcurrentHashMap<Int, JobInfo>()
    private val cancelled = ConcurrentHashMap.newKeySet<Int>()
    private val nm by lazy { getSystemService(NotificationManager::class.java) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannels()

        if (intent?.action == ACTION_CANCEL) {
            val id = intent.getIntExtra(EX_JOB, -1)
            jobs[id]?.let { info ->
                cancelled.add(id)
                scope.launch { runCatching { engine.cancel(info.pid) } }
            }
            return START_NOT_STICKY
        }

        val link = intent?.getStringExtra(EX_LINK)
        if (intent == null || link == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val mode = runCatching { Mode.valueOf(intent.getStringExtra(EX_MODE).orEmpty()) }
            .getOrDefault(Mode.VIDEO)
        val quality = runCatching { VideoQuality.valueOf(intent.getStringExtra(EX_QUALITY).orEmpty()) }
            .getOrDefault(VideoQuality.Q720)
        val audio = runCatching { AudioFormat.valueOf(intent.getStringExtra(EX_AUDIO).orEmpty()) }
            .getOrDefault(AudioFormat.MP3)
        val fileName = intent.getStringExtra(EX_NAME)?.takeIf { it.isNotBlank() }

        val id = counter.incrementAndGet()
        val pid = "svc_${System.currentTimeMillis()}_$id"
        jobs[id] = JobInfo(fileName ?: "جاري التجهيز...", pid)

        // لازم نستدعيها بسرعة بعد startForegroundService
        ServiceCompat.startForeground(
            this, FG_ID, buildProgress(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

        scope.launch { runJob(id, link, mode, quality, audio, fileName) }
        return START_NOT_STICKY
    }

    private fun runJob(
        id: Int, link: String, mode: Mode, quality: VideoQuality,
        audio: AudioFormat, fileName: String?
    ) {
        val info = jobs[id] ?: return
        try {
            engine.init()
            var last = -1
            val saved = engine.download(link, mode, quality, audio, info.pid, fileName) { p, _ ->
                val pct = p.toInt().coerceIn(0, 100)
                if (pct != last) {
                    last = pct
                    info.progress = pct
                    nm.notify(FG_ID, buildProgress())
                }
            }
            notifyDone(id, saved)
        } catch (e: Throwable) {
            if (!cancelled.contains(id)) notifyFailed(id, e)
        } finally {
            jobs.remove(id)
            cancelled.remove(id)
            if (jobs.isEmpty()) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            } else {
                nm.notify(FG_ID, buildProgress())
            }
        }
    }

    // ───────────── الإشعارات ─────────────

    private fun ensureChannels() {
        nm.createNotificationChannel(
            NotificationChannel(CH_PROGRESS, "التحميل الجاري", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_DONE, "اكتمال التحميل", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private fun buildProgress(): Notification {
        val entry = jobs.entries.maxByOrNull { it.key }
        val count = jobs.size
        val title = if (count > 1) "جار تحميل $count ملفات" else "جار التحميل"
        val info = entry?.value
        val pct = info?.progress ?: 0

        val b = NotificationCompat.Builder(this, CH_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(if (info != null) "${info.name}  •  $pct%" else "")
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, pct, pct == 0)

        if (entry != null) {
            val cancel = Intent(this, DownloadService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EX_JOB, entry.key)
            }
            val pi = PendingIntent.getService(
                this, entry.key, cancel,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            b.addAction(0, "إلغاء", pi)
        }
        return b.build()
    }

    private fun notifyDone(id: Int, saved: SavedFile) {
        val open = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(saved.uri, saved.mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pi = PendingIntent.getActivity(
            this, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(this, CH_DONE)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("تم التحميل")
            .setContentText(saved.name)
            .setSubText(saved.place)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(DONE_BASE + id, n)
    }

    private fun notifyFailed(id: Int, e: Throwable) {
        val m = e.message.orEmpty()
        val reason = when {
            "Unable to resolve host" in m || "Network is unreachable" in m || "timed out" in m ->
                "تأكد من اتصال الإنترنت وحاول مرة ثانية."
            "Private" in m || "Sign in" in m || "login" in m -> "هذا المقطع خاص أو يحتاج تسجيل دخول."
            "Unsupported URL" in m -> "هذا الرابط غير مدعوم."
            else -> "جرّب مرة ثانية لاحقاً."
        }
        val n = NotificationCompat.Builder(this, CH_DONE)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("تعذر التحميل")
            .setContentText(reason)
            .setAutoCancel(true)
            .build()
        nm.notify(DONE_BASE + id, n)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CH_PROGRESS = "khazna_progress"
        private const val CH_DONE = "khazna_done"
        private const val FG_ID = 1
        private const val DONE_BASE = 1000
        private const val ACTION_CANCEL = "com.clipsaver.app.CANCEL"
        private const val EX_JOB = "job"
        private const val EX_LINK = "link"
        private const val EX_MODE = "mode"
        private const val EX_QUALITY = "quality"
        private const val EX_AUDIO = "audio"
        private const val EX_NAME = "name"
        private val counter = AtomicInteger(0)

        fun start(ctx: Context, r: DlRequest) {
            val i = Intent(ctx, DownloadService::class.java).apply {
                putExtra(EX_LINK, r.link)
                putExtra(EX_MODE, r.mode.name)
                putExtra(EX_QUALITY, r.quality.name)
                putExtra(EX_AUDIO, r.audio.name)
                putExtra(EX_NAME, r.fileName)
            }
            ContextCompat.startForegroundService(ctx, i)
        }
    }
}
