package dev.shizzi

/**
 * Where the compatibility check is.
 *
 * Separate from [DiagnosticsState] despite the shape rhyming: that one carries
 * a report meant to be exported and read by a developer, this one carries per
 * capability answers a user is shown. Folding them would make every consumer
 * ask which kind of run produced the state it holds.
 */
sealed interface CompatibilityState {

    /** Nothing has been asked yet. The step offers Check. */
    data object Idle : CompatibilityState

    data object Checking : CompatibilityState

    /**
     * [results] holds an entry per [Capability], in declaration order, whether
     * or not the check could answer it.
     */
    data class Complete(val results: List<CapabilityResult>) : CompatibilityState

    /**
     * The check could not run at all — Shizuku went away, or the daemon is
     * stale. Distinct from a run that answered "not compatible": one is a
     * verdict about the device, the other is the app failing to reach it.
     */
    data class Failed(val problem: String) : CompatibilityState
}

/**
 * True only once every capability has answered present.
 *
 * A check that never ran is not compatibility, so [Idle] and [Failed] are false
 * rather than unknown — the button past this step reads the same value.
 */
val CompatibilityState.isCompatible: Boolean
    get() = this is CompatibilityState.Complete && results.all { it.isPresent }
