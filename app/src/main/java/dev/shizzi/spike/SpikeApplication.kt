package dev.shizzi.spike

import android.app.Application
import android.os.Build
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Holds the app Context for [ShizukuGate] and lifts hidden-API restrictions in
 * the app process.
 *
 * The bypass matters for the app process only; the Shizuku shell process is not
 * subject to the same enforcement, but the app still reflects over
 * TetheringManager when reporting state.
 */
class SpikeApplication : Application() {

    /**
     * Owned here rather than by a ViewModel: the session service reads debug
     * logging too, and it has no ViewModel to read it from.
     */
    val settingsStore: SettingsStore by lazy { SettingsStore(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        liftHiddenApiRestrictions()
    }

    /**
     * Android 9+ blocks reflection onto non-SDK members. HiddenApiBypass clears
     * that for this process; without it the TestNetworkManager lookups throw
     * NoSuchMethodException that looks identical to the API genuinely being
     * absent — a distinction the spike exists to make.
     */
    private fun liftHiddenApiRestrictions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        HiddenApiBypass.addHiddenApiExemptions("Landroid/net/", "L")
    }

    companion object {
        lateinit var instance: SpikeApplication
            private set
    }
}
