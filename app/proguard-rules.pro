# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep Room entities
-keep class com.limette.tv.data.local.database.entity.** { *; }

# ExoPlayer
-keep class androidx.media3.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }

# smbj：通过反射构造 FileInformation，且依赖 SLF4J 门面
-keep class com.hierynomus.** { *; }
-dontwarn com.hierynomus.**
-dontwarn org.slf4j.**

# Gson：媒体源 / 飞牛配置的 DataStore JSON 持久化
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.limette.tv.domain.model.MediaSource { *; }
-keep class com.limette.tv.data.preferences.FeiNiuConfig { *; }
