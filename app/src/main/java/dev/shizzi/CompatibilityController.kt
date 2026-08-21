package dev.shizzi

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs the compatibility check and, where it can, the fix for a failed one.
 *
 * Owns [state] rather than reporting into the ViewModel's, because the three
 * operations here are one sequence — a check that finds a fixable device leads
 * to a download, which leads to an install — and splitting the transitions
 * across two owners is what would let them disagree.
 *
 * Holds no resources: [client] is the ViewModel's, and the downloader is built
 * per run.
 */
class CompatibilityController(
    private val context: Context,
    private val client: TetherClient,
    private val scope: CoroutineScope,
) {

    private val localState = MutableStateFlow<CompatibilityState>(CompatibilityState.Idle)
    val state: StateFlow<CompatibilityState> = localState.asStateFlow()

    /** Dropped when the wizard is restarted, so a stale verdict is not shown. */
    fun reset() {
        localState.value = CompatibilityState.Idle
    }

    /**
     * Resolves the two APIs the app rests on, through the shell.
     *
     * A thrown failure is [CompatibilityState.Failed] rather than a verdict of
     * incompatible: not reaching Shizuku says nothing about the device, and
     * telling a working handset it is unsupported is the worse error.
     */
    fun check() {
        if (localState.value is CompatibilityState.Checking) return
        localState.value = CompatibilityState.Checking

        scope.launch {
            localState.value = runCatching { client.checkCompatibility() }
                .fold(
                    onSuccess = { results -> verdictFor(results) },
                    onFailure = { failure ->
                        CompatibilityState.Failed(
                            "${failure.javaClass.simpleName}: ${failure.message}",
                        )
                    },
                )
        }
    }

    /**
     * Offers the module only to a device that would actually be fixed by it.
     *
     * The version guard is the second half of the test: APEX installs cannot be
     * downgraded, so a device already carrying this module version or a later
     * one would have the install rejected by apexd. Such a device reads as
     * [CompatibilityState.Complete] — accurate, and honest about there being
     * nothing left to try.
     */
    private fun verdictFor(results: List<CapabilityResult>): CompatibilityState {
        val installedVersion = TetheringModuleInfo(context).read().versionCode
        val canUpgradeModule = installedVersion == null ||
            installedVersion < TetheringApex.VERSION_CODE

        return when {
            results.isFixableByModuleInstall() && canUpgradeModule ->
                CompatibilityState.Fixable(results)

            else -> CompatibilityState.Complete(results)
        }
    }

    /**
     * Fetches the module into app storage and proves the bytes before offering
     * to install them.
     *
     * A digest or size mismatch fails here rather than being left for the
     * install path: nothing unverified may reach `pm install`.
     */
    fun downloadApex() {
        if (localState.value is CompatibilityState.Downloading) return
        localState.value = CompatibilityState.Downloading(DownloadProgress(0, 0))

        scope.launch {
            val downloaded = withContext(Dispatchers.IO) {
                ApexDownloader(context).download { progress ->
                    localState.value = CompatibilityState.Downloading(progress)
                }
            }

            localState.value = downloaded.fold(
                onSuccess = { file -> CompatibilityState.Downloaded(file.absolutePath) },
                onFailure = { failure ->
                    CompatibilityState.DownloadFailed(failure.asDownloadFailure())
                },
            )
        }
    }

    /**
     * Stages the downloaded module through the shell.
     *
     * A rejection is expected on a handset whose tethering APEX is OEM-signed:
     * apexd pins the signing key, so a Google-signed module is refused outright.
     * That verbatim reason is what the card shows, and the device falls back to
     * being unsupported rather than staying staged forever.
     */
    fun installApex() {
        val downloaded = localState.value as? CompatibilityState.Downloaded ?: return
        localState.value = CompatibilityState.Installing

        scope.launch {
            localState.value = runCatching { client.installTetheringApex(downloaded.path) }
                .fold(
                    onSuccess = { outcome -> stagingVerdict(outcome) },
                    onFailure = { failure ->
                        CompatibilityState.InstallFailed(
                            "${failure.javaClass.simpleName}: ${failure.message}",
                        )
                    },
                )
        }
    }

    private fun stagingVerdict(outcome: StagingOutcome): CompatibilityState = when {
        outcome.isStaged -> CompatibilityState.Staged
        else -> CompatibilityState.InstallFailed(outcome.rawOutput)
    }
}
