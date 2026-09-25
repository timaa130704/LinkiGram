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
-keep class org.telegram.messenger.* { *; }
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

# https://developers.google.com/ml-kit/known-issues#android_issues
-keep class com.google.mlkit.nl.languageid.internal.LanguageIdentificationJni { *; }

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

# JLatexMath: macro/atom classes are loaded reflectively by Class.forName
-keep class org.scilab.forge.jlatexmath.** { *; }
-keep class ru.noties.jlatexmath.** { *; }
-dontwarn org.scilab.forge.jlatexmath.**

# Use -keep to explicitly keep any other classes shrinking would remove
#-dontoptimize
#-dontobfuscate