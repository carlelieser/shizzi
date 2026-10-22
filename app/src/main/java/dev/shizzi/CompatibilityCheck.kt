package dev.shizzi

import android.content.Context
import org.json.JSONObject

enum class Capability { TEST_NETWORK, PREFER_TEST_NETWORKS }

data class CapabilityResult(
    val capability: Capability,
    val isPresent: Boolean,
    val detail: String,
)

class CompatibilityCheck(private val context: Context) {

    fun run(): List<CapabilityResult> = Capability.entries.map { capability ->
        when (capability) {
            Capability.TEST_NETWORK -> checkTestNetwork()
            Capability.PREFER_TEST_NETWORKS -> checkPreferTestNetworks()
        }
    }

    private fun checkTestNetwork(): CapabilityResult {
        val api = TestNetworkApi(context)

        return CapabilityResult(
            capability = Capability.TEST_NETWORK,
            isPresent = api.isAvailable,
            detail = when {
                api.isAvailable -> "The test_network system service is available."
                else -> "The test_network service or TestNetworkManager class is unavailable."
            },
        )
    }

    private fun checkPreferTestNetworks(): CapabilityResult {
        val failure = TetheringPreferenceApi(context).resolutionFailure()

        return CapabilityResult(
            capability = Capability.PREFER_TEST_NETWORKS,
            isPresent = failure == null,
            detail = failure ?: "TetheringManager.setPreferTestNetworks is available.",
        )
    }
}

fun List<CapabilityResult>.toJson(): String = JSONObject().apply {
    this@toJson.forEach { result ->
        put(
            result.capability.name,
            JSONObject().apply {
                put("present", result.isPresent)
                put("detail", result.detail)
            },
        )
    }
}.toString()

fun parseCapabilities(report: String): List<CapabilityResult> {
    val parsed = runCatching { JSONObject(report) }.getOrNull()

    // The privileged side reports operational failures as a verdict=ERROR object
    // instead of per-capability entries; carry the real error into the details
    // so the UI can show why the check failed instead of a generic placeholder.
    val privilegedError = parsed
        ?.takeIf { it.optString("verdict") == "ERROR" }
        ?.optString("error")
        ?.takeIf { it.isNotEmpty() }

    return Capability.entries.map { capability ->
        val entry = parsed?.optJSONObject(capability.name)
        val detail = entry?.optString("detail").orEmpty()

        CapabilityResult(
            capability = capability,
            isPresent = entry?.optBoolean("present") == true,
            detail = when {
                detail.isNotEmpty() -> detail
                privilegedError != null -> "the privileged process failed: $privilegedError"
                else -> "not reported by the privileged process"
            },
        )
    }
}
