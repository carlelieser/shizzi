package dev.shizzi

import android.app.Application
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.lsposed.hiddenapibypass.HiddenApiBypass

class App : Application() {

    val settingsStore: SettingsStore by lazy { SettingsStore(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this

        SessionLog.useAppStorage(filesDir)
        applyLoggingSetting()

        liftHiddenApiRestrictions()
    }

    private fun applyLoggingSetting() {
        CoroutineScope(Dispatchers.IO).launch {
            val isLogging = settingsStore.settings.first().isLogging
            SessionLog.setEnabled(isLogging)
        }
    }

    private fun liftHiddenApiRestrictions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        HiddenApiBypass.addHiddenApiExemptions("Landroid/net/", "L")
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
