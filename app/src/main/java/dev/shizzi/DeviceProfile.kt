package dev.shizzi

import android.os.Build

data class DeviceProfile(val sdkInt: Int, val supportedAbis: List<String>) {

    companion object {
        fun current(): DeviceProfile = DeviceProfile(Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS.toList())
    }
}
