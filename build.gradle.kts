plugins {
    // 8.9 rather than 8.7: lint loads the lint AARs bundled with the libraries
    // it checks, and the Compose 2025.08.00 BOM brings Compose 1.9 and (through
    // material3) lifecycle 2.9, whose detectors are compiled against a newer
    // Kotlin analysis API than 8.7.3's lint can load. It died with
    // IncompatibleClassChangeError before checking a single file. Pinning the
    // libraries back was the wrong lever -- it only moved the crash from one
    // detector to the next.
    id("com.android.application") version "8.9.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
