# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class dev.hush.client.** {
    *** Companion;
}
-keepclasseswithmembers class dev.hush.client.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class dev.hush.client.**$$serializer { *; }
