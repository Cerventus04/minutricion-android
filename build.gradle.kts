// Plugins declarados a nivel raíz (se aplican en :app). Versiones centralizadas aquí.
// Nota: fijadas a la combinación compatible con Paparazzi 1.3.5 (AGP 8.5.2 / Kotlin 2.0.20 /
// Gradle 8.7). Paparazzi 1.3.5 no soporta AGP 9.x, por eso NO se sube a 9.3.0.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
    id("app.cash.paparazzi") version "1.3.5" apply false   // render de pantallas a PNG (sin emulador)
}
