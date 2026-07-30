# Taizi Release ProGuard Rules - Optimized

# Keep Taizi application entry points
-keep class com.taizi.MainActivity { *; }
-keep class com.taizi.TaiziApplication { *; }
-keep class com.taizi.di.AppModule { *; }

# Keep domain models (serialized with Gson)
-keep class com.taizi.domain.model.** { *; }
-keepclassmembers class com.taizi.domain.model.** {
    <init>(...);
    <fields>;
}

# Keep DataStore serializer
-keep class com.taizi.data.local.** { *; }

# Keep repository inner classes used by Gson
-keep class com.taizi.data.repository.** { *; }
-keepclassmembers class com.taizi.data.repository.** {
    <init>(...);
    <fields>;
}

# Keep scraper models and service
-keep class com.taizi.data.scraper.** { *; }
-keepclassmembers class com.taizi.data.scraper.** {
    <init>(...);
    <fields>;
}

# Hilt - Keep entry points and modules
-keep class * extends dagger.hilt.android.HiltAndroidApp { *; }
-keep class * extends dagger.hilt.android.components.** { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-keep @dagger.hilt.EntryPoint class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keepclassmembers @dagger.Module class * { *; }
-keepclassmembers @dagger.Provides class * { *; }
-keepclassmembers @dagger.Binds class * { *; }
-keepclassmembers @javax.inject.Inject class * { *; }

# Gson - Keep serialized names
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
    @com.google.gson.annotations.Expose <fields>;
}

# Coil - Keep image loading
-keep class coil.decode.** { *; }
-keep class coil.fetch.** { *; }

# Coroutines - Keep flow for Compose
-keepclassmembers class kotlinx.coroutines.flow.** { *; }

# Android Framework
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.content.ContentProvider
-keep public class * extends androidx.lifecycle.ViewModel

# Native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Parcelable
-keep class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Avoid warnings for libraries we trust
-dontwarn javax.lang.model.**
-dontwarn dagger.hilt.internal.**
-dontwarn com.google.gson.internal.**
-dontwarn com.google.auto.service.AutoService
-dontwarn com.taizi.Hilt_MainActivity
-dontwarn com.taizi.Hilt_TaiziApplication
-dontwarn javax.tools.Diagnostic$Kind

# Optimization settings
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*
-allowaccessmodification
-repackageclasses 't'
-dontusemixedcaseclassnames
-verbose

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(...);
    static void checkNotNullParameter(...);
    static void checkExpressionValueIsNotNull(...);
    static void checkNotNullExpressionValue(...);
}
