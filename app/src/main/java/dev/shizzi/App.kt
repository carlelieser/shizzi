package dev.shizzi

import android.app.Application
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Holds the app Context for [ShizukuGate] and lifts hidden-API restrictions in
 * the app process.
 *
 * The bypass matters for the app process only; the Shizuku shell process is not
 * subject to the same enforcement, but the app still reflects over
 * TetheringManager when reporting state.
 */
class App : Application() {

    /**
     * Owned here rather than by a ViewModel: the session service reads the
     * logging setting too, and it has no ViewModel to read it from.
     */
    val settingsStore: SettingsStore by lazy { SettingsStore(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Before anything that might log: the app process cannot write to the
        // shell's directory, so its entries would go nowhere until this runs.
        SessionLog.useAppStorage(filesDir)
        applyLoggingSetting()

        liftHiddenApiRestrictions()
    }

    /**
     * Applies the stored logging setting to this process on launch.
     *
     * The store is read asynchronously, so entries written before it arrives
     * follow the default. That window is short and the default is on, which
     * errs toward recording rather than silently dropping a launch failure.
     */
    private fun applyLoggingSetting() {
        CoroutineScope(Dispatchers.IO).launch {
            val isLogging = settingsStore.settings.first().isLogging
            SessionLog.setEnabled(isLogging)
        }
    }

    /**
     * Android 9+ blocks reflection onto non-SDK members. HiddenApiBypass clears
     * that for this process; without it the TestNetworkManager lookups throw
     * NoSuchMethodException that looks identical to the API genuinely being
     * absent — a distinction the probes exist to make.
     */
    private fun liftHiddenApiRestrictions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        HiddenApiBypass.addHiddenApiExemptions("Landroid/net/", "L")
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
