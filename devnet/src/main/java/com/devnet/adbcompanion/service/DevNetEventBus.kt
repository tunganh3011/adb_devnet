package com.devnet.adbcompanion.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object DevNetEventBus {
    private val _stateChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val stateChanged: SharedFlow<Unit> = _stateChanged.asSharedFlow()

    fun notifyStateChanged() {
        _stateChanged.tryEmit(Unit)
    }
}
