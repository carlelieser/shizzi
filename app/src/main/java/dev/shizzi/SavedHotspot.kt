package dev.shizzi

import android.content.Context

private const val BAND_2GHZ = 1

/**
 * The SoftApConfiguration band to retry with when [savedBand] excludes 2.4 GHz,
 * which is how system settings fall back when the device refuses the saved band.
 * Null when 2.4 GHz is already allowed.
 */
fun hotspotBandFallback(savedBand: Int): Int? = when {
    savedBand and BAND_2GHZ != 0 -> null
    else -> savedBand or BAND_2GHZ
}

sealed interface BandFallback {
    data class Ready(val config: Any, val savedBand: Int, val band: Int) : BandFallback
    data class NotNeeded(val savedBand: Int) : BandFallback
    data class Unavailable(val reason: String) : BandFallback
}

class SavedHotspot(private val context: Context) {

    private val builderClass: Class<*>
        get() = Class.forName("android.net.wifi.SoftApConfiguration\$Builder")

    fun bandFallback(): BandFallback {
        val saved = runCatching { readSaved() }
            .getOrElse { return BandFallback.Unavailable("getSoftApConfiguration: ${reasonOf(it)}") }
        val savedBand = runCatching { saved.javaClass.getMethod("getBand").invoke(saved) as Int }
            .getOrElse { return BandFallback.Unavailable("getBand: ${reasonOf(it)}") }

        val band = hotspotBandFallback(savedBand) ?: return BandFallback.NotNeeded(savedBand)
        return runCatching { BandFallback.Ready(withBand(saved, band), savedBand, band) }
            .getOrElse { BandFallback.Unavailable("SoftApConfiguration.Builder: ${reasonOf(it)}") }
    }

    private fun readSaved(): Any {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE)
            ?: error("wifi service unavailable")
        return wifiManager.javaClass.getMethod("getSoftApConfiguration").invoke(wifiManager)
            ?: error("no saved hotspot configuration")
    }

    private fun withBand(saved: Any, band: Int): Any {
        val builder = builderClass.getConstructor(saved.javaClass).newInstance(saved)
        builderClass.getMethod("setBand", Int::class.javaPrimitiveType).invoke(builder, band)
        return builderClass.getMethod("build").invoke(builder)!!
    }

    private fun reasonOf(failure: Throwable): String? = failure.cause?.message ?: failure.message
}
