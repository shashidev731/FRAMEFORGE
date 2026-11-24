# FRAMEFORGE Proguard Rules
-keepattributes *Annotation*
-dontwarn androidx.media3.**
-keep class androidx.media3.** { *; }
-keep class com.frameforge.app.data.** { *; }
