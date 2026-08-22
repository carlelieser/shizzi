package dev.shizzi

import android.os.Build

object TetheringApex {

    private const val COMMIT_SHA = "03e29355cf7277ef934d94c1ff814427a937cf2c"

    const val URL = "https://raw.githubusercontent.com/carlelieser/shizzi/" +
        "$COMMIT_SHA/apex/tethering-311314000.apex"

    const val SHA_256 = "9dd283aa489d2986b9f0021d07dbcfe9d73fb2850c08ac1e57a950daf86f3834"

    const val SIZE_BYTES = 2_937_124L

    const val VERSION_CODE = 311_314_000L

    const val FILE_NAME = "tethering-311314000.apex"

    val INSTALLABLE_SDK_INTS = setOf(
        Build.VERSION_CODES.R,
        Build.VERSION_CODES.S,
        Build.VERSION_CODES.S_V2,
    )
}
