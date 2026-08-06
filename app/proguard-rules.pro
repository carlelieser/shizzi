# R8 rules for the release build.
#
# Everything here exists because something outside this APK reaches into it by
# name. R8 reasons about the call graph it can see; a class only ever
# instantiated by the framework, by Shizuku, or across a binder looks unused to
# it, and the failure lands at runtime rather than at build time.

# --- Shizuku's user service ------------------------------------------------
#
# TetherService runs in a separate, shell-uid process that Shizuku starts. It
# is named as a string in UserServiceArgs and instantiated reflectively, via
# either the no-arg or the Context constructor. Renaming the class breaks the
# lookup; stripping a constructor breaks instantiation with a NoSuchMethod that
# reads like Shizuku itself is broken.
-keep class dev.shizzi.TetherService { *; }

# --- The AIDL contract -----------------------------------------------------
#
# Both sides of the binder must agree on names. The Stub/Proxy machinery is
# generated and dispatches on method identity, so it cannot be renamed on one
# side only — and the two sides here are two different processes.
-keep interface dev.shizzi.ITetherService { *; }
-keep class dev.shizzi.ITetherService$* { *; }

# --- Shizuku itself --------------------------------------------------------
#
# The library's own binder surface, called from the shell process.
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**

# --- Hidden framework APIs -------------------------------------------------
#
# HiddenApi.kt reflects onto android.net.* internals. Those classes live in the
# framework, not this APK, so R8 does not rename them and no keep rule applies
# to them. What does matter is that the reflection is driven by string literals
# R8 must not fold away, and HiddenApiBypass reflects onto its own targets.
-keep class org.lsposed.hiddenapibypass.** { *; }
-dontwarn org.lsposed.hiddenapibypass.**

# The framework instantiates these by name from the manifest.
-keep class dev.shizzi.App
-keep class dev.shizzi.MainActivity
-keep class dev.shizzi.SessionService

# --- Kotlin metadata -------------------------------------------------------
#
# Kept so reflective constructor lookup on TetherService resolves parameter
# types as declared rather than as R8's rewritten signatures.
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepattributes Signature,InnerClasses,EnclosingMethod

# --- Diagnostics -----------------------------------------------------------
#
# Line numbers survive into release stack traces, which the probe report and
# the log screen surface verbatim. Without these a user-reported failure names
# an obfuscated frame and says nothing. The source file name is renamed rather
# than kept, since it adds nothing once line numbers are present.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
