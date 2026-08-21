package dev.shizzi

import android.content.Context
import org.json.JSONObject

/**
 * The capabilities onboarding reports on, in the order it lists them.
 *
 * Only the two that separate a working device from a broken one. Starting the
 * hotspot has the lowest floor of the three the app needs and is reached by two
 * different calls, so asking about it answers nothing a user can act on; see
 * docs/android-compatibility.md.
 */
enum class Capability { TEST_NETWORK, PREFER_TEST_NETWORKS }

/**
 * Whether one capability is present, and the evidence either way.
 *
 * [detail] carries the verbatim reason a check failed, for the same reason
 * [ProbeResult] does: generic error text is what makes a failure unactionable.
 */
data class CapabilityResult(
    val capability: Capability,
    val isPresent: Boolean,
    val detail: String,
)

/**
 * Answers whether this device can run the app, without doing anything to it.
 *
 * Distinct from [ProbeRunner], which answers the same question by running a
 * whole session — it creates a TUN, starts the hotspot, and takes the tethering
 * stack through upstream selection. That is the right check to export when
 * something is wrong and the wrong one to make a user sit through on first
 * launch.
 */
class CompatibilityCheck(private val context: Context) {

    fun run(): List<CapabilityResult> = Capability.entries.map { capability ->
        when (capability) {
            Capability.TEST_NETWORK -> checkTestNetwork()
            Capability.PREFER_TEST_NETWORKS -> checkPreferTestNetworks()
        }
    }

    /**
     * The class alone is not enough: it survives on builds where the service
     * behind it has been trimmed, and the session needs the instance.
     */
    private fun checkTestNetwork(): CapabilityResult {
        val api = TestNetworkApi(context)

        return CapabilityResult(
            capability = Capability.TEST_NETWORK,
            isPresent = api.isAvailable,
            detail = when {
                api.isAvailable -> "getSystemService(\"test_network\") returned an instance"
                else -> "test_network service or TestNetworkManager class unavailable"
            },
        )
    }

    /**
     * Resolved but never invoked. Calling it would raise the preference on a
     * device the user has not started a session on, and the resolution is what
     * the answer turns on — the call itself is accepted wherever the method
     * exists at this UID.
     */
    private fun checkPreferTestNetworks(): CapabilityResult {
        val failure = TetheringPreferenceApi(context).resolutionFailure()

        return CapabilityResult(
            capability = Capability.PREFER_TEST_NETWORKS,
            isPresent = failure == null,
            detail = failure ?: "TetheringManager.setPreferTestNetworks resolved",
        )
    }
}

/** The JSON [CompatibilityCheck] crosses the binder as. */
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

/**
 * Reads back what [toJson] wrote.
 *
 * A capability the report does not mention is absent rather than skipped: an
 * older shell daemon answers without it, and treating silence as present would
 * clear a device the app cannot run on.
 */
fun parseCapabilities(report: String): List<CapabilityResult> {
    val parsed = runCatching { JSONObject(report) }.getOrNull()

    return Capability.entries.map { capability ->
        val entry = parsed?.optJSONObject(capability.name)

        CapabilityResult(
            capability = capability,
            isPresent = entry?.optBoolean("present") == true,
            detail = entry?.optString("detail").orEmpty()
                .ifEmpty { "not reported by the privileged process" },
        )
    }
}
