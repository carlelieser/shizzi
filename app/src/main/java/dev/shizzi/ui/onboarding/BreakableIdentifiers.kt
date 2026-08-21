package dev.shizzi.ui.onboarding

/** Renders as nothing; only offers the layout somewhere legal to wrap. */
private const val ZERO_WIDTH_SPACE = "​"

/**
 * Offers a break after each dot in a dotted name.
 *
 * These lines are exception text, whose class and method names carry no spaces
 * — so the layout has no legal break and splits mid-identifier. A zero-width
 * space leaves the text exactly as the platform reported it while letting it
 * wrap where a reader would expect.
 */
fun breakableIdentifiers(detail: String): String =
    detail.replace(".", ".$ZERO_WIDTH_SPACE")
