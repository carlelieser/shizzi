package dev.shizzi

import android.os.Build

sealed interface CompatibilityState {

    data object Idle : CompatibilityState

    data object Checking : CompatibilityState

    data class Complete(val results: List<CapabilityResult>) : CompatibilityState

    data class Failed(val problem: String) : CompatibilityState

    data class Fixable(val results: List<CapabilityResult>) : CompatibilityState

    data class Downloading(
        val results: List<CapabilityResult>,
        val progress: DownloadProgress,
    ) : CompatibilityState

    data class Downloaded(
        val results: List<CapabilityResult>,
        val path: String,
    ) : CompatibilityState

    data class Installing(val results: List<CapabilityResult>) : CompatibilityState

    data class Staged(val results: List<CapabilityResult>) : CompatibilityState

    data class DownloadFailed(
        val results: List<CapabilityResult>,
        val failure: DownloadFailure,
    ) : CompatibilityState

    data class InstallFailed(
        val results: List<CapabilityResult>,
        val reason: String,
    ) : CompatibilityState
}

val CompatibilityState.isCompatible: Boolean
    get() = this is CompatibilityState.Complete && results.all { it.isPresent }

val CompatibilityState.isOnFixPath: Boolean
    get() = this is CompatibilityState.Fixable ||
        this is CompatibilityState.Downloading ||
        this is CompatibilityState.Downloaded ||
        this is CompatibilityState.Installing ||
        this is CompatibilityState.Staged ||
        this is CompatibilityState.DownloadFailed ||
        this is CompatibilityState.InstallFailed

val CompatibilityState.reportedResults: List<CapabilityResult>
    get() = when (this) {
        is CompatibilityState.Complete -> results
        is CompatibilityState.Fixable -> results
        is CompatibilityState.Downloading -> results
        is CompatibilityState.Downloaded -> results
        is CompatibilityState.Installing -> results
        is CompatibilityState.Staged -> results
        is CompatibilityState.DownloadFailed -> results
        is CompatibilityState.InstallFailed -> results
        else -> emptyList()
    }

fun List<CapabilityResult>.isFixableByModuleInstall(): Boolean {
    val hasTestNetwork = isPresent(Capability.TEST_NETWORK)
    val needsPreference = !isPresent(Capability.PREFER_TEST_NETWORKS)
    val isInstallableRelease = Build.VERSION.SDK_INT in TetheringApex.INSTALLABLE_SDK_INTS

    return hasTestNetwork && needsPreference && isInstallableRelease
}

private fun List<CapabilityResult>.isPresent(capability: Capability): Boolean =
    firstOrNull { it.capability == capability }?.isPresent == true
