package com.github.shekohex.whisperpp.ui.onboarding

import com.github.shekohex.whisperpp.domain.setup.SetupStatus

internal data class OnboardingUiState(
    val status: SetupStatus = SetupStatus(),
    val hasProviders: Boolean = false,
)
