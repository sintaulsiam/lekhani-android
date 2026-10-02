# Lekhani Android ProGuard / R8 Rules

# UniFFI native bindings & JNI
-dontwarn com.lekhani.android.ffi.**
-keep class com.lekhani.android.ffi.** { *; }
-keep class com.lekhani.android.ffi.**$* { *; }
-keepclassmembers class com.lekhani.android.ffi.** { *; }
-keepclassmembers class com.lekhani.android.ffi.**$* { *; }
-keepclasseswithmembers class com.lekhani.android.ffi.** { *; }

# Keep all classes with native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# JNA Rules (Crucial for UniFFI runtime reflection and native dispatch)
-dontwarn com.sun.jna.**
-keep class com.sun.jna.** { *; }
-keepclassmembers class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.Library { *; }
-keepclassmembers class * implements com.sun.jna.Library { *; }
-keep class * implements com.sun.jna.Callback { *; }
-keepclassmembers class * implements com.sun.jna.Callback { *; }
-keep class * extends com.sun.jna.Structure { *; }
-keepclassmembers class * extends com.sun.jna.Structure { *; }
-keep class * implements com.sun.jna.Structure$ByValue { *; }
-keep class * implements com.sun.jna.Structure$ByReference { *; }
-keep class * extends com.sun.jna.ptr.ByReference { *; }
-keepclassmembers class * extends com.sun.jna.ptr.ByReference { *; }

# Lekhani Core Models & Preferences
-keep class com.lekhani.android.model.** { *; }
-keepclassmembers class com.lekhani.android.model.** { *; }
-keep class com.lekhani.android.data.** { *; }
-keepclassmembers class com.lekhani.android.data.** { *; }
-keep class com.lekhani.android.theme.** { *; }
-keepclassmembers class com.lekhani.android.theme.** { *; }

# Activities, Services, Custom Views
-keep class com.lekhani.android.ui.** { *; }
-keepclassmembers class com.lekhani.android.ui.** { *; }
-keep class com.lekhani.android.ime.** { *; }
-keepclassmembers class com.lekhani.android.ime.** { *; }
-keep class com.lekhani.android.canvas.** { *; }
-keepclassmembers class com.lekhani.android.canvas.** { *; }
-keep class com.lekhani.android.feedback.** { *; }
-keepclassmembers class com.lekhani.android.feedback.** { *; }
-keep class com.lekhani.android.voice.** { *; }
-keepclassmembers class com.lekhani.android.voice.** { *; }

# Kotlin Coroutines
-dontwarn kotlinx.coroutines.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
