# Project specific ProGuard rules for ChiliLeaf AI

# Keep line numbers and file names for debuggable stack traces in release builds
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# LiteRT (TensorFlow Lite) runtime classes and JNI interfaces
-keep class com.google.ai.edge.litert.** { *; }
-dontwarn com.google.ai.edge.litert.**

# Native methods called via JNI
-keepclasseswithmembernames class * {
    native <methods>;
}

# Domain models, classifier, and state data classes
-keep class com.example.chilileafdisease.ChiliLeafClassifier** { *; }
-keep class com.example.chilileafdisease.HistoryRecord { *; }
-keep class com.example.chilileafdisease.InspectionUiState** { *; }
-keep class com.example.chilileafdisease.ExifOrientationHelper** { *; }
-keep class com.example.chilileafdisease.TreatmentAdvisor** { *; }

# AndroidX Lifecycle / ViewModel preservation
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.AndroidViewModel { *; }
