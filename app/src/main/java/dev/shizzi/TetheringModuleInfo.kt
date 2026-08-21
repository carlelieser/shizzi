package dev.shizzi

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Reads the version of the installed Tethering Mainline module.
 *
 * The load-bearing API — TetheringManager.setPreferTestNetworks, and the
 * UpstreamNetworkMonitor code that reads the flag it sets — lives inside this
 * module, not the platform, so the API level alone does not say whether a
 * device has it. Two devices on the same release can differ by which module
 * train and build they have taken.
 *
 * What this reports is the module's identity: its package, its version code,
 * and the train the version code encodes. It does not decide whether the
 * feature is present — the direct call (probe Q4) is authoritative for that,
 * and no version number can beat an actual call. The one thing the version
 * settles on its own is the train floor: AOSP source shows the feature is
 * absent from the android11 tethering train and present from android12, so a
 * module below the android12 train cannot have it on any build. Above that, the
 * exact first-feature build is not pinned, so the version is recorded for Q4 to
 * interpret rather than judged against a hardcoded cutoff.
 */
class TetheringModuleInfo(private val context: Context) {

    data class Reading(
        val packageName: String?,
        val versionCode: Long?,
        /** The train the version code encodes (e.g. 31 = android12), or null if unread. */
        val train: Long?,
        /**
         * True only when the train is provably below the one that first carried
         * the feature (android12) — the single verdict the version alone
         * supports. Null when the module is unread, or when the train is
         * android12+ and so cannot be ruled out from the version: that case is
         * Q4's to answer, not this probe's.
         */
        val belowFeatureTrain: Boolean?,
        val description: String,
    )

    fun read(): Reading {
        val (name, code) = resolveModule()
            ?: return Reading(
                packageName = null,
                versionCode = null,
                train = null,
                belowFeatureTrain = null,
                description = "no tethering APEX resolvable via PackageManager " +
                    "(searched: ${CANDIDATE_PACKAGES.joinToString(", ")})",
            )

        val train = code / TRAIN_DIVISOR
        val belowFeatureTrain = train < FIRST_FEATURE_TRAIN

        val description = when {
            belowFeatureTrain ->
                "module $code on the android$train train (< android$FIRST_FEATURE_TRAIN); " +
                    "the feature entered the module in the android$FIRST_FEATURE_TRAIN train, so " +
                    "no build on this train carries it — the platform is out of reach without a " +
                    "train upgrade"

            else ->
                "module $code on the android$train train (>= android$FIRST_FEATURE_TRAIN); this " +
                    "train can carry setPreferTestNetworks, but whether this exact build does is " +
                    "not decidable from the version — Q4 is authoritative"
        }

        return Reading(name, code, train, belowFeatureTrain, description)
    }

    private fun resolveModule(): Pair<String, Long>? {
        for (candidate in CANDIDATE_PACKAGES) {
            val code = runCatching { versionCodeOf(candidate) }.getOrNull()
            if (code != null) return candidate to code
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun versionCodeOf(packageName: String): Long {
        val flags = PackageManager.MATCH_APEX
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(flags.toLong()),
            )
        } else {
            context.packageManager.getPackageInfo(packageName, flags)
        }
        return info.longVersionCode
    }

    private companion object {
        /**
         * OEM builds carry the Google-signed module under com.google.*; pure
         * AOSP uses com.android.*. Try both so the probe works on either.
         */
        val CANDIDATE_PACKAGES = listOf(
            "com.google.android.tethering",
            "com.android.tethering",
        )

        /**
         * Version codes are ten digits: the leading pair is the train (31 =
         * android12), the rest a monotonic build number within it. Dividing by
         * this recovers the train.
         */
        const val TRAIN_DIVISOR = 100_000_000L

        /**
         * The earliest train to carry setPreferTestNetworks. From AOSP source:
         * the android11 tethering train lacks it, the android12 train has it.
         * A module below this train cannot have the feature on any build — the
         * only conclusion the version code supports on its own.
         */
        const val FIRST_FEATURE_TRAIN = 31L // android12
    }
}
