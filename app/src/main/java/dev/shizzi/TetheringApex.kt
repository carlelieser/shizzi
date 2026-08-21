package dev.shizzi

import android.os.Build

/**
 * The tethering Mainline module this app can install, and how to recognise it.
 *
 * A device below Android 13 fails only [Capability.PREFER_TEST_NETWORKS], and it
 * fails it because the method lives in the tethering APEX rather than in the
 * platform — so a new enough module supplies it without a system update. Google
 * publishes no fetchable URL for a signed build (Play system updates and partner
 * OTA are the only channels), which is why the binary is vendored here and
 * served from a pinned commit.
 *
 * The pin is what makes a shipped APK safe: a moving ref would let the bytes
 * change under an install path proven against these ones.
 */
object TetheringApex {

    /** Named separately so the pin is one edit rather than a URL rewrite. */
    private const val COMMIT_SHA = "03e29355cf7277ef934d94c1ff814427a937cf2c"

    /**
     * Pinned to a commit, never to a branch — see the object's KDoc. Changing
     * the binary means changing [COMMIT_SHA], [SHA_256], and [SIZE_BYTES]
     * together.
     */
    const val URL = "https://raw.githubusercontent.com/carlelieser/shizzi/" +
        "$COMMIT_SHA/apex/tethering-311314000.apex"

    /** Verified over the downloaded bytes before anything reaches `pm install`. */
    const val SHA_256 = "9dd283aa489d2986b9f0021d07dbcfe9d73fb2850c08ac1e57a950daf86f3834"

    /** Checked alongside the digest, so a truncated body is rejected early. */
    const val SIZE_BYTES = 2_937_124L

    /**
     * The module version the APEX carries, on the android12 train.
     *
     * APEX installs cannot be downgraded, so a device already at or above this
     * is not offered the module — apexd would reject it, and offering a fix that
     * cannot apply is worse than reporting the device unsupported.
     */
    const val VERSION_CODE = 311_314_000L

    /** Where the download lands, and what the shell process is handed. */
    const val FILE_NAME = "tethering-311314000.apex"

    /**
     * Releases where the platform lacks the method but the module can supply it.
     *
     * Not below 30: the android11 train predates the feature entirely. Not 33+:
     * those already carry it, and a device that passes must never be offered an
     * install.
     */
    val INSTALLABLE_SDK_INTS = setOf(
        Build.VERSION_CODES.R,
        Build.VERSION_CODES.S,
        Build.VERSION_CODES.S_V2,
    )
}
