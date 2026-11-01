# Lena has no reflection of its own. The Jetpack libraries ship their own keep rules.

# LiteRT finds its Java classes from native code by name.
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**
