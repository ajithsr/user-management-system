# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.sliide.usermanagement.**$$serializer { *; }
-keepclassmembers class com.sliide.usermanagement.** {
    *** Companion;
}
-keepclasseswithmembers class com.sliide.usermanagement.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor
-keep class io.ktor.** { *; }
-keep class kotlinx.coroutines.** { *; }

# SQLDelight
-keep class app.cash.sqldelight.** { *; }

# Koin
-keep class org.koin.** { *; }
