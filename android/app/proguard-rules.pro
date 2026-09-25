# Lekhani Android ProGuard / R8 Rules

# UniFFI native bindings & JNI
-keep class com.lekhani.android.ffi.** { *; }
-keep interface com.lekhani.android.ffi.** { *; }
-keepclassmembers class com.lekhani.android.ffi.** { *; }

# Keep all classes with native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Lekhani Core Models & Preferences
-keep class com.lekhani.android.model.** { *; }
-keep class com.lekhani.android.data.** { *; }
-keep class com.lekhani.android.theme.** { *; }
