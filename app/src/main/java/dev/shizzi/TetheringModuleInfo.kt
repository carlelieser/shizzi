package dev.shizzi

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

class TetheringModuleInfo(private val context: Context) {

    data class Reading(
        val packageName: String?,
        val versionCode: Long?,
        val train: Long?,
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
        val CANDIDATE_PACKAGES = listOf(
            "com.google.android.tethering",
            "com.android.tethering",
        )

        const val TRAIN_DIVISOR = 100_000_000L

        const val FIRST_FEATURE_TRAIN = 31L
    }
}
