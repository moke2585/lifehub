package com.lifehub.app.market.install

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface ApkInstallEvent {
    data class Success(val packageName: String?) : ApkInstallEvent

    data class Failure(val message: String) : ApkInstallEvent
}

/** PackageInstaller 结果广播与市场 ViewModel 之间的轻量事件通道。 */
object ApkInstallStatusBus {
    private val _events = MutableStateFlow<ApkInstallEvent?>(null)
    val events: StateFlow<ApkInstallEvent?> = _events.asStateFlow()

    fun publish(event: ApkInstallEvent) {
        _events.value = event
    }

    fun consume() {
        _events.value = null
    }
}
