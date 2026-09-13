package dev.shizzi

enum class VpnMode { AUTO, ALWAYS, NEVER }

fun parseVpnMode(stored: String?): VpnMode =
    runCatching { VpnMode.valueOf(stored.orEmpty()) }.getOrDefault(VpnMode.AUTO)
