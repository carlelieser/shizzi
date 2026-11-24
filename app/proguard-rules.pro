-keep class dev.shizzi.TetherService { *; }

-keep interface dev.shizzi.ITetherService { *; }
-keep class dev.shizzi.ITetherService$* { *; }

-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**

-keep class org.lsposed.hiddenapibypass.** { *; }
-dontwarn org.lsposed.hiddenapibypass.**

-keep class dev.shizzi.App
-keep class dev.shizzi.MainActivity
-keep class dev.shizzi.SessionService

-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepattributes Signature,InnerClasses,EnclosingMethod

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# adb-started helper: launched by name through app_process, receiver/service by manifest.
-keep class dev.shizzi.TetherMain { *; }
-keep class dev.shizzi.AdbHelperReceiver
-keep class dev.shizzi.AdbPairReplyReceiver
-keep class dev.shizzi.AdbPairingService
-keep class dev.shizzi.AdbPairActivity
-keep class io.github.muntashirakon.adb.** { *; }
-dontwarn io.github.muntashirakon.adb.**
-dontwarn org.conscrypt.**
