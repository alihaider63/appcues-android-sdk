package com.appcues.logging

import android.util.Log
import com.appcues.LoggingLevel
import com.appcues.LoggingLevel.NONE
import com.appcues.logging.LogType.DEBUG
import com.appcues.logging.LogType.ERROR
import com.appcues.logging.LogType.INFO
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

internal class LogcatDestination(
    private val logcues: Logcues,
    private val level: LoggingLevel,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : CoroutineScope {

    companion object {

        private const val TAG = "Appcues"
    }

    override val coroutineContext: CoroutineContext
        get() = dispatcher

    fun init() {
        if (level == NONE) return

        launch { logcues.messageFlow.collect { it.log() } }
    }

    private fun LogMessage.log() {
        if (type == INFO) {
            Log.i(TAG, message)
        } else if (type == ERROR) {
            Log.e(TAG, message)
        } else if (type == DEBUG && level == LoggingLevel.DEBUG) {
            Log.d(TAG, message)
        }
    }
}
