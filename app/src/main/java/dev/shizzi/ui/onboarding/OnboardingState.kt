package dev.shizzi.ui.onboarding

import dev.shizzi.CompatibilityState
import dev.shizzi.PermissionStatus
import dev.shizzi.PrivilegeBackendType
import dev.shizzi.PrivilegeState

data class OnboardingState(
    val backend: PrivilegeBackendType,
    val privilegeState: PrivilegeState,
    val compatibility: CompatibilityState,
    val permissions: List<PermissionStatus>,
)
