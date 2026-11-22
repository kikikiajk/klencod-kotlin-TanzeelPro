package com.clipsaver.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.clipsaver.app.ui.ClipSaverTheme
import com.clipsaver.app.ui.ShareDialog

/**
 * تستقبل "مشاركة" من تيك توك / يوتيوب وتعرض نافذة صغيرة فوق التطبيق نفسه
 * (بدون ما تفتح واجهة التطبيق). بعد "حسناً" تنسدّ النافذة والتنزيل يكمل بالإشعارات.
 */
class ShareReceiverActivity : ComponentActivity() {

    private val vm: ShareViewModel by viewModels()

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { begin() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        val text = if (intent?.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)
        } else null

        if (!vm.init(text)) {
            Toast.makeText(this, "ما لكيت رابط بالمشاركة", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            ClipSaverTheme {
                ShareDialog(vm = vm, onClose = { finish() }, onConfirm = { confirm() })
            }
        }
    }

    private fun confirm() {
        val needAsk = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needAsk) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else begin()
    }

    private fun begin() {
        val req = vm.buildRequest() ?: return
        DownloadService.start(applicationContext, req)
        Toast.makeText(this, "بدأ التحميل", Toast.LENGTH_SHORT).show()
        finish()
    }
}
