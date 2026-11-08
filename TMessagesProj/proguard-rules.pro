-keep public class com.google.android.gms.* { public *; }
-keepnames @com.google.android.gms.common.annotation.KeepName class *
-keepclassmembernames class * {
    @com.google.android.gms.common.annotation.KeepName *;
}

-keep @interface androidx.annotation.Keep
-keep @androidx.annotation.Keep class * { *; }
-keepclasseswithmembers class * { @androidx.annotation.Keep *; }

-keep class org.webrtc.* { *; }
-keep class org.webrtc.audio.* { *; }
-keep class org.webrtc.voiceengine.* { *; }
# org.telegram.messenger: only the names that native code and reflection resolve at runtime.
# Before changing these rules see TMessagesProj/proguard-checklist.md and run Tools/r8_keep_check.py.
# Static JNI exports Java_org_telegram_messenger_<Class>_<method> (Utilities, Intro, MediaController, MrzRecognizer).
-keepclasseswithmembers,includedescriptorclasses class org.telegram.messenger.* {
    native <methods>;
}
# gifvideo.cpp: FindClass + GetMethodID, called from the native reader.
-keep class org.telegram.messenger.AnimatedFileDrawableStream {
    int read(int, int);
    void cancel();
    boolean isFinishedLoadingFile();
    boolean isCanceled();
    java.lang.String getFinishedFilePath();
}
# NotificationBadge.BADGERS holds class literals, instances are made with Class.newInstance().
-keep class org.telegram.messenger.NotificationBadge$* implements org.telegram.messenger.NotificationBadge$Badger {
    <init>();
}
-keep class org.telegram.messenger.camera.* { *; }
-keep class org.telegram.messenger.secretmedia.* { *; }
-keep class org.telegram.messenger.support.* { *; }
-keep class org.telegram.messenger.support.* { *; }
-keep class org.telegram.messenger.time.* { *; }
-keep class org.telegram.messenger.video.* { *; }
-keep class org.telegram.messenger.voip.* { *; }
-keep class org.telegram.SQLite.** { *; }
-keep class org.telegram.tgnet.ConnectionsManager { *; }
-keep class org.telegram.tgnet.NativeByteBuffer { *; }
-keep class org.telegram.tgnet.RequestTimeDelegate { *; }
-keep class org.telegram.tgnet.RequestDelegate { *; }
-keep class org.telegram.ui.Stories.recorder.FfmpegAudioWaveformLoader { *; }

# Plugin compatibility: Python plugins (Chaquopy) and Pine hooks look up arbitrary
# org.telegram classes by their ORIGINAL names. A plugin that draws its button in the
# bottom panel hooks org.telegram.ui.MainTabsActivity, and R8 had minified that name
# away, so on_load died with ClassNotFoundException and the plugin loaded with no UI at
# all. The rules above only covered org.telegram.messenger.*, so the UI classes the fork
# itself ships (org.telegram.ui.MainTabsActivity and friends) were not kept. Matches
# what Ettacent/NimarkoGram does, for the same reason.
-keep class org.telegram.** { *; }
-keep class app.nimarkogram.messenger.plugins.PluginServices { *; }
-keep class app.nimarkogram.messenger.plugins.utils.ClassProxyFactory { *; }
-keep class app.nimarkogram.messenger.plugins.utils.ClassProxyFactory$* { *; }
-keep class androidx.mediarouter.app.MediaRouteButton { *; }
-keepclassmembers class ** {
    @android.webkit.JavascriptInterface <methods>;
}

# LinkiGram fork packages.
#
# The standalone build type runs R8 with minifyEnabled + shrinkResources, and
# none of these packages are reachable from anything R8 treats as a root: the
# fork hooks into org.telegram internals, and its screens are entered through
# reflective/indirect paths. The effect was that release 2.3 shipped with
# app.nimarkogram.messenger.preferences, com.exteragram and de.robv stripped
# out of the dex entirely, even though SettingsActivity references
# MainPreferencesActivity directly -- the settings hub was unreachable at
# runtime while the source tree looked complete.
#
# Keep the fork's own namespaces explicitly. Telegram's own classes are already
# covered by the org.telegram.messenger.* rules above.
-keep class app.nimarkogram.** { *; }
-keep class com.exteragram.** { *; }
-keep class de.robv.** { *; }
-keep class top.canyie.pine.** { *; }

# Plugin/settings classes are also loaded by name from the plugin loader.
-keepclassmembers class app.nimarkogram.** {
    public <init>(...);
}
-keepclassmembers class com.exteragram.** {
    public <init>(...);
}
-keep class org.telegram.utils.code.highlight.PrismaHighlighter {
    private static native <methods>;
}

# https://developers.google.com/ml-kit/known-issues#android_issues
-keep class com.google.mlkit.nl.languageid.internal.LanguageIdentificationJni { *; }

# WidgetSandbox: JNI_OnLoad uses RegisterNatives; backend methods are called by native workers.
-keep class org.telegram.utils.twidget.WidgetSandboxNative { *; }
-keep class org.telegram.utils.twidget.WidgetSandboxBackend { *; }

# Huawei Services
-keep class com.huawei.hianalytics.**{ *; }
-keep class com.huawei.updatesdk.**{ *; }
-keep class com.huawei.hms.**{ *; }

# Don't warn about checkerframework and Kotlin annotations
# MVEL references the optional Java scripting API on Android.
-dontwarn javax.script.AbstractScriptEngine
-dontwarn javax.script.Compilable
-dontwarn javax.script.ScriptEngine
-dontwarn javax.script.ScriptEngineFactory

-dontwarn org.checkerframework.**
-dontwarn javax.annotation.**

-keep class io.nano.tex.** {*;}

-keep class org.telegram.tgnet.** { *; }

# JLatexMath: macro/atom classes are loaded reflectively by Class.forName
-keep class org.scilab.forge.jlatexmath.** { *; }
-keep class ru.noties.jlatexmath.** { *; }
-dontwarn org.scilab.forge.jlatexmath.**

# Use -keep to explicitly keep any other classes shrinking would remove
#-dontoptimize
#-dontobfuscate

# Offline Vosk recognizer uses JNA bindings by name.
-keep class org.vosk.** { *; }
-keep class com.sun.jna.** { *; }
-dontwarn com.sun.jna.**

# JNI session portability callbacks, including lambda implementations.
-keep interface org.telegram.tgnet.ConnectionsManager$SessionCallback { *; }
-keepclassmembers class * implements org.telegram.tgnet.ConnectionsManager$SessionCallback {
    public void onResult(byte[]);
}
