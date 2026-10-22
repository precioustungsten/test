package com.openear.app.model

sealed interface ModelState {
    data object NotInstalled : ModelState
    /** A partial download exists and can be resumed. */
    data class Partial(val bytes: Long, val total: Long) : ModelState
    data class Downloading(val bytes: Long, val total: Long) : ModelState
    data object Verifying : ModelState
    data object Ready : ModelState
    data class Failed(val reason: String) : ModelState
}
