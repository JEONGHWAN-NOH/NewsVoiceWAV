# Keep the app entry points and package-private APIs exercised by instrumentation.
-keep class com.newsvoice.tts.** { *; }
-dontobfuscate
# Optional JVM-only TLS providers probed by OkHttp; Android uses its platform TLS.
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
