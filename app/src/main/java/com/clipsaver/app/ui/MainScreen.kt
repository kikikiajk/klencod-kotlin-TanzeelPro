package com.clipsaver.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.ImeAction
import coil.compose.AsyncImage
import com.clipsaver.app.AudioFormat
import com.clipsaver.app.ClipInfo
import com.clipsaver.app.DownloadViewModel
import com.clipsaver.app.Mode
import com.clipsaver.app.Stage
import com.clipsaver.app.UiState
import com.clipsaver.app.VideoQuality

@Composable
fun MainScreen(vm: DownloadViewModel) {
    val s by vm.state.collectAsState()

    // الواجهة عربية فنفرض اتجاه اليمين لليسار
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(BgTop, BgBottom)))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Header()

                when {
                    s.engineError != null -> MessageCard(s.engineError!!, isError = true)
                    !s.engineReady -> EngineBanner()
                }

                LinkCard(s, vm)

                s.error?.let { MessageCard(it, isError = true) }

                when (s.stage) {
                    Stage.IDLE -> if (s.error == null) TipCard()
                    Stage.LOADING -> LoadingCard()
                    Stage.READY -> {
                        s.info?.let { InfoCard(it) }
                        OptionsCard(s, vm)
                        PrimaryButton("تنزيل الآن", Icons.Rounded.Download) { vm.startDownload() }
                    }
                    Stage.DOWNLOADING -> {
                        s.info?.let { InfoCard(it) }
                        ProgressCard(s, vm)
                    }
                    Stage.DONE -> {
                        s.info?.let { InfoCard(it) }
                        DoneCard(s, vm)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ───────────────────────── الأقسام ─────────────────────────

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(AccentBrush),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Download, null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("تنزيل برو", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text("نزّل مقاطعك بالجودة اللي تريدها", color = TextDim, fontSize = 13.sp)
        }
    }
}

@Composable
private fun EngineBanner() {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.5.dp,
                color = Cyan
            )
            Spacer(Modifier.width(12.dp))
            Text("جاري تجهيز محرك التنزيل (أول مرة فقط)...", color = Color.White, fontSize = 14.sp)
        }
    }
}

@Composable
private fun LinkCard(s: UiState, vm: DownloadViewModel) {
    val clipboard = LocalClipboardManager.current
    val busy = s.stage == Stage.LOADING || s.stage == Stage.DOWNLOADING

    GlassCard {
        SectionTitle("رابط المقطع")
        OutlinedTextField(
            value = s.url,
            onValueChange = vm::onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp, textDirection = TextDirection.Ltr),
            placeholder = { Text("الصق رابط تيك توك أو يوتيوب هنا", color = TextDim, fontSize = 13.sp) },
            trailingIcon = {
                IconButton(onClick = {
                    clipboard.getText()?.text?.let { vm.onUrlChange(it) }
                }, enabled = !busy) {
                    Icon(Icons.Rounded.ContentPaste, contentDescription = "لصق", tint = Cyan)
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { vm.analyze() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Violet,
                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                disabledBorderColor = Color.White.copy(alpha = 0.1f),
                cursorColor = Pink,
                disabledTextColor = TextDim
            )
        )
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            text = "تحليل الرابط",
            icon = Icons.Rounded.Search,
            enabled = !busy && s.url.isNotBlank()
        ) { vm.analyze() }
    }
}

@Composable
private fun TipCard() {
    GlassCard {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Share, null, tint = Cyan, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("طريقة أسرع", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "افتح المقطع في تيك توك أو يوتيوب، اضغط «مشاركة» ثم اختر «تنزيل برو». " +
                        "تطلع نافذة صغيرة تختار منها الاسم والجودة، والتحميل يكمل بالإشعارات.",
                    color = TextDim,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun LoadingCard() {
    GlassCard {
        StatusBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.5.dp,
                    color = Pink
                )
                Spacer(Modifier.width(12.dp))
                Text("جاري جلب معلومات المقطع...", color = Color.White, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun InfoCard(info: ClipInfo) {
    GlassCard {
        if (!info.thumbnail.isNullOrBlank()) {
            AsyncImage(
                model = info.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.05f))
            )
            Spacer(Modifier.height(12.dp))
        }
        Text(
            info.title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 24.sp
        )
        val meta = listOfNotNull(
            info.uploader.takeIf { it.isNotBlank() },
            info.durationSec.takeIf { it > 0 }?.let { formatDuration(it) }
        ).joinToString("  •  ")
        if (meta.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(meta, color = TextDim, fontSize = 13.sp)
        }
    }
}

@Composable
private fun OptionsCard(s: UiState, vm: DownloadViewModel) {
    GlassCard {
        SectionTitle("نوع التنزيل")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OptionChip("فيديو", Icons.Rounded.Movie, s.mode == Mode.VIDEO, Modifier.weight(1f)) {
                vm.setMode(Mode.VIDEO)
            }
            OptionChip("صوت فقط", Icons.Rounded.MusicNote, s.mode == Mode.AUDIO, Modifier.weight(1f)) {
                vm.setMode(Mode.AUDIO)
            }
        }
        Spacer(Modifier.height(18.dp))

        if (s.mode == Mode.VIDEO) {
            SectionTitle("الدقة")
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                VideoQuality.entries.forEach { q ->
                    OptionChip(q.label, null, s.quality == q) { vm.setQuality(q) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "إذا الدقة المطلوبة غير متوفرة، يختار التطبيق أقرب دقة أقل منها.",
                color = TextDim,
                fontSize = 12.sp
            )
        } else {
            SectionTitle("صيغة الصوت")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AudioFormat.entries.forEach { f ->
                    OptionChip(f.label, null, s.audioFormat == f) { vm.setAudioFormat(f) }
                }
            }
        }
    }
}

@Composable
private fun ProgressCard(s: UiState, vm: DownloadViewModel) {
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("جاري التنزيل...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("${(s.progress * 100).toInt()}%", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Spacer(Modifier.height(12.dp))
        if (s.progress > 0f) {
            LinearProgressIndicator(
                progress = { s.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = Pink,
                trackColor = Color.White.copy(alpha = 0.12f)
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = Pink,
                trackColor = Color.White.copy(alpha = 0.12f)
            )
        }
        if (s.etaSec > 0) {
            Spacer(Modifier.height(8.dp))
            Text("المتبقي تقريباً: ${s.etaSec} ثانية", color = TextDim, fontSize = 12.sp)
        }
        Spacer(Modifier.height(14.dp))
        SecondaryButton("إلغاء", Modifier.fillMaxWidth(), Icons.Rounded.Close) { vm.cancel() }
    }
}

@Composable
private fun DoneCard(s: UiState, vm: DownloadViewModel) {
    val context = LocalContext.current
    GlassCard(borderColor = Green.copy(alpha = 0.5f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, null, tint = Green, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("تم التنزيل بنجاح", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(s.saved?.place.orEmpty(), color = TextDim, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton("فتح الملف", Icons.Rounded.PlayArrow) {
            s.saved?.let { f ->
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(f.uri, f.mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    context.startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("صيغة ثانية", Modifier.weight(1f), Icons.Rounded.Refresh) { vm.backToOptions() }
            SecondaryButton("رابط جديد", Modifier.weight(1f), Icons.Rounded.Search) { vm.reset() }
        }
    }
}

@Composable
private fun MessageCard(text: String, isError: Boolean) {
    GlassCard(borderColor = if (isError) Red.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Rounded.ErrorOutline,
                null,
                tint = if (isError) Red else Cyan,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(text, color = Color.White, fontSize = 14.sp, lineHeight = 22.sp)
        }
    }
}

private fun formatDuration(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
