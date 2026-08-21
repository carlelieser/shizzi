package dev.shizzi.ui.onboarding

private const val ZERO_WIDTH_SPACE = "​"

fun breakableIdentifiers(detail: String): String =
    detail.replace(".", ".$ZERO_WIDTH_SPACE")
