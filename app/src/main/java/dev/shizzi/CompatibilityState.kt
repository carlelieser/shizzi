package dev.shizzi

import android.os.Build

/**
 * Where the compatibility check is.
 *
 * Separate from [DiagnosticsState] despite the shape rhyming: that one carries
 * a report meant to be exported and read by a developer, this one carries per
 * capability answers a user is shown. Folding them would make every consumer
 * ask which kind of run produced the state it holds.
 */
sealed interface CompatibilityState {

    /** Nothing has been asked yet. The step offers Check. */
    data object Idle : CompatibilityState

    data object Checking : CompatibilityState

    /**
     * [results] holds an entry per [Capability], in declaration order, whether
     * or not the check could answer it.
     */
    data class Complete(val results: List<CapabilityResult>) : CompatibilityState

    /**
     * The check could not run at all — Shizuku went away, or the daemon is
     * stale. Distinct from a run that answered "not compatible": one is a
     * verdict about the device, the other is the app failing to reach it.
     */
    data class Failed(val problem: String) : CompatibilityState

    /**
     * The check failed, and installing the tethering module would fix it.
     *
     * Distinct from a [Complete] that came back incompatible: this device is
     * only missing a Mainline module, which the app can supply. Carries the
     * results so the cards behind the offer still show what was actually found.
     *
     * @see isFixableByModuleInstall for exactly when this is reached.
     */
    data class Fixable(val results: List<CapabilityResult>) : CompatibilityState

    /**
     * Every state past [Fixable] carries [results] for the same reason it does:
     * the rows above the card are the explanation for the offer, and they have
     * already been answered. Dropping them mid-install would replace a finding
     * with a spinner over a question the check has not re-asked.
     */
    data class Downloading(
        val results: List<CapabilityResult>,
        val progress: DownloadProgress,
    ) : CompatibilityState

    /** Bytes on disk, digest checked. [path] is what the shell is handed. */
    data class Downloaded(
        val results: List<CapabilityResult>,
        val path: String,
    ) : CompatibilityState

    data class Installing(val results: List<CapabilityResult>) : CompatibilityState

    /**
     * apexd took the module and will mount it on the next boot.
     *
     * Not compatible yet — [isCompatible] stays false — because the method the
     * app needs does not resolve until the module is mounted.
     */
    data class Staged(val results: List<CapabilityResult>) : CompatibilityState

    data class DownloadFailed(
        val results: List<CapabilityResult>,
        val failure: DownloadFailure,
    ) : CompatibilityState

    /** [reason] is pm's own output, which is the only account of why. */
    data class InstallFailed(
        val results: List<CapabilityResult>,
        val reason: String,
    ) : CompatibilityState
}

/**
 * True only once every capability has answered present.
 *
 * A check that never ran is not compatibility, so [CompatibilityState.Idle] and
 * [CompatibilityState.Failed] are false rather than unknown — the button past
 * this step reads the same value. A staged device is false too: the module is
 * not mounted until it reboots, so the API still does not resolve.
 */
val CompatibilityState.isCompatible: Boolean
    get() = this is CompatibilityState.Complete && results.all { it.isPresent }

/**
 * True from the moment the module is offered until the device reboots.
 *
 * The step shows its fix cards instead of a verdict mark across all of these:
 * the device has not been judged incompatible, it has been offered something to
 * do about it.
 */
val CompatibilityState.isOnFixPath: Boolean
    get() = this is CompatibilityState.Fixable ||
        this is CompatibilityState.Downloading ||
        this is CompatibilityState.Downloaded ||
        this is CompatibilityState.Installing ||
        this is CompatibilityState.Staged ||
        this is CompatibilityState.DownloadFailed ||
        this is CompatibilityState.InstallFailed

/**
 * The results behind a fix-path state, so the capability rows keep showing what
 * the check actually found while the module is being fetched.
 *
 * Held across the whole fix path: the module install changes what a re-run
 * would find, not what this run found, and the rows are the reason the offer is
 * on screen at all.
 */
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

/**
 * Whether installing the tethering module would turn this run into a pass.
 *
 * Deliberately narrow. The offer is only honest when the single missing piece
 * is the one the module carries:
 *
 *  - [Capability.TEST_NETWORK] already passes. It comes from the platform, not
 *    from the APEX, so a device failing it is not fixed by any module.
 *  - [Capability.PREFER_TEST_NETWORKS] is the only failure.
 *  - The release is one where the platform lacks the method but the module can
 *    supply it ([TetheringApex.INSTALLABLE_SDK_INTS]).
 *
 * Anything else stays unsupported. Promising a fix a device cannot take is
 * worse than reporting it accurately.
 */
fun List<CapabilityResult>.isFixableByModuleInstall(): Boolean {
    val hasTestNetwork = isPresent(Capability.TEST_NETWORK)
    val needsPreference = !isPresent(Capability.PREFER_TEST_NETWORKS)
    val isInstallableRelease = Build.VERSION.SDK_INT in TetheringApex.INSTALLABLE_SDK_INTS

    return hasTestNetwork && needsPreference && isInstallableRelease
}

private fun List<CapabilityResult>.isPresent(capability: Capability): Boolean =
    firstOrNull { it.capability == capability }?.isPresent == true
