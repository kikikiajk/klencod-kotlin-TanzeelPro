package com.clipsaver.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipsaver.app.AudioFormat
import com.clipsaver.app.Dest
import com.clipsaver.app.Mode
import com.clipsaver.app.Platform
import com.clipsaver.app.ShareState
import com.clipsaver.app.ShareViewModel
import com.clipsaver.app.VideoQuality

private val CardBg = Color(0xFF1B1440)

@Composable
fun ShareDialog(vm: ShareViewModel, onClose: () -> Unit, onConfirm: () -> Unit) {
    val s by vm.state.collectAsState()
    val noRipple = remember { MutableInteractionSource() }
    val noRipple2 = remember { MutableInteractionSource() }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(interactionSource = noRipple, indication = null) { onClose() }
                .systemBarsPadding()
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 22.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(CardBg)
                    // نمنع الضغطة داخل البطاقة من إغلاق النافذة
                    .clickable(interactionSource = noRipple2, indication = null) { }
                    .verticalScroll(rememberScrollState())
            ) {
                // الشريط العلوي: حسناً | تنزيل | إغلاق
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AccentBrush)
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onConfirm,
                        enabled = s.canConfirm,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color.White,
                            disabledContentColor = Color.White.copy(alpha = 0.4f)
                        )
                    ) {
                        Text("حسناً", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Text(
                        "تنزيل",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.Close, contentDescription = "إغلاق", tint = Color.White)
                    }
                }

                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    NameField(s, vm)
                    ModeRow(s, vm)
                    OptionPicker(s, vm)
                    if (s.askFolder) FolderField(s, vm)
                    SaveHint(s)
                }
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = Violet,
    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
    cursorColor = Pink
)

@Composable
private fun Label(text: String) {
    Text(text, color = TextDim, fontSize = 13.sp)
}

@Composable
private fun NameField(s: ShareState, vm: ShareViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label("اسم الملف")
        OutlinedTextField(
            value = s.fileName,
            onValueChange = vm::setName,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            placeholder = {
                Text(
                    if (s.infoLoading) "جاري جلب العنوان..." else "اسم الملف (اختياري)",
                    color = TextDim,
                    fontSize = 13.sp
                )
            },
            trailingIcon = {
                if (s.infoLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Cyan
                    )
                }
            },
            colors = fieldColors()
        )
        if (s.infoFailed) {
            Text("تعذر جلب العنوان، راح ينحفظ بالاسم الأصلي.", color = TextDim, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ModeRow(s: ShareState, vm: ShareViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
        ModeOption("الفيديو", s.mode == Mode.VIDEO) { vm.setMode(Mode.VIDEO) }
        ModeOption("الصوت", s.mode == Mode.AUDIO) { vm.setMode(Mode.AUDIO) }
    }
}

@Composable
private fun ModeOption(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = Pink,
                unselectedColor = TextDim
            )
        )
        Text(text, color = Color.White, fontSize = 16.sp)
    }
}

@Composable
private fun OptionPicker(s: ShareState, vm: ShareViewModel) {
    val isVideo = s.mode == Mode.VIDEO
    val title = if (isVideo) "الدقة" else "صيغة الصوت"
    val current = if (isVideo) s.quality.label else s.audioFormat.label
    val labels: List<String> =
        if (isVideo) VideoQuality.entries.map { it.label } else AudioFormat.entries.map { it.label }

    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label(title)
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .clickable { expanded = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(current, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = TextDim)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(CardBg)
            ) {
                labels.forEachIndexed { index, label ->
                    DropdownMenuItem(
                        text = { Text(label, color = Color.White) },
                        onClick = {
                            expanded = false
                            if (isVideo) vm.setQuality(VideoQuality.entries[index])
                            else vm.setAudioFormat(AudioFormat.entries[index])
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderField(s: ShareState, vm: ShareViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label("اختر اسم مجلد لحفظ صوتيات اليوتيوب")
        OutlinedTextField(
            value = s.folderName,
            onValueChange = vm::setFolder,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            placeholder = { Text("مثال: ${Dest.YOUTUBE_AUDIO_DEFAULT}", color = TextDim, fontSize = 13.sp) },
            colors = fieldColors()
        )
        Text("مرة وحدة بس، والتطبيق يتذكره للتحميلات الجاية.", color = TextDim, fontSize = 12.sp)
    }
}

@Composable
private fun SaveHint(s: ShareState) {
    val ctxFolder = when {
        s.mode == Mode.VIDEO -> "المعرض ‹ ${Dest.VIDEO_ALBUM}"
        s.platform == Platform.TIKTOK -> "الموسيقى ‹ ${Dest.TIKTOK_AUDIO}"
        s.platform == Platform.YOUTUBE ->
            "الموسيقى ‹ " + (s.folderName.takeIf { it.isNotBlank() } ?: Dest.YOUTUBE_AUDIO_DEFAULT)
        else -> "الموسيقى ‹ ${Dest.OTHER_AUDIO}"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("راح ينحفظ في:", color = TextDim, fontSize = 12.sp)
        Spacer(Modifier.width(6.dp))
        Text(ctxFolder, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
