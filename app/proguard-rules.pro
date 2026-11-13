# Keep serializable model metadata used by kotlinx.serialization.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class org.liaocl.careeyes.data.** {
    *** Companion;
}
-keepclasseswithmembers class org.liaocl.careeyes.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
