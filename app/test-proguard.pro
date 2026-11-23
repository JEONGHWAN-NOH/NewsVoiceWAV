-keep class com.newsvoice.tts.BroadcastCoverTest { *; }
-keepattributes *Annotation*
# These compile-time-only lint annotations are absent from the Android test runtime.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.MustBeClosed
-dontshrink
-dontobfuscate
-dontoptimize
