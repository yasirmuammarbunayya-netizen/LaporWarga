# Entitas Room tetap dipertahankan untuk build release.
-keep class com.example.tugashelloworld.model.** { *; }
-keep class com.example.tugashelloworld.db.** { *; }
-keepattributes Signature, *Annotation*
-dontwarn org.osmdroid.**
