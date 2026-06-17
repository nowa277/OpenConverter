# Keep the FfmpegBridge JNI symbol
-keep class com.openconverter.ffmpeg.FfmpegBridge { *; }
-keepclasseswithmembernames class * { native <methods>; }

# Keep decoders' public API (Kotlin metadata may otherwise strip)
-keep class com.openconverter.decoders.** { *; }
