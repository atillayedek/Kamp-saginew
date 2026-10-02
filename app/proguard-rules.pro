# kotlinx.serialization: keep generated serializers for DTOs and navigation routes.
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers @kotlinx.serialization.Serializable class com.kampusagi.android.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.kampusagi.android.**$$serializer { *; }

# Ktor / Supabase reference JVM-only classes that do not exist on Android.
-dontwarn java.lang.management.**
-dontwarn org.slf4j.**
-dontwarn org.slf4j.impl.StaticLoggerBinder

# Strip verbose/debug logging calls from release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
