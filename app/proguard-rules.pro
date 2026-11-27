# Matrix Rust SDK bindings use JNA and reflection.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class org.matrix.rustcomponents.sdk.** { *; }
-keep class uniffi.** { *; }
-dontwarn java.awt.**
-dontwarn com.sun.jna.**
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
