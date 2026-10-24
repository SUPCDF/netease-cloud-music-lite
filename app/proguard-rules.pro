# ================= 通用 =================
# 反射 / 序列化 / Retrofit 需要保留的元信息
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault, *Annotation*
# 崩溃栈可读（配合 mapping.txt 反混淆）
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# ================= kotlinx.serialization =================
# 官方推荐：保留 json 组件与生成的序列化器
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# 本项目的 @Serializable 模型（含生成的 $$serializer）
-keep class com.ncmusic.core.network.model.** { *; }
-keep,includedescriptorclasses class com.ncmusic.**$$serializer { *; }
-keepclassmembers class com.ncmusic.** {
    *** Companion;
}
-keepclasseswithmembers class com.ncmusic.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ================= Retrofit / OkHttp =================
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
# 接口方法上的 @GET/@POST/@Field 等注解由 Retrofit 反射读取
-keep,allowobfuscation interface com.ncmusic.core.network.api.** { *; }

# ================= Room =================
-keep class com.ncmusic.core.data.db.** { *; }
-dontwarn androidx.room.paging.**

# ================= 枚举 =================
# 主题 / 播放模式 / 音质按 name 存进 DataStore，再用 valueOf 还原
-keepclassmembers enum com.ncmusic.** { *; }

# ================= Media3 / 通知 =================
-dontwarn androidx.media3.**

# ================= 其他可选依赖 =================
-dontwarn kotlinx.coroutines.debug.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.jetbrains.annotations.**
-dontwarn java.lang.instrument.**
