package dev.shizzi

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CompatibilityController(
    private val context: Context,
    private val client: TetherClient,
    private val scope: CoroutineScope,
) {

    private val localState = MutableStateFlow<CompatibilityState>(CompatibilityState.Idle)
    val state: StateFlow<CompatibilityState> = localState.asStateFlow()

    fun reset() {
        localState.value = CompatibilityState.Idle
    }

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

    fun downloadApex() {
        if (localState.value is CompatibilityState.Downloading) return

        val results = localState.value.reportedResults
        localState.value = CompatibilityState.Downloading(results, DownloadProgress(0, 0))

        scope.launch {
            val downloaded = withContext(Dispatchers.IO) {
                ApexDownloader(context).download { progress ->
                    localState.value = CompatibilityState.Downloading(results, progress)
                }
            }

            localState.value = downloaded.fold(
                onSuccess = { file ->
                    CompatibilityState.Downloaded(results, file.absolutePath)
                },
                onFailure = { failure ->
                    CompatibilityState.DownloadFailed(results, failure.asDownloadFailure())
                },
            )
        }
    }

    fun installApex() {
        val downloaded = localState.value as? CompatibilityState.Downloaded ?: return
        val results = downloaded.results
        localState.value = CompatibilityState.Installing(results)

        scope.launch {
            localState.value = runCatching { client.installTetheringApex(downloaded.path) }
                .fold(
                    onSuccess = { outcome -> stagingVerdict(results, outcome) },
                    onFailure = { failure ->
                        CompatibilityState.InstallFailed(
                            results,
                            "${failure.javaClass.simpleName}: ${failure.message}",
                        )
                    },
                )
        }
    }

    fun rebootDevice() {
        val staged = localState.value as? CompatibilityState.Staged ?: return

        scope.launch {
            val problem = runCatching { client.rebootDevice() }
                .getOrElse { failure -> "${failure.javaClass.simpleName}: ${failure.message}" }

            if (problem.isNotEmpty()) {
                localState.value = CompatibilityState.InstallFailed(staged.results, problem)
            }
        }
    }

    private fun stagingVerdict(
        results: List<CapabilityResult>,
        outcome: StagingOutcome,
    ): CompatibilityState = when {
        outcome.isStaged -> CompatibilityState.Staged(results)
        else -> CompatibilityState.InstallFailed(results, outcome.rawOutput)
    }
}
