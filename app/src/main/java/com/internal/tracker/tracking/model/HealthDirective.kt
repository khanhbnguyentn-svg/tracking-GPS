package com.internal.tracker.tracking.model

sealed interface HealthDirective {
    data object None : HealthDirective
    data class OpenGap(val reRegisterNow: Boolean) : HealthDirective
    data object ReRegister : HealthDirective
    data object CloseGap : HealthDirective
}
