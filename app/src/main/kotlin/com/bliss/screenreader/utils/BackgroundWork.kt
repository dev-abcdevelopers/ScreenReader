@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.utils

import android.os.SystemClock
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object BackgroundWork {
    private const val REVEAL_AFTER_MILLIS = 150L
    private const val MIN_VISIBLE_MILLIS = 300L

    val StoreDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

    fun <ResultType> Run(
        OwnerRef: LifecycleOwner,
        OnSlow: () -> Unit = {},
        Work: () -> ResultType,
        OnResult: (ResultType) -> Unit
    ): Job = OwnerRef.lifecycleScope.launch {
        var ShownAt = 0L
        val RevealJob = launch {
            delay(REVEAL_AFTER_MILLIS)
            ShownAt = SystemClock.uptimeMillis()
            OnSlow()
        }
        val ResultVal = withContext(StoreDispatcher) { Work() }
        RevealJob.cancel()
        if (ShownAt > 0L) {
            val ShownFor = SystemClock.uptimeMillis() - ShownAt
            if (ShownFor < MIN_VISIBLE_MILLIS) delay(MIN_VISIBLE_MILLIS - ShownFor)
        }
        OnResult(ResultVal)
    }
}
