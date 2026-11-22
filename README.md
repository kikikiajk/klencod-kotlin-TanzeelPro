# كليب سيفر (ClipSaver)

تطبيق أندرويد بلغة **Kotlin + Jetpack Compose** لتنزيل مقاطع تيك توك ويوتيوب:
- يظهر في قائمة **المشاركة** (Share) — شارك الرابط وراح يتحلل تلقائياً.
- اختيار **فيديو / صوت فقط**.
- اختيار **الدقة** (أفضل / 1080 / 720 / 480 / 360) أو **صيغة الصوت** (MP3 / M4A).
- شريط تقدّم + إلغاء، وحفظ الملف في `التنزيلات/ClipSaver` بدون صلاحيات تخزين.
- واجهة عربية RTL بتصميم داكن بتدرّجات.

## طريقة البناء
1. افتح **Android Studio** (Koala أو أحدث) ← `File > Open` ← اختر مجلد `ClipSaver`.
2. انتظر مزامنة Gradle (تحتاج إنترنت أول مرة، وJDK 17 — يجي مع Android Studio).
3. وصّل جوالك (أو محاكي) وشغّل ▶ .
4. لملف APK: `Build > Build APK(s)`، تلقاه في `app/build/outputs/apk/`.

## هيكل المشروع
```
app/src/main/java/com/clipsaver/app/
 ├─ MainActivity.kt        استقبال المشاركة + عرض الشاشة
 ├─ DownloadViewModel.kt   الحالة والمنطق (تحليل، تنزيل، إلغاء)
 ├─ ClipEngine.kt          التعامل مع yt-dlp و ffmpeg وحفظ الملف
 ├─ Models.kt              الأنواع والحالات
 └─ ui/ (Theme, Components, MainScreen)   التصميم
```

## ملاحظات
- المكتبة المستخدمة: youtubedl-android (نسخة junkfood02) فيها yt-dlp و ffmpeg.
  حجم الـ APK يكبر (~50–70MB) لأنها تضم المحرك.
- التطبيق يحدّث yt-dlp تلقائياً بالخلفية عند فتحه.
- تنزيل يوتيوب يخالف شروط خدمتهم، فالتطبيق غير مناسب لمتجر Google Play. استخدمه للمحتوى المسموح لك.
- إذا ظهر خطأ بسطر `updateYoutubeDL` في `ClipEngine.kt` (اختلاف إصدار المكتبة)، احذف دالة `updateEngine` واستدعاءها بالـ ViewModel.
