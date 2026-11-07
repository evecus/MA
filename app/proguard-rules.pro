# Keep Shizuku
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }

# Keep libsu
-keep class com.topjohnwu.superuser.** { *; }

# zip4j（通过文件扩展名反射加载加密策略）
-keep class net.lingala.zip4j.** { *; }
-dontwarn net.lingala.zip4j.**

# commons-compress / xz / commons-io（ServiceLoader 加载压缩实现）
-keep class org.apache.commons.compress.** { *; }
-keep class org.tukaani.xz.** { *; }
-dontwarn org.apache.commons.compress.**
-dontwarn org.tukaani.xz.**

# media3 自带 consumer rules，无需额外 keep；仅屏蔽无关告警
-dontwarn androidx.media3.**
