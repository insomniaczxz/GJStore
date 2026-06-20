# Retrofit rules
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature, InnerClasses

# Gson rules
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.stream.** { *; }

# Keep your data classes (models) so Gson can parse them
-keep class com.example.gjstore.data.** { *; }
-keep class com.example.gjstore.PendingAction { *; }
