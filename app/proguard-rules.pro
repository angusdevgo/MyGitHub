# Kotlin 序列化模型防混淆
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
    @kotlinx.serialization.Serializable <fields>;
}
-keep class kotlinx.serialization.** { *; }
-keep class com.angus.mygithub.data.api.** { *; }
-keepclassmembers class com.angus.mygithub.data.api.** { *; }

# Retrofit & OkHttp
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Coil & Markdown
-dontwarn coil3.**
-dontwarn com.mikepenz.markdown.**

# Tink & ErrorProne
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn com.google.crypto.tink.**
-keep class com.google.crypto.tink.** { *; }
