# Reglas de ProGuard (release). Ofuscación suave + keeps de seguridad.

# Modelos de datos de la app (se leen/serializan por nombre en algún punto).
-keep class org.ivansola.minutricion.data.** { *; }

# Kotlin metadata / firmas.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-dontwarn kotlinx.**

# org.json viene del framework Android.
-dontwarn org.json.**

# ML Kit / CameraX traen sus propias reglas de consumidor; solo silenciamos avisos.
-dontwarn com.google.mlkit.**
-dontwarn androidx.camera.**
