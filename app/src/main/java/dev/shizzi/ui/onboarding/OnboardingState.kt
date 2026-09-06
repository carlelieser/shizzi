package dev.shizzi.ui.onboarding

import dev.shizzi.CompatibilityState
import dev.shizzi.PermissionStatus
import dev.shizzi.ShizukuState

data class OnboardingState(
    val shizuku: ShizukuState,
    val compatibility: CompatibilityState,
    val permissions: List<PermissionStatus>,
)
